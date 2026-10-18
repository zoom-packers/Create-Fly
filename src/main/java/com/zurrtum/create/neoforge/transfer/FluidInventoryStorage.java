package com.zurrtum.create.neoforge.transfer;

import com.google.common.collect.MapMaker;
import com.zurrtum.create.AllDataComponents;
import com.zurrtum.create.infrastructure.fluids.FluidInventory;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * Exposes a Create {@link FluidInventory} as a NeoForge {@code ResourceHandler<FluidResource>}.
 * <p>
 * Create's fluid amounts are in droplets (81000 per bucket), NeoForge's are in millibuckets (1000 per bucket).
 */
public interface FluidInventoryStorage extends ResourceHandler<FluidResource> {
    int DROPLETS_PER_MB = 81;
    Map<FluidResource, FluidStack> CACHE = new MapMaker().weakKeys().makeMap();

    static FluidStack getCachedStack(FluidResource resource) {
        return CACHE.computeIfAbsent(resource, FluidInventoryStorage::toStack);
    }

    static FluidStack toStack(FluidResource resource) {
        return new FluidStack(resource.getFluid(), 1, resource.getComponentsPatch());
    }

    static FluidStack toStack(FluidResource resource, int droplets) {
        if (resource.isEmpty() || droplets <= 0) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(resource.getFluid(), droplets, resource.getComponentsPatch());
    }

    static FluidResource toResource(FluidStack stack) {
        if (stack.isEmpty()) {
            return FluidResource.EMPTY;
        }
        DataComponentPatch patch = stack.getComponentChanges();
        if (patch.isPatched(AllDataComponents.FLUID_MAX_CAPACITY)) {
            patch = patch.forget(type -> type == AllDataComponents.FLUID_MAX_CAPACITY);
        }
        return FluidResource.of(stack.getFluid(), patch);
    }

    static int toDroplets(long mb) {
        return (int) Math.min(mb * DROPLETS_PER_MB, Integer.MAX_VALUE);
    }

    static int toMbFloor(int droplets) {
        return droplets <= 0 ? 0 : droplets / DROPLETS_PER_MB;
    }

    static int toMbCeil(int droplets) {
        return droplets <= 0 ? 0 : (droplets + DROPLETS_PER_MB - 1) / DROPLETS_PER_MB;
    }

    static FluidInventoryStorage of(FluidInventory inventory) {
        Objects.requireNonNull(inventory, "Null fluid inventory is not supported.");
        return FluidInventoryStorageImpl.of(inventory);
    }

    static FluidInventoryStorage of(FluidInventory inventory, @Nullable Direction direction) {
        Objects.requireNonNull(inventory, "Null fluid inventory is not supported.");
        return FluidInventoryStorageImpl.of(inventory, direction);
    }

    static boolean matches(FluidResource resource, FluidStack stack) {
        if (resource.isEmpty() || !stack.isOf(resource.getFluid())) {
            return false;
        }
        return FluidStack.areFluidsAndComponentsEqualIgnoreCapacity(stack, getCachedStack(resource));
    }

    static String toString(@Nullable FluidInventory inventory) {
        if (inventory == null) {
            return "~~NULL~~";
        }
        String result = inventory.toString();
        if (inventory instanceof BlockEntity blockEntity) {
            result += " (%s, %s)".formatted(blockEntity.getBlockState(), blockEntity.getBlockPos());
        }
        return result;
    }
}
