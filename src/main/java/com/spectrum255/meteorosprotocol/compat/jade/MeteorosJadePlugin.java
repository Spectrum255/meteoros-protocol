package com.spectrum255.meteorosprotocol.compat.jade;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.machine.MachineBlock;
import com.spectrum255.meteorosprotocol.machine.MachineBlockEntity;
import com.spectrum255.meteorosprotocol.particle.Phase;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Optional Jade integration for the particle machines.
 *
 * <p>Jade only scans this class when Jade itself is installed, so the class
 * (and its Jade imports) never load on a Jade-free client/server. The
 * server-side provider sends the machine's FE, mixed buffer and the non-zero
 * phase tanks; the client-side provider renders compact progress bars (energy,
 * mixed buffer, up to three phase tanks) instead of plain text lines. The
 * renderer still supplies the animated bars, while Jade supplies the exact
 * numbers.</p>
 */
@WailaPlugin("meteorosprotocol")
public final class MeteorosJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(MachineProvider.INSTANCE, MachineBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(MachineProvider.INSTANCE, MachineBlock.class);
    }

    private enum MachineProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID =
                ResourceLocation.fromNamespaceAndPath(MeteorosProtocol.MODID, "machine");
        private static final String ROOT = "MeteorosProtocol";
        private static final String PHASE_PREFIX = "Phase.";
        private static final String MIXED_PREFIX = "Mixed.";

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MachineBlockEntity machine)) {
                return;
            }
            CompoundTag root = new CompoundTag();
            root.putLong("Energy", machine.energy());
            root.putLong("EnergyCapacity", machine.energyCapacity());
            root.putLong("Mixed", machine.getTotal());
            root.putLong("MixedCapacity", MachineBlockEntity.MIXED_CAPACITY);
            root.putLong("Loss", machine.lastSeparatorLoss());
            int mixedNonZero = 0;
            int tankNonZero = 0;
            for (Phase phase : Phase.all()) {
                long mixedAmount = machine.getAmount(phase);
                if (mixedAmount > 0L) {
                    root.putLong(MIXED_PREFIX + phase.id(), mixedAmount);
                    mixedNonZero++;
                }
                long tankAmount = machine.phaseStorage().getAmount(phase);
                if (tankAmount > 0L) {
                    root.putLong(PHASE_PREFIX + phase.id(), tankAmount);
                    tankNonZero++;
                }
            }
            root.putInt("MixedCount", mixedNonZero);
            root.putInt("PhaseCount", tankNonZero);
            data.put(ROOT, root);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            MachineBlockEntity machine = accessor.getBlockEntity() instanceof MachineBlockEntity found
                    ? found : null;
            CompoundTag root = accessor.getServerData().getCompound(ROOT);

            long energy;
            long energyCapacity;
            long loss;
            if (root.contains("Energy")) {
                energy = root.getLong("Energy");
                energyCapacity = root.getLong("EnergyCapacity");
                loss = root.getLong("Loss");
            } else if (machine != null) {
                // Fallback for the first client frame before the server payload
                // arrives; the client block entity is synced via getUpdateTag().
                energy = machine.energy();
                energyCapacity = machine.energyCapacity();
                loss = machine.lastSeparatorLoss();
            } else {
                return;
            }

            tooltip.add(Component.translatable("jade.meteorosprotocol.header"));

            // FE is intentionally not shown here: our own machine screens and the
            // block entity renderer already display energy, and the user asked for
            // a compact tooltip (no energy row).

            // All phases share one strip: segment widths are proportional to each
            // phase's share of the stored total, and the fill ceiling is the sum of
            // the phase tank capacities. When the tanks are empty we fall back to the
            // mixed buffer, whose ceiling is the shared mixed capacity.
            List<Phase> phases = Phase.all();
            int[] colors = new int[phases.size()];
            long[] tankAmounts = new long[phases.size()];
            long tankTotal = 0L;
            long tankCapacity = 0L;
            for (Phase phase : phases) {
                int index = phase.index();
                colors[index] = phase.argb();
                long amount = tankAmount(root, machine, phase);
                tankAmounts[index] = amount;
                tankTotal += amount;
                tankCapacity += phase.tankCapacity();
            }

            long[] stripAmounts = tankAmounts;
            long stripTotal = tankTotal;
            long stripCapacity = tankCapacity;
            if (stripTotal <= 0L) {
                stripAmounts = new long[phases.size()];
                long mixedStored = 0L;
                for (Phase phase : phases) {
                    long amount = mixedAmount(root, machine, phase);
                    stripAmounts[phase.index()] = amount;
                    mixedStored += amount;
                }
                stripTotal = mixedStored;
                stripCapacity = MachineBlockEntity.MIXED_CAPACITY;
            }

            tooltip.add(new MixedStripElement(colors, stripAmounts, stripTotal, stripCapacity));
            tooltip.add(Component.literal(num(stripTotal) + " / " + num(stripCapacity) + " mPU")
                    .withStyle(ChatFormatting.GRAY));

            if (accessor.getBlock() == MeteorosProtocol.PHASE_SEPARATOR.get()) {
                tooltip.add(Component.translatable("jade.meteorosprotocol.loss", num(loss)));
            }
        }

        private static long mixedAmount(CompoundTag root, MachineBlockEntity machine, Phase phase) {
            String key = MIXED_PREFIX + phase.id();
            if (root.contains(key)) {
                return root.getLong(key);
            }
            return machine != null ? machine.getAmount(phase) : 0L;
        }

        private static long tankAmount(CompoundTag root, MachineBlockEntity machine, Phase phase) {
            String key = PHASE_PREFIX + phase.id();
            if (root.contains(key)) {
                return root.getLong(key);
            }
            return machine != null ? machine.phaseStorage().getAmount(phase) : 0L;
        }

        private static String num(long value) {
            return String.format(Locale.ROOT, "%,d", value);
        }
    }
}
