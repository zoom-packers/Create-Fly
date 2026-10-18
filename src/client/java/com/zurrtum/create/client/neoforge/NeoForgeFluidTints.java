package com.zurrtum.create.client.neoforge;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

/**
 * Replaces Fabric's {@code FluidVariantRendering.getColor}: fluids from other NeoForge mods tint through NeoForge's
 * {@link FluidTintSource}, which can see the fluid's data components.
 */
public final class NeoForgeFluidTints {
    private NeoForgeFluidTints() {
    }

    /**
     * @return the tint, or null when the fluid has no NeoForge tint source and the vanilla path should be used
     */
    public static @Nullable Integer getColor(
        FluidModel model,
        Fluid fluid,
        DataComponentPatch components,
        @Nullable BlockAndTintGetter level,
        @Nullable BlockPos pos
    ) {
        if (!(model.tintSource() instanceof FluidTintSource tint)) {
            return null;
        }
        if (level != null && pos != null && components.isEmpty()) {
            return tint.colorInWorld(fluid.defaultFluidState(), fluid.defaultFluidState().createLegacyBlock(), level, pos);
        }
        return tint.colorAsStack(new FluidStack(fluid, 1, components));
    }
}
