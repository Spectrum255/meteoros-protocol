package com.spectrum255.meteorosprotocol.command;

import com.mojang.brigadier.CommandDispatcher;
import com.spectrum255.meteorosprotocol.compat.WeatherAdjustedField;
import com.spectrum255.meteorosprotocol.machine.MachineBlockEntity;
import com.spectrum255.meteorosprotocol.particle.*;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
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

        String message = String.format(Locale.ROOT,
                "Biome %s @Y=%d | Dth=%.3f (T=%.3f/C=%.3f) | Dch=%.3f (V+=%.3f/V-=%.3f q=%.3f)"
                        + " | Aq=%.3f | Ae=%.3f | Dtotal=%.3f | %s",
                biome, pos.getY(),
                sample.thermalDensity(), sample.thermion(), sample.cryon(),
                sample.chargeDensity(), sample.voltixPos(), sample.voltixNeg(), sample.chargeImbalance(),
                sample.aqueousDensity(), sample.primordialDensity(), sample.totalDensity(),
                saturation.oversaturated() ? "oversaturated"
                        : saturation.lowDensity() ? "sparse"
                        : saturation.highDensity() ? "dense"
                        : "normal");
        source.sendSuccess(() -> Component.literal(message), false);
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
