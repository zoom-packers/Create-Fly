package com.zurrtum.create.mixin;

import com.mojang.serialization.Lifecycle;
import com.zurrtum.create.content.kinetics.mixer.PotionRecipe;
import com.zurrtum.create.content.processing.sequenced.SequencedAssemblyRecipe;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryLoadTask;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// 26.3: recipes are a reloadable registry, so Create's generated recipes (sequenced assembly steps, potion mixing)
// are registered into it before it freezes, instead of into RecipeManager#prepare's map
@Mixin(RegistryLoadTask.class)
public class RegistryLoadTaskMixin<T> {
    @Unique
    private static final RegistrationInfo GENERATED = new RegistrationInfo(Optional.empty(), Lifecycle.experimental());

    @Shadow
    @Final
    WritableRegistry<T> registry;

    @Inject(method = "freezeRegistry(Ljava/util/Map;)Z", at = @At("HEAD"))
    @SuppressWarnings("unchecked")
    private void addGeneratedRecipes(Map<ResourceKey<?>, Exception> loadingErrors, CallbackInfoReturnable<Boolean> cir) {
        if (!registry.key().equals(Registries.RECIPE)) {
            return;
        }
        WritableRegistry<Recipe<?>> recipes = (WritableRegistry<Recipe<?>>) (Object) registry;
        Map<Identifier, Recipe<?>> map = new HashMap<>();
        recipes.entrySet().forEach(entry -> map.put(entry.getKey().identifier(), entry.getValue()));
        int loaded = map.size();
        map.putAll(SequencedAssemblyRecipe.GENERATE_RECIPES);
        SequencedAssemblyRecipe.GENERATE_RECIPES.clear();
        PotionRecipe.register(map);
        if (map.size() == loaded) {
            return;
        }
        map.forEach((id, recipe) -> {
            ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
            if (!recipes.containsKey(key)) {
                recipes.register(key, recipe, GENERATED);
            }
        });
    }
}
