package com.spectrum255.meteorosprotocol.command;

import com.mojang.brigadier.CommandDispatcher;
import com.spectrum255.meteorosprotocol.compat.LightHeatParticleModifier;
import com.spectrum255.meteorosprotocol.compat.LocalizedRainCompat;
import com.spectrum255.meteorosprotocol.compat.TemperatureProbe;
import com.spectrum255.meteorosprotocol.compat.WeatherAdjustedField;
import com.spectrum255.meteorosprotocol.machine.MachineBlockEntity;
import com.spectrum255.meteorosprotocol.particle.*;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "meteorosprotocol")
public final class MeteorosCommands {
    private MeteorosCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("meteoros")
                .then(Commands.literal("field").executes(ctx -> field(ctx.getSource())))
                .then(Commands.literal("temp").executes(ctx -> temp(ctx.getSource())))
                .then(Commands.literal("side").executes(ctx -> side(ctx.getSource())))
                .then(Commands.literal("phases").executes(ctx -> phases(ctx.getSource())))
                .then(Commands.literal("selftest").executes(ctx -> selftest(ctx.getSource())))
                .then(Commands.literal("tank").executes(ctx -> tank(ctx.getSource()))));
    }

    private static int field(CommandSourceStack source) {
        if (!(source.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        BlockPos pos = BlockPos.containing(source.getPosition());
        FieldSample sample = WeatherAdjustedField.sampleAt(level, pos);
        Saturation saturation = Saturation.of(sample);
        String biome = level.getBiome(pos).unwrapKey()
                .map(key -> key.location().toString())
                .orElse("unknown");

        double ambient = TemperatureProbe.ambient(level, pos);
        double local = TemperatureProbe.local(level, pos);
        double deltaK = local - ambient;
        float temperatureHeat = TemperatureProbe.heatOf(deltaK);

        String message = String.format(Locale.ROOT,
                "Biome %s @Y=%d | Dth=%.3f (T=%.3f/C=%.3f) | Dch=%.3f (V+=%.3f/V-=%.3f q=%.3f)"
                        + " | Aq=%.3f | Ae=%.3f | Dtotal=%.3f | %s"
                        + " | temp %dK (%+dK -> %+.1fT)",
                biome, pos.getY(),
                sample.thermalDensity(), sample.thermion(), sample.cryon(),
                sample.chargeDensity(), sample.voltixPos(), sample.voltixNeg(), sample.chargeImbalance(),
                sample.aqueousDensity(), sample.primordialDensity(), sample.totalDensity(),
                saturation.oversaturated() ? "oversaturated"
                        : saturation.lowDensity() ? "sparse"
                        : saturation.highDensity() ? "dense"
                        : "normal",
                Math.round(local), Math.round(deltaK), temperatureHeat);
        source.sendSuccess(() -> Component.literal(message), false);
        source.sendSuccess(() -> Component.literal("  " + phaseSummary(sample)), false);
        int blockLight = LightHeatParticleModifier.blockLight(level, pos);
        int skyLight = LightHeatParticleModifier.skyLight(level, pos);
        int skyDarken = level.getSkyDarken();
        boolean precipitating = LocalizedRainCompat.isPrecipitatingAt(level, pos);
        int skyEffective = LightHeatParticleModifier.skyEffective(skyLight, skyDarken, precipitating);
        float heat = LightHeatParticleModifier.heatFor(blockLight, skyEffective);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "  light: block %d + sky %d (darken %d) -> +%.1f T",
                blockLight, skyLight, skyDarken, heat)), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "  intake capture loss: %d%% (Thermion/Cryon/Aetheron lossy, Voltix/Aquantum lossless)",
                Math.round(MachineBlockEntity.intakeCaptureLossRatio() * 100.0D))), false);
        return 1;
    }

    /** Prints the six-face mixed/phase side configuration of the targeted machine. */
    private static int side(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos pos;
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            HitResult hit = player.pick(8.0D, 0.0F, false);
            if (!(hit instanceof BlockHitResult blockHit)) {
                source.sendFailure(Component.literal("No block in sight within 8 blocks."));
                return 0;
            }
            pos = blockHit.getBlockPos();
        } else {
            pos = BlockPos.containing(source.getPosition());
        }
        if (!(level.getBlockEntity(pos) instanceof MachineBlockEntity machine)) {
            source.sendFailure(Component.literal("That block is not a Meteoros machine."));
            return 0;
        }
        String facing = machine.getBlockState().hasProperty(
                com.spectrum255.meteorosprotocol.machine.MachineBlock.FACING)
                ? machine.getBlockState().getValue(
                        com.spectrum255.meteorosprotocol.machine.MachineBlock.FACING).getName()
                : "n/a";
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Side config @%d,%d,%d (facing=%s)", pos.getX(), pos.getY(), pos.getZ(), facing)), false);
        for (com.spectrum255.meteorosprotocol.machine.SideChannel channel
                : com.spectrum255.meteorosprotocol.machine.SideChannel.values()) {
            StringBuilder line = new StringBuilder("  " + channel.name().toLowerCase(Locale.ROOT) + ":");
            for (Direction direction : Direction.values()) {
                line.append(' ').append(direction.getName()).append('=')
                        .append(machine.sideMode(direction, channel).name());
            }
            String text = line.toString();
            source.sendSuccess(() -> Component.literal(text), false);
        }
        String autoIoLine = "  auto-io: " + (machine.autoIo() ? "on" : "off");
        source.sendSuccess(() -> Component.literal(autoIoLine), false);
        StringBuilder input = new StringBuilder("  input: ");
        if (machine.inputPhases().isEmpty()) {
            input.append("none");
        } else {
            for (int index = 0; index < machine.inputPhases().size(); index++) {
                if (index > 0) {
                    input.append(", ");
                }
                input.append(machine.inputPhases().get(index).shortName().getString());
            }
        }
        String inputLine = input.toString();
        source.sendSuccess(() -> Component.literal(inputLine), false);
        return 1;
    }

    private static int temp(CommandSourceStack source) {
        if (!(source.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        BlockPos pos = BlockPos.containing(source.getPosition());
        double ambient = TemperatureProbe.ambient(level, pos);
        double local = TemperatureProbe.local(level, pos);
        double deltaK = local - ambient;
        float heat = TemperatureProbe.heatOf(deltaK);
        float chill = TemperatureProbe.chillOf(deltaK);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Temperature @%d,%d,%d: ambient=%.1fK local=%.1fK dK=%+.1f heat=%+.1fT chill=%.1fC",
                pos.getX(), pos.getY(), pos.getZ(), ambient, local, deltaK, heat, chill)), false);
        return 1;
    }

    /** One "id=value" entry per registered phase, including addon phases. */
    private static String phaseSummary(FieldSample sample) {
        float[] concentrations = sample.concentrations();
        StringBuilder text = new StringBuilder("phases:");
        for (Phase phase : Phase.all()) {
            text.append(String.format(Locale.ROOT, " %s=%.3f",
                    phase.id(), concentrations[phase.index()]));
        }
        return text.toString();
    }

    /** Lists every registered phase and its index, capacity and sampler state. */
    private static int phases(CommandSourceStack source) {
        int core = Phase.corePhases().size();
        int total = Phase.all().size();
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Particle phases: %d total (%d core / %d addon), bootstrapped=%s",
                total, core, total - core, PhaseRegistry.isBootstrapped())), false);
        for (Phase phase : Phase.all()) {
            String kind = phase.core() ? "core" : "addon";
            boolean sampler = PhaseSamplers.hasSampler(phase);
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "  [%d] %s | %s | tank=%d mPU | sampler=%s",
                    phase.index(), phase.id(), kind, phase.tankCapacity(), sampler ? "yes" : "no")), false);
        }
        return 1;
    }

    private static int selftest(CommandSourceStack source) {
        var failures = ParticleSelfTest.run();
        if (failures.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Meteoros selftest: PASS (all cases)"), false);
            return 1;
        }
        source.sendFailure(Component.literal("Meteoros selftest: FAIL: " + String.join(", ", failures)));
        return 0;
    }

    private static int tank(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        HitResult hit = player.pick(8.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit)) {
            source.sendFailure(Component.literal("No block in sight within 8 blocks."));
            return 0;
        }

        BlockPos pos = blockHit.getBlockPos();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            source.sendFailure(Component.literal("That block has no block entity."));
            return 0;
        }

        boolean phaseStorage = blockEntity instanceof PhaseStorage;
        boolean mixedStorage = blockEntity instanceof MixedStorage;
        if (!phaseStorage && !mixedStorage) {
            source.sendFailure(Component.literal("That block is not a particle container."));
            return 0;
        }

        if (blockEntity instanceof MachineBlockEntity machine) {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "Machine (%d, %d, %d) | FE %d/%d",
                    pos.getX(), pos.getY(), pos.getZ(), machine.energy(), machine.energyCapacity())), false);
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "  mixed total: %d / %d mPU",
                    machine.getTotal(), MachineBlockEntity.MIXED_CAPACITY)), false);
            for (Phase phase : Phase.all()) {
                long amount = machine.getAmount(phase);
                source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                        "  mixed %s: %d mPU", phase.id(), amount)), false);
            }
            PhaseStorage phaseView = machine.phaseStorage();
            for (Phase phase : Phase.all()) {
                long amount = phaseView.getAmount(phase);
                long capacity = phaseView.getCapacity(phase);
                source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                        "  phase %s: %d / %d mPU", phase.id(), amount, capacity)), false);
            }
            return 1;
        }

        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Container (%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ())), false);

        if (phaseStorage) {
            PhaseStorage storage = (PhaseStorage) blockEntity;
            for (Phase phase : Phase.all()) {
                long amount = storage.getAmount(phase);
                long capacity = storage.getCapacity(phase);
                source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                        "  %s: %d / %d mPU", phase.id(), amount, capacity)), false);
            }
        }
        if (mixedStorage) {
            MixedStorage storage = (MixedStorage) blockEntity;
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "  total: %d mPU (%.3f PU)",
                    storage.getTotal(), storage.getTotal() / 1000.0)), false);
        }
        return 1;
    }
}
