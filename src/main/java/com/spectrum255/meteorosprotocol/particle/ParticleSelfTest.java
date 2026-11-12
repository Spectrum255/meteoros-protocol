package com.spectrum255.meteorosprotocol.particle;

import com.spectrum255.localizedrain.api.WeatherType;
import com.spectrum255.meteorosprotocol.Config;
import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.compat.LightHeatParticleModifier;
import com.spectrum255.meteorosprotocol.compat.TemperatureProbe;
import com.spectrum255.meteorosprotocol.compat.TemperatureSources;
import com.spectrum255.meteorosprotocol.compat.WeatherBonusConfig;
import com.spectrum255.meteorosprotocol.compat.WeatherBonusRules;
import com.spectrum255.meteorosprotocol.compat.WeatherParticleModifier;
import com.spectrum255.meteorosprotocol.machine.MachineBlockEntity;
import com.spectrum255.meteorosprotocol.machine.SideChannel;
import com.spectrum255.meteorosprotocol.machine.SideMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * S1 structural self-test for the v2 data model. Runs from
 * {@code /meteoros selftest} and never needs a world: all formula checks go
 * through the pure {@link SimpleBiomeField#compute} entry point.
 *
 * <p>Most checks are phase-count agnostic: they assert that the six core phases
 * come first, in order, and derive every array size from {@link Phase#all()} so
 * addon phases do not break them. The registry group additionally pins the main
 * mod's own seventh phase, impure aetheron.</p>
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
        check(failures, "side config", ParticleSelfTest::sideConfig);
        check(failures, "number formatting", ParticleSelfTest::numberFormatting);
        check(failures, "intake loss and purifier", ParticleSelfTest::intakeAndPurifier);
        check(failures, "stratosphere", ParticleSelfTest::stratosphere);
        check(failures, "weather bonuses", ParticleSelfTest::weatherBonuses);
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

        // The main mod ships one addon phase of its own: impure aetheron.
        require(all.size() == 7, "expected six core phases plus impure aetheron");
        Phase impure = Phase.IMPURE_AETHERON;
        require(!impure.core(), "impure aetheron must not be a core phase");
        require(!impure.atmospheric(), "impure aetheron is not part of the atmospheric field");
        require(impure.index() == 6, "impure aetheron must take the first addon index");
        require(impure.rgb() == 0xB9BDC0, "impure aetheron colour");
        require(all.get(6) == impure, "impure aetheron must be the last phase in Phase.all()");
        PhaseSampler impureSampler = PhaseSamplers.sampler(impure);
        require(impureSampler != null, "impure aetheron needs a sampler");
        FieldSample aeBase = new FieldSample(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 10.0F);
        require(near(impureSampler.sample(null, null, aeBase), 1.0F),
                "impure aetheron sampler must track 10% of the local Aetheron");
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
                "the upgraded generator's byproduct budget stays 25 mPU/t");
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
        require(MachineBlockEntity.PIPE_MPU_PER_TRANSFER == Integer.MAX_VALUE,
                "pipes must have no artificial mixed throughput cap");
        require(MachineBlockEntity.PIPE_PHASE_MPU_PER_TRANSFER == Integer.MAX_VALUE,
                "phase pipes must have no artificial per-phase throughput cap");
        require(MachineBlockEntity.ACTIVE_IO_BUDGET_PER_CALL == Integer.MAX_VALUE,
                "machine active I/O must have no artificial per-call cap");
        require(MachineBlockEntity.PORT_MPU_PER_TRANSFER == Integer.MAX_VALUE,
                "the configurable port must have no artificial mixed cap");
        require(MachineBlockEntity.PORT_PHASE_MPU_PER_TRANSFER == Integer.MAX_VALUE,
                "the configurable port must have no artificial per-phase cap");
        require(MachineBlockEntity.ENERGY_TRANSFER_PER_TICK == Integer.MAX_VALUE,
                "energy push must have no artificial per-tick cap");
        require(MachineBlockEntity.ITEM_TRANSFER_PER_TICK == Integer.MAX_VALUE,
                "item push must have no artificial per-tick cap");
        require(MachineBlockEntity.INTAKE_MPU_PER_TICK == 25L
                        && MachineBlockEntity.SEPARATOR_MAX_INPUT_MPU_PER_TICK == 25L
                        && MachineBlockEntity.GENERATOR_INPUT_MPU_PER_TICK == 25L,
                "processing rates must stay at 25 mPU/t after the transport cap change");

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

    private static void sideConfig() {
        require(SideMode.NONE.next() == SideMode.IN, "NONE.next() must be IN");
        require(SideMode.IN.next() == SideMode.OUT, "IN.next() must be OUT");
        require(SideMode.OUT.next() == SideMode.BOTH, "OUT.next() must be BOTH");
        require(SideMode.BOTH.next() == SideMode.NONE, "BOTH.next() must wrap to NONE");
        require(SideMode.NONE.previous() == SideMode.BOTH, "NONE.previous() must wrap to BOTH");
        require(SideMode.byId(-1) == SideMode.NONE && SideMode.byId(4) == SideMode.NONE,
                "byId must fall back to NONE outside 0..3");

        int mask = MachineBlockEntity.withMode(0, Direction.NORTH, SideMode.BOTH);
        require(MachineBlockEntity.modeAt(mask, Direction.NORTH) == SideMode.BOTH,
                "NORTH must round-trip BOTH");
        require(MachineBlockEntity.modeAt(mask, Direction.SOUTH) == SideMode.NONE,
                "writing NORTH must not disturb SOUTH");
        mask = MachineBlockEntity.withMode(mask, Direction.UP, SideMode.IN);
        require(MachineBlockEntity.modeAt(mask, Direction.UP) == SideMode.IN, "UP must read IN");
        require(MachineBlockEntity.modeAt(mask, Direction.NORTH) == SideMode.BOTH,
                "writing UP must not disturb NORTH");

        for (Direction direction : Direction.values()) {
            require(MachineBlockEntity.modeAt(0xFFF, direction) == SideMode.BOTH,
                    "the all-BOTH default must read BOTH on every face");
            require(MachineBlockEntity.modeAt(0, direction) == SideMode.NONE,
                    "the weather default must read NONE on every face");
        }

        // Four-channel defaults per machine family.
        MachineBlockEntity generator = new MachineBlockEntity(
                BlockPos.ZERO, MeteorosProtocol.THERMAL_GENERATOR.get().defaultBlockState());
        require(generator.energySideMask() == 0xAAA,
                "generator must default to all-OUT energy");
        require(generator.sideMode(Direction.UP, SideChannel.MIXED) == SideMode.BOTH,
                "generator particle faces must default to BOTH");

        MachineBlockEntity separator = new MachineBlockEntity(
                BlockPos.ZERO, MeteorosProtocol.PHASE_SEPARATOR.get().defaultBlockState());
        require(separator.energySideMask() == 0x555,
                "separator must default to all-IN energy");

        MachineBlockEntity intake = new MachineBlockEntity(
                BlockPos.ZERO, MeteorosProtocol.PARTICLE_INTAKE.get().defaultBlockState());
        require(intake.sideMode(Direction.UP, SideChannel.ENERGY) == SideMode.IN,
                "intake energy faces must default to IN");
        require(intake.sideMode(Direction.UP, SideChannel.ITEM) == SideMode.BOTH,
                "intake item faces must default to BOTH");

        MachineBlockEntity port = new MachineBlockEntity(
                BlockPos.ZERO, MeteorosProtocol.PARTICLE_PORT.get().defaultBlockState());
        require(port.sideMode(Direction.UP, SideChannel.ENERGY) == SideMode.NONE
                        && port.sideMode(Direction.UP, SideChannel.ITEM) == SideMode.NONE,
                "particle port must default to no energy/item I/O");
        require(port.sideMode(Direction.UP, SideChannel.MIXED) == SideMode.BOTH,
                "particle port particle faces must default to BOTH");

        MachineBlockEntity weather = new MachineBlockEntity(
                BlockPos.ZERO, MeteorosProtocol.WEATHER_STATION.get().defaultBlockState());
        for (SideChannel channel : SideChannel.values()) {
            require(weather.sideMode(Direction.UP, channel) == SideMode.NONE,
                    "weather blocks must disable every channel");
        }

        // ENERGY channel gates the per-face energy capability.
        require(intake.energyStorageForSide(Direction.UP).canReceive(),
                "an IN energy face must be able to receive");
        require(intake.energyStorageForSide(Direction.UP).receiveEnergy(1_000, false) == 1_000,
                "an IN energy face must accept FE");
        intake.setSideMode(Direction.UP, SideChannel.ENERGY, SideMode.NONE);
        require(intake.energyStorageForSide(Direction.UP).receiveEnergy(1_000, false) == 0,
                "a NONE energy face must reject FE");
        require(intake.energyStorageForSide(Direction.UP).extractEnergy(100, false) == 0,
                "a NONE energy face must not extract FE");
        intake.setSideMode(Direction.UP, SideChannel.ENERGY, SideMode.OUT);
        require(intake.energyStorageForSide(Direction.UP).receiveEnergy(1_000, false) == 0,
                "an OUT energy face must reject FE");
        require(intake.energyStorageForSide(Direction.UP).extractEnergy(250, false) == 250,
                "an OUT energy face must extract FE");

        // ITEM channel gates the intake tower's two slots; slot 0 is never extractable.
        require(intake.itemHandlerForSide(Direction.UP) != null,
                "the intake tower must expose an item handler");
        require(generator.itemHandlerForSide(Direction.UP) == null,
                "machines without item slots must not expose an item handler");
        ItemStack filter = new ItemStack(MeteorosProtocol.FILTER_CARTRIDGE.get());
        ItemStack canister = new ItemStack(MeteorosProtocol.CANISTER.get());
        intake.setSideMode(Direction.UP, SideChannel.ITEM, SideMode.BOTH);
        require(intake.itemHandlerForSide(Direction.UP).insertItem(0, filter, false).isEmpty(),
                "a filter must insert into slot 0");
        require(intake.itemHandlerForSide(Direction.UP).extractItem(0, 1, false).isEmpty(),
                "slot 0 must never be extractable");
        require(intake.itemHandlerForSide(Direction.UP).insertItem(1, canister, false).isEmpty(),
                "a canister must insert into slot 1");
        intake.setSideMode(Direction.UP, SideChannel.ITEM, SideMode.OUT);
        require(intake.itemHandlerForSide(Direction.UP).insertItem(1, canister, false).getCount() == 1,
                "an OUT item face must reject inserts");
        require(intake.itemHandlerForSide(Direction.UP).extractItem(1, 1, false).getCount() == 1,
                "an OUT item face must allow canister extraction");

        // Auto-I/O master switch: defaults on, round-trips through NBT as "AutoIo".
        require(generator.autoIo(), "auto I/O must default to true");
        generator.setAutoIo(false);
        require(!generator.autoIo(), "setAutoIo(false) must stick");
        CompoundTag autoIoTag = new CompoundTag();
        generator.saveAdditional(autoIoTag, RegistryAccess.EMPTY);
        require(autoIoTag.contains("AutoIo"), "auto I/O must save under the 'AutoIo' key");
        require(!autoIoTag.getBoolean("AutoIo"), "AutoIo must persist the off state");
        generator.setAutoIo(true);
        generator.loadAdditional(autoIoTag, RegistryAccess.EMPTY);
        require(!generator.autoIo(), "loadAdditional must restore auto I/O = false");
        CompoundTag legacyTag = new CompoundTag();
        generator.setAutoIo(true);
        generator.loadAdditional(legacyTag, RegistryAccess.EMPTY);
        require(generator.autoIo(), "saves without AutoIo must default to true");

        // Arrival order: first-in-first-shown. Emptying a tank clears its slot and
        // a later refill counts as a new arrival; the order round-trips through NBT.
        MachineBlockEntity arrival = new MachineBlockEntity(
                BlockPos.ZERO, MeteorosProtocol.PHASE_SEPARATOR.get().defaultBlockState());
        require(arrival.arrivalSeq(Phase.AETHERON.index()) == -1,
                "empty tanks must start without an arrival slot");
        arrival.phaseStorage().insert(Phase.AETHERON, 10L, false);
        arrival.refreshArrivalOrderForSelfTest();
        int firstSeq = arrival.arrivalSeq(Phase.AETHERON.index());
        require(firstSeq >= 1, "a filled tank must get a positive arrival slot");
        arrival.phaseStorage().insert(Phase.THERMION, 10L, false);
        arrival.refreshArrivalOrderForSelfTest();
        int secondSeq = arrival.arrivalSeq(Phase.THERMION.index());
        require(secondSeq > firstSeq, "a later tank must get a larger arrival slot");
        arrival.phaseStorage().extract(Phase.AETHERON, 10L, false);
        arrival.refreshArrivalOrderForSelfTest();
        require(arrival.arrivalSeq(Phase.AETHERON.index()) == -1,
                "an emptied tank must clear its arrival slot");
        arrival.phaseStorage().insert(Phase.AETHERON, 10L, false);
        arrival.refreshArrivalOrderForSelfTest();
        require(arrival.arrivalSeq(Phase.AETHERON.index()) > secondSeq,
                "refilling an emptied tank must count as a new arrival");
        CompoundTag arrivalTag = new CompoundTag();
        arrival.saveAdditional(arrivalTag, RegistryAccess.EMPTY);
        require(arrivalTag.contains("ArrivalSeq"), "arrival order must save under 'ArrivalSeq'");
        MachineBlockEntity arrivalLoaded = new MachineBlockEntity(
                BlockPos.ZERO, MeteorosProtocol.PHASE_SEPARATOR.get().defaultBlockState());
        arrivalLoaded.loadAdditional(arrivalTag, RegistryAccess.EMPTY);
        for (Phase phase : Phase.all()) {
            require(arrivalLoaded.arrivalSeq(phase.index()) == arrival.arrivalSeq(phase.index()),
                    "arrival order must round-trip through NBT");
        }

        // Button ids must stay clear of the per-phase selection range: adding the
        // seventh phase shifted the extract id from 8 to 9, so the old hard-coded
        // screen ids silently selected a phase instead. Check the formula for
        // both the historical six-phase registry and the current one.
        for (int phaseCount : new int[]{6, 7}) {
            int extractId = 2 + phaseCount;
            int autoIoId = extractId + 1;
            require(!(extractId >= 2 && extractId < 2 + phaseCount),
                    "the extract button id must not overlap the phase selection range");
            require(autoIoId == extractId + 1,
                    "the auto-I/O button id must directly follow the extract id");
            require(autoIoId != extractId,
                    "the extract and auto-I/O button ids must differ");
            require(autoIoId != 0 && autoIoId != 1,
                    "machine button ids must not collide with power or redstone");
        }

        // Default input whitelist: only the generator is picky. Internal
        // production writes must bypass the filtered entry points.
        require(generator.inputPhases().equals(List.of(Phase.THERMION)),
                "the generator must only accept Thermion from outside");
        require(separator.inputPhases().equals(Phase.all()),
                "the separator must accept every phase");
        require(intake.inputPhases().equals(Phase.all()),
                "the intake tower must accept every phase");
        require(port.inputPhases().equals(Phase.all()),
                "the particle port must accept every phase");
        require(weather.inputPhases().isEmpty(),
                "weather blocks must accept no phases");
        require(generator.insert(Phase.CRYON, 1_000L, false) == 0L,
                "the generator must reject external Cryon mixed inserts");
        require(generator.getAmount(Phase.CRYON) == 0L,
                "a rejected insert must not touch the mixed buffer");
        require(generator.phaseStorage().insert(Phase.CRYON, 1_000L, false) == 0L,
                "the generator must reject external Cryon tank inserts");
        require(generator.insert(Phase.THERMION, 1_000L, false) == 1_000L,
                "the generator must still accept external Thermion");
        // Capacity is now allocated per machine type: the generator owns a
        // Thermion tank only, so even internal writes to anything else are
        // refused (no hidden storage).
        require(generator.phaseStorage().getCapacity(Phase.CRYON) == 0L,
                "the generator must allocate no Cryon tank");
        require(generator.phaseStorage().getCapacity(Phase.THERMION)
                        == Phase.THERMION.tankCapacity(),
                "the generator must allocate its full Thermion tank");
        require(generator.addPhaseInternal(Phase.CRYON, 500L) == 0L,
                "internal writes must not land in a zero-capacity tank");
        require(generator.phaseStorage().getAmount(Phase.CRYON) == 0L,
                "a refused internal write must leave the tank empty");
        require(separator.addPhaseInternal(Phase.CRYON, 500L) == 500L,
                "internal writes must bypass the whitelist on a machine with that tank");
        require(separator.phaseStorage().getAmount(Phase.CRYON) == 500L,
                "internal Cryon must land in a purifier tank");

        // Per-machine tank allocation: the generator keeps one phase, the
        // purifier four, containers all seven, weather blocks none.
        require(generator.storagePhases().equals(List.of(Phase.THERMION)),
                "the generator must only allocate a Thermion tank");
        require(separator.storagePhases().equals(List.of(
                        Phase.IMPURE_AETHERON, Phase.AETHERON, Phase.THERMION, Phase.CRYON)),
                "the purifier must allocate impure Aetheron and its three outputs");
        require(intake.storagePhases().equals(Phase.all()),
                "the intake tower must keep a tank for every phase");
        require(port.storagePhases().equals(Phase.all()),
                "the port must keep a tank for every phase");
        require(weather.storagePhases().isEmpty(),
                "weather blocks must allocate no phase tanks");
        long generatorCapacity = 0L;
        long purifierCapacity = 0L;
        for (Phase phase : Phase.all()) {
            generatorCapacity += generator.phaseStorage().getCapacity(phase);
            purifierCapacity += separator.phaseStorage().getCapacity(phase);
        }
        require(generatorCapacity == Phase.THERMION.tankCapacity(),
                "the generator's total tank capacity must be one tank");
        require(purifierCapacity == 4L * Phase.THERMION.tankCapacity(),
                "the purifier's total tank capacity must be four tanks");
        long weatherCapacity = 0L;
        for (Phase phase : Phase.all()) {
            weatherCapacity += weather.phaseStorage().getCapacity(phase);
        }
        require(weatherCapacity == 0L, "weather blocks must have zero tank capacity");

        // Impure aetheron: never accepted from outside; the generator byproduct
        // path is gated behind an upgrade that does not exist yet.
        require(generator.insert(Phase.IMPURE_AETHERON, 1_000L, false) == 0L,
                "the generator must reject external impure Aetheron mixed inserts");
        require(generator.phaseStorage().insert(Phase.IMPURE_AETHERON, 1_000L, false) == 0L,
                "the generator must reject external impure Aetheron tank inserts");
        require(generator.addPhaseInternal(Phase.IMPURE_AETHERON, 500L) == 0L,
                "the generator allocates no impure Aetheron tank");
        require(generator.relevantPhases().equals(List.of(Phase.THERMION)),
                "a bare generator only shows Thermion: it has no byproduct");
        require(!generator.hasGeneratorUpgrade(), "the generator upgrade slot is not implemented yet");

        // The tower filters pure Aetheron out of the captured mixture but keeps
        // the impure form.
        float[] shares = new float[Phase.all().size()];
        shares[Phase.AETHERON.index()] = 60.0F;
        shares[Phase.IMPURE_AETHERON.index()] = 6.0F;
        MachineBlockEntity.zeroPureAetheronShare(shares);
        require(shares[Phase.AETHERON.index()] == 0.0F,
                "the intake tower must not capture pure Aetheron");
        require(shares[Phase.IMPURE_AETHERON.index()] == 6.0F,
                "the intake tower must keep capturing impure Aetheron");

        // A bare generator burns Thermion for FE only: +500 FE and no byproduct.
        generator.addPhaseInternal(Phase.THERMION,
                MachineBlockEntity.GENERATOR_INPUT_MPU_PER_TICK);
        long energyBefore = generator.energy();
        long mixedBefore = generator.getTotal();
        long tankImpureBefore = generator.phaseStorage().getAmount(Phase.IMPURE_AETHERON);
        require(generator.runGeneratorTickForSelfTest(),
                "the generator must run with one operation of Thermion in its tank");
        require(generator.energy() == energyBefore + MachineBlockEntity.GENERATOR_FE_PER_TICK,
                "a generator tick must add 500 FE");
        require(generator.getAmount(Phase.IMPURE_AETHERON) == 0L,
                "a bare generator must not spill impure Aetheron into the mixed buffer");
        require(generator.phaseStorage().getAmount(Phase.IMPURE_AETHERON) == tankImpureBefore,
                "a bare generator must not produce impure Aetheron");
        require(generator.getTotal() == mixedBefore,
                "a generator tick must not change the mixed buffer");

        // A full FE buffer must back-pressure the fuel line: no external Thermion
        // is accepted and no tick consumes what is already in the tank.
        generator.receiveEnergy((int) MachineBlockEntity.ENERGY_CAPACITY, false);
        require(generator.energy() == MachineBlockEntity.ENERGY_CAPACITY,
                "the generator energy buffer must fill for the back-pressure check");
        long tankBeforeFull = generator.phaseStorage().getAmount(Phase.THERMION);
        long mixedBeforeFull = generator.getTotal();
        require(generator.insert(Phase.THERMION, 1_000L, false) == 0L,
                "a full generator must refuse external Thermion mixed inserts");
        require(generator.phaseStorage().insert(Phase.THERMION, 1_000L, false) == 0L,
                "a full generator must refuse external Thermion tank inserts");
        require(generator.phaseStorage().getAmount(Phase.THERMION) == tankBeforeFull,
                "refused Thermion must not land in the tank");
        require(generator.getTotal() == mixedBeforeFull,
                "refused Thermion must not land in the mixed buffer");
        require(!generator.runGeneratorTickForSelfTest(),
                "a full generator must not burn Thermion");
        require(generator.phaseStorage().getAmount(Phase.THERMION) == tankBeforeFull,
                "a paused generator must leave its tank untouched");
    }

    /**
     * Intake capture loss, impure-Aetheron sampler baseline and the purifier
     * conversion. These rule the "why is my tower only storing 80%" behaviour and
     * the impure-Aetheron economy, so they are pinned even though they need no
     * level.
     */
    private static void intakeAndPurifier() {
        // Capture loss only touches the heat/cold/aether phases.
        long[] amounts = new long[phaseCount()];
        for (int index = 0; index < amounts.length; index++) {
            amounts[index] = 100L;
        }
        MachineBlockEntity.applyIntakeCaptureLoss(amounts);
        long loss = Math.round(MachineBlockEntity.intakeCaptureLossRatio() * 100.0D);
        long kept = 100L - loss;
        require(amounts[Phase.THERMION.index()] == kept, "Thermion must lose the capture fraction");
        require(amounts[Phase.CRYON.index()] == kept, "Cryon must lose the capture fraction");
        require(amounts[Phase.AETHERON.index()] == kept, "Aetheron must lose the capture fraction");
        require(amounts[Phase.IMPURE_AETHERON.index()] == kept,
                "impure Aetheron must lose the capture fraction");
        require(amounts[Phase.VOLTIX_POS.index()] == 100L,
                "positive Voltix must be captured losslessly");
        require(amounts[Phase.VOLTIX_NEG.index()] == 100L,
                "negative Voltix must be captured losslessly");
        require(amounts[Phase.AQUANTUM.index()] == 100L,
                "Aquantum must be captured losslessly");

        // Impure Aetheron sampler: 10% of ambient aetheron, times the 0.25 upgrade factor.
        PhaseSampler sampler = PhaseSamplers.sampler(Phase.IMPURE_AETHERON);
        require(sampler != null, "impure Aetheron must register a sampler");
        FieldSample base = new FieldSample(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 10.0F);
        float sampled = sampler.sample(null, BlockPos.ZERO, base);
        require(near(sampled, 10.0F * 0.10F * 0.25F),
                "the impure Aetheron sampler must use the 0.10 x 0.25 baseline");

        // Purifier: 25 impure -> 25 pure + 5 Thermion + 5 Cryon, and no-material
        // ticks must not spend energy.
        MachineBlockEntity purifier = new MachineBlockEntity(
                BlockPos.ZERO, MeteorosProtocol.PHASE_SEPARATOR.get().defaultBlockState());
        purifier.receiveEnergy(1_000, false);
        require(purifier.phaseStorage().insert(Phase.IMPURE_AETHERON, 25L, false) == 25L,
                "the purifier must accept impure Aetheron");
        long energyBefore = purifier.energy();
        require(purifier.runPurifierTickForSelfTest(),
                "the purifier must run with 25 mPU of impure Aetheron");
        require(purifier.energy() == energyBefore - MachineBlockEntity.SEPARATOR_FE_PER_TICK,
                "a purifier tick must spend its 25 FE");
        require(purifier.phaseStorage().getAmount(Phase.IMPURE_AETHERON) == 0L,
                "the purifier must consume its impure Aetheron");
        require(purifier.phaseStorage().getAmount(Phase.AETHERON) == 25L,
                "the purifier must return 25 mPU of pure Aetheron");
        require(purifier.phaseStorage().getAmount(Phase.THERMION) == 5L,
                "the purifier must return 5 mPU of Thermion");
        require(purifier.phaseStorage().getAmount(Phase.CRYON) == 5L,
                "the purifier must return 5 mPU of Cryon");
        long idleEnergy = purifier.energy();
        require(!purifier.runPurifierTickForSelfTest(),
                "the purifier must idle when it has no impure Aetheron");
        require(purifier.energy() == idleEnergy,
                "an idle purifier tick must not spend energy");
        require(purifier.relevantPhases().equals(List.of(
                        Phase.IMPURE_AETHERON, Phase.AETHERON, Phase.THERMION, Phase.CRYON)),
                "the purifier GUI must only show its four relevant phases");
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

    private static void numberFormatting() {
        require("0 mPU".equals(ParticleFormat.formatMPU(0L)), "formatMPU(0) should be \"0 mPU\"");
        require("999 mPU".equals(ParticleFormat.formatMPU(999L)), "formatMPU(999) should be \"999 mPU\"");
        require("1 kPU".equals(ParticleFormat.formatMPU(1_000L)), "formatMPU(1000) should be \"1 kPU\"");
        require("1.5 kPU".equals(ParticleFormat.formatMPU(1_500L)), "formatMPU(1500) should be \"1.5 kPU\"");
        require("1 MPU".equals(ParticleFormat.formatMPU(1_000_000L)),
                "formatMPU(1000000) should be \"1 MPU\"");
        require("23.8 MPU".equals(ParticleFormat.formatMPU(23_750_000L)),
                "formatMPU(23750000) should be \"23.8 MPU\"");
        require("1 MFE".equals(ParticleFormat.formatFE(999_980L)),
                "formatFE(999980) should promote to \"1 MFE\", got " + ParticleFormat.formatFE(999_980L));
        require("12,345,678 mPU".equals(ParticleFormat.formatPlain(12_345_678L, "mPU")),
                "formatPlain should keep thousands separators");
    }

    private static void stratosphere() {
        FieldSample base = new FieldSample(10.0F, 8.0F, 4.0F, 6.0F, 20.0F, 5.0F);
        int start = 720;
        int transition = 320;
        float thin = 0.60F;
        float perBlock = 0.08F;
        float aqua = 0.10F;
        float ion = 0.03F;

        FieldSample atTransition = sampleAt(base, 320.0F, 0, true, true, start, transition,
                thin, perBlock, aqua, ion);
        require(sameSample(atTransition, base), "y=transitionStartY must be unchanged");

        FieldSample y400 = sampleAt(base, 400.0F, 0, true, true, start, transition, thin, perBlock, aqua, ion);
        float t400 = 0.20F;
        float thin400 = 1.0F - (1.0F - thin) * t400;
        float aqua400 = 1.0F - (1.0F - aqua) * t400;
        require(near(thin400, 0.92F), "y=400 thin factor should be 0.92, got " + thin400);
        require(near(y400.thermion(), base.thermion() * thin400), "y=400 T should be scaled by the ramp");
        require(near(y400.aquantum(), base.aquantum() * thin400 * aqua400),
                "y=400 Aq should be scaled by thin*0.92 and aqua*0.82 (combined 0.7544)");
        require(near(y400.aetheron(), base.aetheron() + 1.28F), "y=400 Ae should gain 1.28");
        require(near(y400.voltixPos(), base.voltixPos() * thin400), "the transition band must not ionise");

        FieldSample y719 = sampleAt(base, 719.9F, 0, true, true, start, transition, thin, perBlock, aqua, ion);
        FieldSample y720 = sampleAt(base, 720.0F, 0, true, true, start, transition, thin, perBlock, aqua, ion);
        require(near(y720.aetheron(), base.aetheron() + 6.4F), "y=720 Ae should gain bandTop 6.4");
        require(near(y720.voltixPos(), base.voltixPos() * thin), "y=720 must not ionise yet");
        require(Math.abs(y719.aetheron() - y720.aetheron()) < 0.01F,
                "y=719.9 and y=720 must be continuous");

        float height = 80.0F;
        FieldSample day = sampleAt(base, 800.0F, 0, true, true, start, transition, thin, perBlock, aqua, ion);
        require(near(day.aetheron(), base.aetheron() + 12.8F),
                "y=800 Ae should be bandTop + h*perBlock = 12.8");
        require(near(day.thermion(), base.thermion() * thin), "y=800 T should be scaled by 0.60");
        require(near(day.aquantum(), base.aquantum() * thin * aqua), "y=800 Aq should be scaled by 0.06");
        require(near(day.voltixPos(), base.voltixPos() * thin + height * ion),
                "V+ should gain h*ion during the day");
        require(near(day.voltixNeg(), base.voltixNeg() * thin), "V- should gain nothing during the day");

        FieldSample night = sampleAt(base, 800.0F, 11, true, true, start, transition, thin, perBlock, aqua, ion);
        require(near(night.voltixPos(), base.voltixPos() * thin), "V+ should gain nothing at night");
        require(near(night.voltixNeg(), base.voltixNeg() * thin + height * ion),
                "V- should gain the night bonus");
        require(near(night.aetheron(), base.aetheron() + 12.8F), "Ae gain must not depend on the time of day");

        FieldSample noSky = sampleAt(base, 800.0F, 0, false, true, start, transition, thin, perBlock, aqua, ion);
        require(near(noSky.aetheron(), base.aetheron()), "no sky must zero the Ae gain");
        require(near(noSky.voltixPos(), base.voltixPos() * thin), "no sky must zero the ionisation");
        require(near(noSky.thermion(), base.thermion() * thin), "no sky must keep the thin-air penalty");

        FieldSample skyOff = sampleAt(base, 800.0F, 0, false, false, start, transition, thin, perBlock, aqua, ion);
        require(near(skyOff.aetheron(), base.aetheron() + 12.8F),
                "requireSky=false should restore the Ae gain");
        require(near(skyOff.voltixPos(), base.voltixPos() * thin + height * ion),
                "requireSky=false should restore the ionisation");

        require(StratosphereParticleModifier.isStratosphere(721, true, 720), "y=start+1 should match");
        require(!StratosphereParticleModifier.isStratosphere(720, true, 720), "y=start should not match");
        require(!StratosphereParticleModifier.isStratosphere(800, false, 720), "disabled should never match");

        require(near(TemperatureProbe.ambientForStratosphere(720, 720), 240.0),
                "stratosphere ambient at y=720 should be 240 K");
        require(near(TemperatureProbe.ambientForStratosphere(800, 720), 252.0),
                "stratosphere ambient at y=800 should be 252 K");
        require(near(TemperatureProbe.ambientForStratosphere(960, 720), 276.0),
                "stratosphere ambient at y=960 should be 276 K");
    }

    private static void weatherBonuses() {
        WeatherType rain = new WeatherType("weather.meteorosprotocol.test.rain", true,
                Biome.Precipitation.RAIN, true);
        WeatherType snow = new WeatherType("weather.meteorosprotocol.test.snow", true,
                Biome.Precipitation.SNOW, true);
        WeatherType dust = new WeatherType("weather.meteorosprotocol.test.dust", false,
                Biome.Precipitation.NONE, false);
        WeatherType dustThunder = new WeatherType("weather.meteorosprotocol.test.dust_thunder", false,
                Biome.Precipitation.NONE, true);

        WeatherBonusRules.Rules rainRules = WeatherBonusRules.defaultsFor(rain);
        require(near(rainRules.thermion(), -4.0F) && near(rainRules.cryon(), 4.0F)
                        && near(rainRules.aquantum(), 6.0F) && near(rainRules.aetheron(), 0.0F)
                        && near(rainRules.chargeDensity(), 12.0F),
                "rain defaults must be T-4 C+4 Aq+6 charge 12, got " + rainRules);
        WeatherBonusRules.Rules snowRules = WeatherBonusRules.defaultsFor(snow);
        require(near(snowRules.thermion(), -5.0F) && near(snowRules.cryon(), 5.0F)
                        && near(snowRules.aquantum(), 4.0F)
                        && near(snowRules.chargeDensity(), 12.0F),
                "snow defaults must be T-5 C+5 Aq+4 (less aqueous than rain), got " + snowRules);
        require(WeatherBonusRules.defaultsFor(dust).isZero(),
                "a no-precipitation type without thunder must default to zero");
        require(near(WeatherBonusRules.defaultsFor(dustThunder).chargeDensity(), 12.0F),
                "a no-precipitation type with thunder defaults to charge 12");

        FieldSample baseSample = new FieldSample(10.0F, 10.0F, 2.0F, 1.0F, 2.0F, 3.0F);
        WeatherParticleModifier.Result applied =
                WeatherParticleModifier.apply(baseSample, rainRules, true, 0.5F, 0.0F);
        require(near(applied.sample().thermion(), 6.0F)
                        && near(applied.sample().cryon(), 14.0F)
                        && near(applied.sample().aquantum(), 8.0F)
                        && near(applied.sample().aetheron(), 3.0F),
                "rain rules must add their deltas to the sample");
        require(near(applied.sample().chargeDensity(), 3.0F + 12.0F * 0.5F),
                "charge density must scale with storm intensity");
        require(applied.raining() && applied.thundering(),
                "an active rainy sample must report rain and thunder");

        WeatherBonusRules.Rules negative = new WeatherBonusRules.Rules(-100.0F, 0.0F, -100.0F,
                -100.0F, 0.0F);
        WeatherParticleModifier.Result clamped =
                WeatherParticleModifier.apply(baseSample, negative, true, 0.0F, 0.0F);
        require(clamped.sample().thermion() == 0.0F && clamped.sample().aquantum() == 0.0F
                        && clamped.sample().aetheron() == 0.0F,
                "negative deltas must clamp the reservoirs to zero");
        require(WeatherParticleModifier.apply(baseSample, rainRules, false, 0.0F, 0.0F)
                        .sample().equals(baseSample),
                "inactive weather must return the sample unchanged");

        Map<ResourceLocation, WeatherBonusRules.Entry> entries = new LinkedHashMap<>();
        entries.put(ResourceLocation.fromNamespaceAndPath("localizedrain", "rain"),
                WeatherBonusRules.Entry.fromType(rain, rainRules));
        entries.put(ResourceLocation.fromNamespaceAndPath("localizedrain", "snow"),
                WeatherBonusRules.Entry.fromType(snow, snowRules));
        String text = WeatherBonusConfig.render(entries, true, true);
        WeatherBonusConfig.Parsed parsed = WeatherBonusConfig.parse(text);
        require(parsed.enabled() && parsed.autoAddNewTypes(), "general flags must round trip");
        require(parsed.entries().size() == entries.size(), "all types must round trip");
        for (Map.Entry<ResourceLocation, WeatherBonusRules.Entry> e : entries.entrySet()) {
            WeatherBonusRules.Entry read = parsed.entries().get(e.getKey());
            require(read != null && read.rules().equals(e.getValue().rules())
                            && read.displayKey().equals(e.getValue().displayKey())
                            && read.precipitation().equals(e.getValue().precipitation()),
                    "weather entry " + e.getKey() + " must round trip, got " + read);
        }

        WeatherBonusConfig.Parsed tolerant = WeatherBonusConfig.parse("""
                [general]
                \tenabled = true
                \tbogus = maybe
                [types."example:dust"]
                \tthermion = nope
                \tcryon = 5.0
                \t# comment
                [not_a_section]
                """);
        WeatherBonusRules.Entry tolerantEntry = tolerant.entries()
                .get(ResourceLocation.fromNamespaceAndPath("example", "dust"));
        require(tolerantEntry != null && near(tolerantEntry.rules().cryon(), 5.0F)
                        && near(tolerantEntry.rules().thermion(), 0.0F),
                "a malformed line must not drop valid keys or throw");

        Map<ResourceLocation, WeatherType> registryTypes = new LinkedHashMap<>();
        registryTypes.put(ResourceLocation.fromNamespaceAndPath("localizedrain", "rain"), rain);
        registryTypes.put(ResourceLocation.fromNamespaceAndPath("example", "dust"), dust);
        WeatherBonusConfig.MergeResult merged = WeatherBonusConfig.merge(entries, registryTypes);
        WeatherBonusRules.Entry added = merged.entries()
                .get(ResourceLocation.fromNamespaceAndPath("example", "dust"));
        require(merged.added() == 1 && added != null && added.rules().isZero(),
                "a newly registered type must be added with defaults");
        require(merged.entries().size() == 3, "merging must keep existing file entries");

        WeatherBonusConfig.MergeResult frozen = WeatherBonusConfig.merge(entries, registryTypes, false);
        require(frozen.added() == 0
                        && frozen.entries().size() == entries.size()
                        && frozen.entries().get(ResourceLocation.fromNamespaceAndPath("example", "dust")) == null,
                "auto_add_new_types = false must leave the file untouched");
    }

    private static FieldSample sampleAt(FieldSample base, float y, int skyDarken, boolean skyVisible,
                                        boolean requireSky, int startY, int transitionStartY,
                                        float thin, float perBlock, float aqua, float ion) {
        return StratosphereParticleModifier.apply(base, y, startY, transitionStartY, skyDarken,
                skyVisible, requireSky, thin, perBlock, aqua, ion);
    }

    private static boolean sameSample(FieldSample left, FieldSample right) {
        return left.thermion() == right.thermion() && left.cryon() == right.cryon()
                && left.voltixPos() == right.voltixPos() && left.voltixNeg() == right.voltixNeg()
                && left.aquantum() == right.aquantum() && left.aetheron() == right.aetheron();
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
