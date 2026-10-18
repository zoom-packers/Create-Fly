package com.zurrtum.create.client.compat.jei;

import com.zurrtum.create.infrastructure.fluids.FluidStack;

// NeoForge: JEI uses NeoForge FluidStacks counted in millibuckets, Create counts droplets (81 per millibucket)
public final class JeiFluids {
    public static final int DROPLETS_PER_MILLIBUCKET = 81;

    private JeiFluids() {
    }

    public static long toJeiAmount(long droplets) {
        if (droplets <= 0) {
            return 0;
        }
        return Math.max(1, droplets / DROPLETS_PER_MILLIBUCKET);
    }

    public static int fromJeiAmount(long millibuckets) {
        return (int) Math.min(Integer.MAX_VALUE, millibuckets * DROPLETS_PER_MILLIBUCKET);
    }

    public static net.neoforged.neoforge.fluids.FluidStack toJei(FluidStack stack) {
        if (stack.isEmpty()) {
            return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        }
        return new net.neoforged.neoforge.fluids.FluidStack(
            stack.getFluid(),
            (int) toJeiAmount(stack.getAmount()),
            stack.getComponentChanges()
        );
    }

    public static FluidStack fromJei(net.neoforged.neoforge.fluids.FluidStack stack) {
        if (stack.isEmpty()) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(stack.getFluid(), fromJeiAmount(stack.getAmount()), stack.getComponentsPatch());
    }
}
