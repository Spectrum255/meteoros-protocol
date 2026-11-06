package com.spectrum255.meteorosprotocol.particle;

import com.spectrum255.meteorosprotocol.Config;
import com.spectrum255.meteorosprotocol.compat.LightHeatParticleModifier;
import com.spectrum255.meteorosprotocol.compat.TemperatureProbe;
import com.spectrum255.meteorosprotocol.compat.TemperatureSources;
import com.spectrum255.meteorosprotocol.machine.MachineBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * S1 structural self-test for the v2 data model. Runs from
 * {@code /meteoros selftest} and never needs a world: all formula checks go
 * through the pure {@link SimpleBiomeField#compute} entry point.
 *
 * <p>The checks are phase-count agnostic: they assert that the six core phases
 * come first, in order, and derive every array size from
 * {@link Phase#all()} so addon phases do not break them.</p>
 */
public final class ParticleSelfTest {
    private ParticleSelfTest() {
    }

    public static List<String> run() {
        List<String> failures = new ArrayList<>();
        check(failures, "phase registry", ParticleSelfTest::phaseRegistry);
        check(failures, "normalize", ParticleSelfTest::normalize);
        check(failures, "amount conversion", ParticleSelfTest::amountConversion);
        check(failures, "share conversion", ParticleSelfTest::shareConversion);
        check(failures, "nbt round trip", ParticleSelfTest::nbtRoundTrip);
        check(failures, "mass balance", ParticleSelfTest::massBalance);
        check(failures, "conversion", ParticleSelfTest::conversion);
        check(failures, "field sample", ParticleSelfTest::fieldSample);
        check(failures, "saturation", ParticleSelfTest::saturation);
        check(failures, "biome magnitudes", ParticleSelfTest::biomeMagnitudes);
        check(failures, "height gradient", ParticleSelfTest::heightGradient);
        check(failures, "machine rates", ParticleSelfTest::machineRates);
        check(failures, "light heat", ParticleSelfTest::lightHeat);
        check(failures, "temperature", ParticleSelfTest::temperature);
        return failures;
    }

    private static void check(List<String> failures, String name, Runnable test) {
        try {
            test.run();
        } catch (RuntimeException exception) {
            failures.add(name + (exception.getMessage() == null ? "" : ": " + exception.getMessage()));
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static int phaseCount() {
        return Phase.all().size();
    }

    private static void phaseRegistry() {
        List<Phase> all = Phase.all();
        require(all.size() >= Phase.corePhases().size(), "registry must contain the six core phases");
        for (int index = 0; index < Phase.corePhases().size(); index++) {
            require(all.get(index) == Phase.corePhases().get(index),
                    "core phase order changed at index " + index);
            require(all.get(index).core(), "core phase flagged as extra: " + all.get(index).id());
            require(all.get(index).index() == index, "index mismatch for " + all.get(index).id());
        }
        require(all.subList(0, Phase.corePhases().size()).equals(Phase.corePhases()),
                "Phase.all() must start with the six core phases");
        for (int index = 0; index < all.size(); index++) {
            require(all.get(index).index() == index,
                    "index must equal list position for " + all.get(index).id());
        }
        for (Phase phase : all) {
            require(Phase.byId(phase.id()) == phase, "byId failed for full id " + phase.id());
            require(Phase.byId(phase.key().getPath()) == phase,
                    "byId failed for bare id " + phase.key().getPath());
            require(!phase.core() || Phase.corePhases().contains(phase),
                    "Phase.core() disagrees with corePhases(): " + phase.id());
            require(phase.tankCapacity() > 0L, "tank capacity must be positive: " + phase.id());
        }
        require(Phase.byId("VOLTIX_POS") == Phase.VOLTIX_POS, "byId must be case-insensitive");
        require(Phase.byId("meteorosprotocol:voltix_pos") == Phase.VOLTIX_POS,
                "byId must accept a namespaced id");
        require(Phase.byId("voltix") == null, "legacy voltix must not map to a phase");
        require(Phase.byId("not_a_phase") == null, "unknown id must return null");
    }

    private static void normalize() {
        int count = phaseCount();
        float[] input = new float[count];
        for (int index = 0; index < count; index++) {
            input[index] = 1.0F;
        }
        float[] normalized = ParticleMath.normalize(input);
        for (float value : normalized) {
            require(Math.abs(value - 1.0F / count) < 1.0E-6F, "normalize should split evenly");
        }
        float[] zeros = ParticleMath.normalize(new float[count]);
        for (float value : zeros) {
            require(value == 0.0F, "zero input must stay zero");
        }
    }

    private static void amountConversion() {
        int count = phaseCount();
        float[] shares = new float[count];
        for (int index = 0; index < count; index++) {
            shares[index] = 1.0F / count;
        }
        long[] amounts = ParticleMath.amountsFromShares(1_000_000L, shares);
        require(amounts.length == count, "amounts length");
        require(sum(amounts) == 1_000_000L, "amounts must sum to total");
        long[] empty = ParticleMath.amountsFromShares(1_000_000L, new float[count]);
        require(sum(empty) == 0L, "zero composition must move nothing");
    }

    private static void shareConversion() {
        int count = phaseCount();
        long[] amounts = new long[count];
        for (int index = 0; index < count; index++) {
            amounts[index] = 200_000L;
        }
        float[] shares = ParticleMath.sharesFromAmounts(amounts);
        require(Math.abs(sum(shares) - 100.0F) < 1.0E-3F, "composition must sum to 100");
        float[] zero = ParticleMath.sharesFromAmounts(new long[count]);
        require(sum(zero) == 0.0F, "zero amounts must give zero composition");
    }

    private static void nbtRoundTrip() {
        TestStorage storage = new TestStorage(8_000_000L);
        for (Phase phase : Phase.all()) {
            storage.insert(phase, 137_000L, false);
        }
        CompoundTag saved = ParticleNbt.save(storage);
        TestStorage copy = new TestStorage(8_000_000L);
        ParticleNbt.load(copy, saved);
        for (Phase phase : Phase.all()) {
            require(copy.getAmount(phase) == 137_000L, "nbt round trip " + phase.id());
        }

        // Legacy v1 data: bare-name long keys in whole PU, no unit marker, plus
        // the retired "voltix" key. V+/V- must stay zero, the other names scale.
        CompoundTag legacyParticles = new CompoundTag();
        for (Phase phase : Phase.corePhases()) {
            if (phase != Phase.VOLTIX_POS && phase != Phase.VOLTIX_NEG) {
                legacyParticles.putLong(phase.key().getPath(), 137L);
            }
        }
        legacyParticles.putLong("voltix", 137L);
        CompoundTag legacyTag = new CompoundTag();
        legacyTag.put("Particles", legacyParticles);
        TestStorage legacyCopy = new TestStorage(8_000_000L);
        ParticleNbt.load(legacyCopy, legacyTag);
        for (Phase phase : Phase.corePhases()) {
            long expected = (phase == Phase.VOLTIX_POS || phase == Phase.VOLTIX_NEG) ? 0L : 137_000L;
            require(legacyCopy.getAmount(phase) == expected, "legacy nbt " + phase.id());
        }

        List<Phase> all = Phase.all();
        for (Phase phase : new Phase[]{Phase.VOLTIX_POS, Phase.VOLTIX_NEG, Phase.AETHERON, all.get(all.size() - 1)}) {
            ParticleStack roundTrip = ParticleNbt.loadStack(
                    ParticleNbt.saveStack(new ParticleStack(phase, 123_000L))
            );
            require(roundTrip.phase() == phase && roundTrip.amount() == 123_000L,
                    "stack round trip " + phase.id());
        }

        CompoundTag legacyStackTag = new CompoundTag();
        legacyStackTag.putString("Phase", "voltix");
        legacyStackTag.putLong("Amount", 10L);
        ParticleStack legacyStack = ParticleNbt.loadStack(legacyStackTag);
        require(legacyStack.phase() == Phase.AETHERON && legacyStack.amount() == 0L,
                "legacy voltix stack must be empty");
    }

    private static void massBalance() {
        MassBalance.require(20_000L, 19_000L, 1_000L);
        try {
            MassBalance.require(20_000L, 18_000L, 1_000L);
            throw new IllegalStateException("invalid mass balance was accepted");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void conversion() {
        Conversion generator = Conversion.GENERATOR_THERMION;
        generator.validate();
        require(generator.inputs().getOrDefault(Phase.THERMION, 0L) == 10_000L,
                "generator T input");
        require(generator.outputs().getOrDefault(Phase.CRYON, 0L) == 10_000L,
                "generator C output");
        require(!generator.outputs().containsKey(Phase.AETHERON),
                "generator must not output Ae");
    }

    private static void fieldSample() {
        int count = phaseCount();
        FieldSample sample = new FieldSample(30.0F, 25.0F, 0.5F, 0.5F, 40.0F, 1.0F);
        require(near(sample.thermalDensity(), 55.0F), "D_th");
        require(near(sample.chargeDensity(), 1.0F), "D_ch");
        require(near(sample.aqueousDensity(), 40.0F), "Aq");
        require(near(sample.primordialDensity(), 1.0F), "Ae");
        require(near(sample.totalDensity(), 97.0F), "D_total");
        require(near(sample.chargeImbalance(), 0.0F), "q");
        require(near(sample.concentration(Phase.AQUANTUM), 40.0F), "concentration(Aq)");

        float[] concentrations = sample.concentrations();
        require(concentrations.length == count, "concentrations length");
        require(near(concentrations[Phase.AETHERON.index()], 1.0F), "Ae order");
        require(near(concentrations[Phase.THERMION.index()], 30.0F), "T order");
        require(near(concentrations[Phase.CRYON.index()], 25.0F), "C order");
        require(near(concentrations[Phase.VOLTIX_POS.index()], 0.5F), "V+ order");
        require(near(concentrations[Phase.VOLTIX_NEG.index()], 0.5F), "V- order");
        require(near(concentrations[Phase.AQUANTUM.index()], 40.0F), "Aq order");
        require(near(sum(concentrations), 97.0F), "concentrations sum");
        require(near(sum(sample.composition()), 100.0F) || sample.totalDensity() == 0.0F,
                "composition sum");
        require(sample.dominant() == Phase.AQUANTUM, "dominant phase");

        FieldSample dense = new FieldSample(30.0F, 25.0F, 1.0F, 1.0F, 60.0F, 10.0F);
        require(near(dense.totalDensity(), 127.0F), "D_total may exceed 100");
        require(Saturation.of(dense).highDensity(), "dense saturation");

        FieldSample empty = FieldSample.EMPTY;
        require(empty.totalDensity() == 0.0F, "empty total");
        require(sum(empty.composition()) == 0.0F, "empty composition");
        require(empty.dominant() == Phase.AETHERON, "empty dominant");
    }

    private static void saturation() {
        require(Saturation.of(new FieldSample(50.0F, 40.0F, 0.0F, 0.0F, 0.0F, 0.0F))
                .thermalSaturated(), "thermal saturation");

        Saturation charge = Saturation.of(new FieldSample(0.0F, 0.0F, 27.0F, 3.0F, 0.0F, 0.0F));
        require(charge.chargeSaturated(), "charge saturation");
        require(charge.chargeImbalanced(), "charge imbalance");

        require(Saturation.of(new FieldSample(5.0F, 5.0F, 0.0F, 0.0F, 0.0F, 0.0F))
                .lowDensity(), "low density");
        require(Saturation.of(new FieldSample(30.0F, 30.0F, 0.0F, 0.0F, 60.0F, 10.0F))
                .highDensity(), "high density");
        require(Saturation.of(new FieldSample(0.0F, 0.0F, 0.0F, 0.0F, 81.0F, 0.0F))
                .aqueousSaturated(), "aqueous saturation");
        require(Saturation.of(new FieldSample(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 81.0F))
                .primordialSaturated(), "primordial saturation");
        require(Saturation.of(new FieldSample(90.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F))
                .oversaturated(), "single-phase oversaturation");
    }

    private static void biomeMagnitudes() {
        FieldSample desert = SimpleBiomeField.compute(new SimpleBiomeField.ClimateInputs(
                2.0F, 0.0F, false, false, SimpleBiomeField.DimensionKind.OVERWORLD), 64);
        require(desert.thermalDensity() > 0.0F
                && desert.thermion() / desert.thermalDensity() > 0.85F, "desert T ratio");
        require(desert.aqueousDensity() < 10.0F, "desert Aq");

        FieldSample snow = SimpleBiomeField.compute(new SimpleBiomeField.ClimateInputs(
                0.0F, 0.5F, false, false, SimpleBiomeField.DimensionKind.OVERWORLD), 64);
        require(snow.thermalDensity() > 0.0F
                && snow.cryon() / snow.thermalDensity() > 0.75F, "snow C ratio");

        FieldSample ocean = SimpleBiomeField.compute(new SimpleBiomeField.ClimateInputs(
                0.5F, 0.5F, true, false, SimpleBiomeField.DimensionKind.OVERWORLD), 64);
        require(ocean.aqueousDensity() > 45.0F, "ocean Aq");

        FieldSample nether = SimpleBiomeField.compute(new SimpleBiomeField.ClimateInputs(
                2.0F, 0.0F, false, false, SimpleBiomeField.DimensionKind.NETHER), 64);
        require(nether.thermalDensity() > 70.0F, "nether D_th");
        require(nether.aqueousDensity() < 8.0F, "nether Aq");

        FieldSample end = SimpleBiomeField.compute(new SimpleBiomeField.ClimateInputs(
                0.5F, 0.0F, false, false, SimpleBiomeField.DimensionKind.END), 64);
        require(end.primordialDensity() > 65.0F, "end Ae");
        require(end.thermalDensity() < 20.0F, "end D_th");
        require(end.aqueousDensity() < 5.0F, "end Aq");
    }

    private static void heightGradient() {
        SimpleBiomeField.ClimateInputs plains = new SimpleBiomeField.ClimateInputs(
                0.5F, 0.5F, false, false, SimpleBiomeField.DimensionKind.OVERWORLD);
        FieldSample low = SimpleBiomeField.compute(plains, 32);
        FieldSample high = SimpleBiomeField.compute(plains, 200);
        require(low.chargeImbalance() < 0.0F, "low charge must be negative");
        require(high.chargeImbalance() > 0.0F, "high charge must be positive");
        require(high.thermalDensity() < low.thermalDensity(), "altitude must lower D_th");
    }

    /**
     * Guards the S4 per-tick rates, the separator carry, and the split
     * mixed/phase capacity helpers against silent regression.
     */
    private static void machineRates() {
        require(MachineBlockEntity.INTAKE_MPU_PER_TICK == 25L, "intake must move 25 mPU/t");
        require(MachineBlockEntity.INTAKE_FE_PER_TICK == 15L, "intake must cost 15 FE/t");
        require(MachineBlockEntity.SEPARATOR_MAX_INPUT_MPU_PER_TICK == 25L,
                "separator must cap input at 25 mPU/t");
        require(MachineBlockEntity.SEPARATOR_FE_PER_TICK == 25L, "separator must cost 25 FE/t");
        require(MachineBlockEntity.separatorOutput(25L) == 23L, "separator 25 -> 23");
        require(MachineBlockEntity.separatorLoss(25L) == 2L, "separator loss must be 2");
        require(MachineBlockEntity.separatorOutput(25L, 75L) == 24L,
                "carry 75 must make the next 25 mPU tick output 24");
        require(MachineBlockEntity.separatorNextCarry(25L, 0L) == 75L,
                "25 mPU must leave a carry of 75");
        require(MachineBlockEntity.separatorNextCarry(25L, 75L) == 50L,
                "carry 75 + 25 mPU must leave 50");

        long carry = 0L;
        long produced = 0L;
        for (int tick = 0; tick < 4; tick++) {
            produced += MachineBlockEntity.separatorOutput(25L, carry);
            carry = MachineBlockEntity.separatorNextCarry(25L, carry);
        }
        require(produced == 95L, "four 25 mPU separator ticks must yield 95 mPU");
        require(carry == 0L, "separator carry must return to 0 after four ticks");

        require(MachineBlockEntity.GENERATOR_INPUT_MPU_PER_TICK == 25L,
                "generator must consume 25 mPU T/t");
        require(MachineBlockEntity.GENERATOR_OUTPUT_MPU_PER_TICK == 25L,
                "generator must produce 25 mPU C/t");
        require(MachineBlockEntity.GENERATOR_FE_PER_TICK == 500L,
                "generator must produce 500 FE/t (20 FE per mPU)");
        require(MachineBlockEntity.GENERATOR_FE_PER_TICK
                        / MachineBlockEntity.GENERATOR_INPUT_MPU_PER_TICK == 20L,
                "generator must pay 20 FE per mPU burned");
        require(MachineBlockEntity.ENERGY_CAPACITY == 1_000_000L,
                "machines must buffer 1,000,000 FE");
        require(MachineBlockEntity.ENERGY_CAPACITY
                        >= MachineBlockEntity.GENERATOR_FE_PER_TICK * 1_000L,
                "energy buffer must hold at least 1,000 generator ticks");
        require(MachineBlockEntity.PIPE_MPU_PER_TRANSFER == 500L,
                "pipes must move 500 mPU per active tick (100 mPU/t)");
        require(MachineBlockEntity.PIPE_PHASE_MPU_PER_TRANSFER == 100L,
                "phase pipes must move 100 mPU per phase per active tick (20 mPU/t)");
        require(MachineBlockEntity.PIPE_MPU_PER_TRANSFER
                        == MachineBlockEntity.INTAKE_MPU_PER_TICK * 4L,
                "a mixed pipe must carry four intake towers");
        require(MachineBlockEntity.PIPE_PHASE_MPU_PER_TRANSFER
                        == MachineBlockEntity.GENERATOR_INPUT_MPU_PER_TICK * 4L,
                "a phase pipe must carry four generators");

        require(MachineBlockEntity.mixedAccepted(2_000_000L, 900_000L) == 100_000L,
                "mixed insert must accept only the remaining shared space");
        require(MachineBlockEntity.mixedAccepted(1L, MachineBlockEntity.MIXED_CAPACITY) == 0L,
                "a full mixed buffer must accept nothing");
        Phase tank = Phase.AQUANTUM;
        require(MachineBlockEntity.phaseAccepted(tank, 9_000_000L, 0L) == tank.tankCapacity(),
                "a phase tank must cap at its own capacity");
        require(MachineBlockEntity.phaseAccepted(tank, 1L, tank.tankCapacity()) == 0L,
                "a full phase tank must accept nothing");

        int count = phaseCount();
        long[] plan = new long[count];
        plan[Phase.AQUANTUM.index()] = 1L;
        long[] phaseAmounts = new long[count];
        phaseAmounts[Phase.AQUANTUM.index()] = tank.tankCapacity();
        require(!MachineBlockEntity.separatorFits(plan, phaseAmounts),
                "separator precheck must reject a full output phase");
        phaseAmounts[Phase.AQUANTUM.index()] = tank.tankCapacity() - 1L;
        require(MachineBlockEntity.separatorFits(plan, phaseAmounts),
                "separator precheck must accept one free mPU");
    }

    /**
     * Guards the light-heat modifier: block light and effective sky light are
     * two separate channels, the day/night reduction makes night dimmer than
     * noon, rain dims the sky further, and Cryon pays the configured share
     * without going negative. Expected values derive from the live config so a
     * tuned server still passes.
     */
    private static void lightHeat() {
        FieldSample base = new FieldSample(10.0F, 10.0F, 1.0F, 1.0F, 2.0F, 3.0F);
        FieldSample unlit = LightHeatParticleModifier.apply(base, 0, 0, 0, false);
        require(near(unlit.thermion(), 10.0F) && near(unlit.cryon(), 10.0F)
                        && near(unlit.voltixPos(), 1.0F) && near(unlit.voltixNeg(), 1.0F)
                        && near(unlit.aquantum(), 2.0F) && near(unlit.aetheron(), 3.0F),
                "zero block and sky light must leave the sample unchanged");

        float share = LightHeatParticleModifier.cryonShare();
        float skyCoefficient = LightHeatParticleModifier.heatPerSky();
        float blockCoefficient = LightHeatParticleModifier.heatPerArtificial();

        float noonHeat = LightHeatParticleModifier.skyEffective(15, 0, false) * skyCoefficient;
        FieldSample noon = LightHeatParticleModifier.apply(base, 0, 15, 0, false);
        require(near(noon.thermion(), 10.0F + noonHeat),
                "noon sky light must add its own heat to Thermion");
        require(near(noon.cryon(), Math.max(0.0F, 10.0F - noonHeat * share)),
                "Cryon must pay the configured share of the sky-light gain");

        float nightHeat = LightHeatParticleModifier.skyEffective(15, 11, false) * skyCoefficient;
        FieldSample night = LightHeatParticleModifier.apply(base, 0, 15, 11, false);
        require(near(night.thermion(), 10.0F + nightHeat), "night heat must follow the reduced sky light");
        require(nightHeat < noonHeat || noonHeat == 0.0F,
                "night sky light must be clearly dimmer than noon");

        float rainyHeat = LightHeatParticleModifier.skyEffective(15, 0, true) * skyCoefficient;
        FieldSample rainy = LightHeatParticleModifier.apply(base, 0, 15, 0, true);
        require(near(rainy.thermion(), 10.0F + rainyHeat), "rain must scale the sky-light heat");
        require(rainyHeat < noonHeat || noonHeat == 0.0F, "rain must dim the sky-light heat below noon");

        float torchHeat = 14 * blockCoefficient;
        FieldSample torch = LightHeatParticleModifier.apply(base, 14, 0, 0, false);
        require(near(torch.thermion(), 10.0F + torchHeat), "14 block light must add its own heat");
        require(near(torch.cryon(), Math.max(0.0F, 10.0F - torchHeat * share)),
                "Cryon must pay the configured share of the block-light gain");

        FieldSample dry = new FieldSample(0.0F, 0.0F, 2.0F, 3.0F, 4.0F, 5.0F);
        FieldSample litDry = LightHeatParticleModifier.apply(dry, 15, 0, 0, false);
        require(litDry.cryon() == 0.0F, "Cryon must never go negative");
        require(near(litDry.thermion(), 15 * blockCoefficient), "Thermion gain must be independent of Cryon");
        require(near(litDry.aquantum(), 4.0F) && near(litDry.aetheron(), 5.0F)
                        && near(litDry.voltixPos(), 2.0F) && near(litDry.voltixNeg(), 3.0F),
                "the other phases must be preserved");
    }

    /**
     * Guards the temperature model: ambient altitude falloff, heat/chill clamps,
     * the heat-source table, and the weakened light-heat defaults.
     */
    private static void temperature() {
        require(near(TemperatureProbe.ambientFor(0.8F, 64), 300.0),
                "biome 0.8 at y=64 must be 300 K");
        require(near(TemperatureProbe.ambientFor(0.0F, 64), 280.0),
                "biome 0.0 at y=64 must be 280 K");
        require(near(TemperatureProbe.ambientFor(0.8F, 192), 280.8),
                "biome 0.8 at y=192 must be 280.8 K");

        require(near(TemperatureProbe.heatOf(300.0), 15.0F), "heatOf(300) must be 15 T");
        require(near(TemperatureProbe.heatOf(2000.0), 20.0F), "heatOf must cap at 20 T");
        require(near(TemperatureProbe.heatOf(-200.0), -5.0F), "heatOf must floor at -5 T");
        require(near(TemperatureProbe.chillOf(-100.0), 3.0F), "chillOf(-100) must be 3 C");
        require(near(TemperatureProbe.chillOf(50.0), 0.0F),
                "chillOf must stay zero when warmer than ambient");

        require(near(TemperatureSources.temperatureOf(Blocks.MAGMA_BLOCK.defaultBlockState()), 1000.0F),
                "magma blocks must read 1000 K");
        require(near(TemperatureSources.temperatureOf(Blocks.FURNACE.defaultBlockState()
                        .setValue(BlockStateProperties.LIT, true)), 900.0F),
                "lit furnaces must read 900 K");
        require(TemperatureSources.temperatureOf(Blocks.STONE.defaultBlockState()) == null,
                "plain stone must not be a heat source");

        require(near(Config.PF_LIGHT_HEAT_ARTIFICIAL.get().floatValue(), 0.30F),
                "block-light heat default must be 0.30");
        require(near(Config.PF_LIGHT_HEAT_SKY.get().floatValue(), 0.15F),
                "sky-light heat default must be 0.15");
    }

    private static boolean near(float actual, float expected) {
        return Math.abs(actual - expected) < 1.0E-4F;
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-4;
    }

    private static long sum(long[] values) {
        long total = 0L;
        for (long value : values) {
            total += value;
        }
        return total;
    }

    private static float sum(float[] values) {
        float total = 0.0F;
        for (float value : values) {
            total += value;
        }
        return total;
    }

    private static final class TestStorage implements MixedStorage {
        private final long[] values = new long[phaseCount()];
        private final long capacity;

        TestStorage(long capacity) {
            this.capacity = capacity;
        }

        @Override
        public long getAmount(Phase phase) {
            return values[phase.index()];
        }

        @Override
        public long getCapacity(Phase phase) {
            return capacity;
        }

        @Override
        public long insert(Phase phase, long amount, boolean simulate) {
            long moved = Math.min(Math.max(0L, amount), capacity - values[phase.index()]);
            if (!simulate) {
                values[phase.index()] += moved;
            }
            return moved;
        }

        @Override
        public long extract(Phase phase, long amount, boolean simulate) {
            long moved = Math.min(Math.max(0L, amount), values[phase.index()]);
            if (!simulate) {
                values[phase.index()] -= moved;
            }
            return moved;
        }
    }
}
