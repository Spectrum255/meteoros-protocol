package com.spectrum255.meteorosprotocol.compat.jade;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.machine.MachineBlock;
import com.spectrum255.meteorosprotocol.machine.MachineBlockEntity;
import com.spectrum255.meteorosprotocol.particle.Phase;
import java.util.ArrayList;
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
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElementHelper;

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
        private static final int MAX_PHASE_BARS = 3;
        private static final int ENERGY_COLOR = 0xFFD24B3F;
        private static final int MIXED_COLOR = 0xFF8B8B8B;

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
            long mixed;
            long loss;
            if (root.contains("Energy")) {
                energy = root.getLong("Energy");
                energyCapacity = root.getLong("EnergyCapacity");
                mixed = root.getLong("Mixed");
                loss = root.getLong("Loss");
            } else if (machine != null) {
                // Fallback for the first client frame before the server payload
                // arrives; the client block entity is synced via getUpdateTag().
                energy = machine.energy();
                energyCapacity = machine.energyCapacity();
                mixed = machine.getTotal();
                loss = machine.lastSeparatorLoss();
            } else {
                return;
            }

            IElementHelper helper = IElementHelper.get();
            BoxStyle box = BoxStyle.getTransparent();

            tooltip.add(Component.translatable("jade.meteorosprotocol.header"));

            tooltip.add(helper.progress(
                    fraction(energy, energyCapacity),
                    Component.literal(num(energy) + " / " + num(energyCapacity) + " FE"),
                    helper.progressStyle().color(ENERGY_COLOR),
                    box,
                    true));

            tooltip.add(helper.progress(
                    fraction(mixed, MachineBlockEntity.MIXED_CAPACITY),
                    Component.literal(num(mixed) + " / " + num(MachineBlockEntity.MIXED_CAPACITY) + " mPU"),
                    helper.progressStyle().color(MIXED_COLOR),
                    box,
                    true));

            if (accessor.getBlock() == MeteorosProtocol.PHASE_SEPARATOR.get()) {
                tooltip.add(Component.translatable("jade.meteorosprotocol.loss", num(loss)));
            }

            List<Phase> stored = nonZeroTanks(root, machine);
            int shown = Math.min(MAX_PHASE_BARS, stored.size());
            for (int i = 0; i < shown; i++) {
                Phase phase = stored.get(i);
                long amount = tankAmount(root, machine, phase);
                tooltip.add(helper.progress(
                        fraction(amount, phase.tankCapacity()),
                        Component.literal(phase.shortName().getString()
                                + " " + num(amount) + " / " + num(phase.tankCapacity())),
                        helper.progressStyle().color(phase.argb()),
                        box,
                        true));
            }
            if (stored.size() > MAX_PHASE_BARS) {
                tooltip.add(Component.translatable(
                                "jade.meteorosprotocol.phases.more", stored.size() - MAX_PHASE_BARS)
                        .withStyle(ChatFormatting.GRAY));
            } else if (stored.isEmpty()) {
                tooltip.add(Component.translatable("jade.meteorosprotocol.phases.empty")
                        .withStyle(ChatFormatting.GRAY));
            }
        }

        /** Non-zero phase tanks, in registry order (core phases first). */
        private static List<Phase> nonZeroTanks(CompoundTag root, MachineBlockEntity machine) {
            List<Phase> stored = new ArrayList<>();
            for (Phase phase : Phase.all()) {
                if (tankAmount(root, machine, phase) > 0L) {
                    stored.add(phase);
                }
            }
            return stored;
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

        private static float fraction(long value, long capacity) {
            if (capacity <= 0L) {
                return 0.0F;
            }
            float fill = (float) ((double) value / (double) capacity);
            return Math.max(0.0F, Math.min(1.0F, fill));
        }

        private static String num(long value) {
            return String.format(Locale.ROOT, "%,d", value);
        }
    }
}
