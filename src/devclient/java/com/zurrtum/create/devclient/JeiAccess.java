package com.zurrtum.create.devclient;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Dev-only JEI plugin for the client smoke test: keeps the runtime so the test can open JEI's recipe screen. Only
 * touched when JEI is loaded (-PwithJei).
 */
@JeiPlugin
public final class JeiAccess implements IModPlugin {
    private static @Nullable IJeiRuntime runtime;

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath("create_devclient", "smoke");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    static boolean hasRuntime() {
        return runtime != null;
    }

    static void showRecipesFor(ItemStack output) {
        if (runtime != null) {
            runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, output));
        }
    }
}
