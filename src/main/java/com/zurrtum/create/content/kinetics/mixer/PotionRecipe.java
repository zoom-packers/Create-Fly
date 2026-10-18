package com.zurrtum.create.content.kinetics.mixer;

import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.predicates.PotionsPredicate;
import net.minecraft.world.item.crafting.PotionIngredient;
import net.minecraft.world.item.crafting.BrewingRecipe;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zurrtum.create.AllRecipeSerializers;
import com.zurrtum.create.AllRecipeTypes;
import com.zurrtum.create.content.fluids.potion.PotionFluidHandler;
import com.zurrtum.create.content.processing.basin.BasinInput;
import com.zurrtum.create.content.processing.basin.BasinRecipe;
import com.zurrtum.create.content.processing.recipe.HeatCondition;
import com.zurrtum.create.content.processing.recipe.SizedIngredient;
import com.zurrtum.create.foundation.blockEntity.behaviour.filtering.ServerFilteringBehaviour;
import com.zurrtum.create.foundation.fluid.FluidIngredient;
import com.zurrtum.create.infrastructure.component.BottleType;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.*;

import static com.zurrtum.create.Create.MOD_ID;

public record PotionRecipe(FluidStack result, FluidIngredient fluidIngredient,
                           Ingredient ingredient) implements BasinRecipe {
    public static final MapCodec<PotionRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec((RecordCodecBuilder.Instance<PotionRecipe> instance) -> instance.group(
        FluidStack.CODEC.fieldOf("result").forGetter(PotionRecipe::result),
        FluidIngredient.CODEC.fieldOf("fluid_ingredient").forGetter(PotionRecipe::fluidIngredient),
        Ingredient.CODEC.fieldOf("ingredient").forGetter(PotionRecipe::ingredient)
    ).apply(instance, PotionRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, PotionRecipe> STREAM_CODEC = StreamCodec.composite(
        FluidStack.PACKET_CODEC,
        PotionRecipe::result,
        FluidIngredient.PACKET_CODEC,
        PotionRecipe::fluidIngredient,
        Ingredient.CONTENTS_STREAM_CODEC,
        PotionRecipe::ingredient,
        PotionRecipe::new
    );
    public static final RecipeSerializer<PotionRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);
    public static @Nullable ReloadData data;

    public static void register(Map<Identifier, Recipe<?>> map) {
        if (data == null) {
            return;
        }
        // 26.3: brewing is data driven. Every brewing recipe names one container and one input potion, so the
        // mixing recipes are derived from the loaded brewing recipes instead of the removed PotionBrewing registry.
        int recipeIndex = 0;
        Map<Identifier, Recipe<?>> mixes = new LinkedHashMap<>();
        for (Recipe<?> recipe : map.values()) {
            if (!(recipe instanceof BrewingRecipe brewing)) {
                continue;
            }
            ItemStack output = brewing.getOutput().create();
            PotionContents outputContents = output.get(DataComponents.POTION_CONTENTS);
            if (outputContents == null || !isBottle(output.getItem())) {
                continue;
            }
            PotionIngredient input = brewing.getInput();
            List<Holder<Potion>> inputPotions = input.potions().flatMap(PotionsPredicate::potions)
                .map(set -> set.stream().toList()).orElse(List.of());
            FluidStack toFluid = PotionFluidHandler.getFluidFromPotion(
                outputContents,
                PotionFluidHandler.bottleTypeFromItem(output.getItem()),
                81000
            );
            Ingredient reagent = brewing.getReagent().ingredient();
            for (Holder<Item> container : input.ingredient().items().toList()) {
                if (!isBottle(container.value())) {
                    continue;
                }
                BottleType fromBottleType = PotionFluidHandler.bottleTypeFromItem(container.value());
                for (Holder<Potion> potion : inputPotions) {
                    FluidIngredient fromFluid = PotionFluidHandler.getFluidIngredientFromPotion(
                        new PotionContents(potion),
                        fromBottleType,
                        81000
                    );
                    Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, "potion_mixing_vanilla_" + recipeIndex++);
                    mixes.put(id, new PotionRecipe(toFluid, fromFluid, reagent));
                }
            }
        }
        map.putAll(mixes);
        data = null;
    }

    private static boolean isBottle(Item item) {
        return item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION;
    }

    @Override
    public int getIngredientSize() {
        return 2;
    }

    @Override
    public List<SizedIngredient> ingredients() {
        return List.of(new SizedIngredient(ingredient, 1));
    }

    @Override
    public List<FluidIngredient> fluidIngredients() {
        return List.of(fluidIngredient);
    }

    @Override
    public HeatCondition heat() {
        return HeatCondition.HEATED;
    }

    @Override
    public boolean matches(BasinInput input, Level world) {
        if (!HeatCondition.HEATED.testBlazeBurner(input.heat())) {
            return false;
        }
        ServerFilteringBehaviour filter = input.filter();
        if (filter == null) {
            return false;
        }
        if (!filter.test(result)) {
            return false;
        }
        List<ItemStack> outputs = BasinRecipe.tryCraft(input, ingredient);
        if (outputs == null) {
            return false;
        }
        if (!BasinRecipe.matchFluidIngredient(input, fluidIngredient)) {
            return false;
        }
        return input.acceptOutputs(outputs, List.of(result), true);
    }

    @Override
    public boolean apply(BasinInput input) {
        if (!HeatCondition.HEATED.testBlazeBurner(input.heat())) {
            return false;
        }
        Deque<Runnable> changes = new ArrayDeque<>();
        List<ItemStack> outputs = BasinRecipe.prepareCraft(input, ingredient, changes);
        if (outputs == null) {
            return false;
        }
        if (!BasinRecipe.prepareFluidCraft(input, fluidIngredient, changes)) {
            return false;
        }
        List<FluidStack> fluids = List.of(result);
        if (!input.acceptOutputs(outputs, fluids, true)) {
            return false;
        }
        changes.forEach(Runnable::run);
        return input.acceptOutputs(outputs, fluids, false);
    }

    @Override
    public RecipeSerializer<PotionRecipe> getSerializer() {
        return AllRecipeSerializers.POTION;
    }

    @Override
    public RecipeType<PotionRecipe> getType() {
        return AllRecipeTypes.POTION;
    }

    public record ReloadData(HolderLookup.Provider registries, FeatureFlagSet enabledFeatures) {
    }
}