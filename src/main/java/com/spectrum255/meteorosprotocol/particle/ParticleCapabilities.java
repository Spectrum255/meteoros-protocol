package com.spectrum255.meteorosprotocol.particle;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.minecraft.resources.ResourceLocation;

public final class ParticleCapabilities {
    private ParticleCapabilities() {}
    public static final BlockCapability<PhaseStorage, Void> PHASE_STORAGE = BlockCapability.createVoid(ResourceLocation.fromNamespaceAndPath(MeteorosProtocol.MODID,"phase_storage"), PhaseStorage.class);
    /**
     * Sided mixed-storage capability. The context is the face being queried, so
     * the intake tower can expose its extract-only output port on one side only.
     * Machines without a sided restriction ignore the context and return
     * themselves on every face.
     */
    public static final BlockCapability<MixedStorage, Direction> MIXED_STORAGE = BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath(MeteorosProtocol.MODID,"mixed_storage"), MixedStorage.class);
}
