package com.zurrtum.create.foundation.utility;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.loot.NeoForgeLootContextParams;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * 26.3 replaced {@code FuelValues} with the {@link DataComponents#COOKING_FUEL} item component, whose burn time can be
 * a data-driven context provider. Create's fuel consumers are not furnaces, so they resolve it with whatever context
 * they have.
 */
public final class FuelHelper {
    private static final ContextKeySet PARAMS = new ContextKeySet.Builder().optional(LootContextParams.ORIGIN)
        .optional(LootContextParams.BLOCK_STATE).optional(NeoForgeLootContextParams.QUERIED_STACK).build();

    private FuelHelper() {
    }

    public static boolean isFuel(ItemStack stack) {
        return stack.has(DataComponents.COOKING_FUEL);
    }

    public static int burnDuration(Level level, @Nullable BlockPos pos, ItemStack stack) {
        CookingFuel fuel = stack.get(DataComponents.COOKING_FUEL);
        if (fuel == null) {
            return 0;
        }
        if (fuel.burnTime() instanceof ResolvableInt.Constant(int value)) {
            return value;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        LootParams.Builder params = new LootParams.Builder(serverLevel).withOptionalParameter(
            NeoForgeLootContextParams.QUERIED_STACK,
            stack
        );
        if (pos != null) {
            params.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.BLOCK_STATE, level.getBlockState(pos));
        }
        return fuel.burnTime().get(new LootContext.Builder(params.create(PARAMS)).create(Optional.empty()), 0);
    }
}
