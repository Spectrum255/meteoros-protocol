package com.spectrum255.meteorosprotocol.compat.jade;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.machine.MachineBlock;
import com.spectrum255.meteorosprotocol.machine.MachineBlockEntity;
import com.spectrum255.meteorosprotocol.particle.Phase;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * phase tanks; the client-side provider renders a compact tooltip: header,
 * energy, mixed buffer, separator loss and a single phase summary line. The
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
        private static final int MAX_PHASE_ENTRIES = 6;

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
            int nonZero = 0;
            for (Phase phase : Phase.all()) {
                long amount = machine.phaseStorage().getAmount(phase);
                if (amount > 0L) {
                    root.putLong(PHASE_PREFIX + phase.id(), amount);
                    nonZero++;
                }
            }
            root.putInt("PhaseCount", nonZero);
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

            tooltip.add(Component.translatable("jade.meteorosprotocol.header"));
            tooltip.add(Component.translatable(
                    "jade.meteorosprotocol.energy", num(energy), num(energyCapacity)));
            tooltip.add(Component.translatable(
                    "jade.meteorosprotocol.mixed", num(mixed), num(MachineBlockEntity.MIXED_CAPACITY)));

            if (accessor.getBlock() == MeteorosProtocol.PHASE_SEPARATOR.get()) {
                tooltip.add(Component.translatable("jade.meteorosprotocol.loss", num(loss)));
            }

            tooltip.add(phasesLine(root, machine));
        }

        /**
         * Builds the single "Phases: Ae 1,234 · T 890 · +2" line from the
         * non-zero phase amounts, capped at {@value #MAX_PHASE_ENTRIES}
         * entries. Returns the translated empty variant when every tank is 0.
         */
        private static Component phasesLine(CompoundTag root, MachineBlockEntity machine) {
            List<String> entries = new ArrayList<>();
            int nonZero = 0;
            for (Phase phase : Phase.all()) {
                long amount = root.contains(PHASE_PREFIX + phase.id())
                        ? root.getLong(PHASE_PREFIX + phase.id())
                        : machine != null ? machine.phaseStorage().getAmount(phase) : 0L;
                if (amount <= 0L) {
                    continue;
                }
                nonZero++;
                if (entries.size() < MAX_PHASE_ENTRIES) {
                    entries.add(phase.shortName().getString() + " " + num(amount));
                }
            }
            if (entries.isEmpty()) {
                return Component.translatable("jade.meteorosprotocol.phases.empty");
            }
            StringBuilder summary = new StringBuilder(String.join(" \u00b7 ", entries));
            int remaining = nonZero - entries.size();
            if (remaining > 0) {
                summary.append(" \u00b7 ")
                        .append(Component.translatable("jade.meteorosprotocol.phases.more", remaining)
                                .getString());
            }
            return Component.translatable("jade.meteorosprotocol.phases", summary.toString());
        }

        private static String num(long value) {
            return String.format(Locale.ROOT, "%,d", value);
        }
    }
}
