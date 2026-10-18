package com.zurrtum.create.client.compat.jei;

import com.zurrtum.create.AllDataComponents;
import com.zurrtum.create.infrastructure.component.BottleType;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class PotionFluidSubtypeInterpreter implements ISubtypeInterpreter<FluidStack> {
    public static final PotionFluidSubtypeInterpreter INSTANCE = new PotionFluidSubtypeInterpreter();

    @Override
    public @Nullable Object getSubtypeData(FluidStack ingredient, UidContext context) {
        if (ingredient.isComponentsPatchEmpty()) {
            return null;
        }
        DataComponentMap components = ingredient.getComponents();
        return List.of(
            components.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY),
            components.getOrDefault(AllDataComponents.POTION_FLUID_BOTTLE_TYPE, BottleType.REGULAR)
        );
    }
}
