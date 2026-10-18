package com.zurrtum.create.neoforge;

import net.minecraft.resources.Identifier;
import com.zurrtum.create.infrastructure.config.AllConfigs;
import com.zurrtum.create.content.kinetics.belt.BeltHelper;
import com.zurrtum.create.foundation.recipe.trie.RecipeTrieFinder;
import com.zurrtum.create.foundation.recipe.RecipeFinder;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import com.zurrtum.create.content.equipment.armor.DivingHelmetItem;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;
import com.zurrtum.create.content.trains.entity.CarriageEntityHandler;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import java.util.function.Consumer;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProviders;
import net.minecraft.world.level.storage.loot.providers.number.floats.ResolvableFloat;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.core.component.DataComponents;
import com.zurrtum.create.AllFuelTimes;
import com.zurrtum.create.Create;
import com.zurrtum.create.neoforge.transfer.NeoForgeTransfer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * NeoForge entrypoint, replacing the Fabric {@code main} entrypoint ({@link Create#onInitialize()}).
 * <p>
 * Create's registry content is not registered here: as on Fabric without Fabric API, it is registered during vanilla
 * bootstrap by {@code BlocksMixin}, {@code FluidsMixin} and {@code BuiltInRegistriesMixin}, which NeoForge runs before
 * loading mods. This class wires up what Fabric API or Fabric Loader did for upstream.
 */
@Mod(Create.MOD_ID)
public class CreateNeoForge {
    private static final List<Runnable> AFTER_REGISTRATION = new ArrayList<>();
    private static final List<Consumer<RegisterCapabilitiesEvent>> CAPABILITIES = new ArrayList<>();

    public CreateNeoForge(IEventBus modBus, ModContainer container) {
        new Create().onInitialize();
        CreateFluidTypes.register(modBus);
        CreateGameTests.register(modBus);
        modBus.addListener(NeoForgeTransfer::registerCapabilities);
        modBus.addListener(RegisterCapabilitiesEvent.class, event -> CAPABILITIES.forEach(task -> task.accept(event)));
        modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(() -> {
            AFTER_REGISTRATION.forEach(Runnable::run);
            AFTER_REGISTRATION.clear();
        }));
        modBus.addListener(CreateNeoForge::addFuels);
        NeoForge.EVENT_BUS.addListener(CreateNeoForge::sendRecipes);
        NeoForge.EVENT_BUS.addListener(CreateNeoForge::breathe);
        NeoForge.EVENT_BUS.addListener(CreateNeoForge::addReloadListeners);
        NeoForge.EVENT_BUS.addListener(CreateNeoForge::enterSection);
    }

    /**
     * NeoForge fires EnteringSection where upstream's PersistentEntitySectionManagerCallbackMixin injected. That mixin
     * used an implicit {@code @Local long}, which is ambiguous in NeoForge's patched {@code onMove} (it already has
     * {@code newSectionPos} and {@code oldSectionKey}) and crashed the server the first time an entity changed section.
     * NeoForge also fires the event from the client's TransientEntitySectionManager, which upstream's mixin never
     * targeted, and the handler casts to ServerLevel, so client levels are skipped.
     */
    private static void enterSection(EntityEvent.EnteringSection event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        CarriageEntityHandler.onEntityEnterSection(event.getEntity(), event.getPackedOldPos(), event.getPackedNewPos());
    }

    /**
     * NeoForge replaces vanilla's air supply code with LivingBreatheEvent, where upstream's LivingEntityMixin hooked
     * the diving helmet in.
     */
    private static void breathe(LivingBreatheEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getAbilities().invulnerable) {
            return;
        }
        ServerLevel level = player.level();
        if (player.isEyeInFluid(FluidTags.WATER)) {
            if (!event.canBreathe() && DivingHelmetItem.breatheUnderwater(player, level)) {
                event.setCanBreathe(true);
            }
        } else if (player.isInLava()) {
            DivingHelmetItem.breatheInLava(player, level);
        }
    }

    /**
     * 26.3 replaced {@code FuelValues} (which upstream's FuelValuesMixin added to) with the cooking_fuel component.
     */
    private static void addFuels(ModifyDefaultComponentsEvent event) {
        AllFuelTimes.ALL.forEach((item, burnTime) -> event.modify(
            item, (components, context, target) -> components.set(
                DataComponents.COOKING_FUEL,
                new CookingFuel(
                    new ResolvableInt.Constant(burnTime),
                    ResolvableFloat.fromKey(ContextFloatProviders.COOKING_DEFAULT_SPEED_MULTIPLIER)
                )
            )
        ));
    }

    /**
     * Registers capabilities from code that runs during vanilla bootstrap, before the event fires.
     */
    public static void onRegisterCapabilities(Consumer<RegisterCapabilitiesEvent> task) {
        CAPABILITIES.add(task);
    }

    /**
     * Runs a task once every mod has registered its content, for code that upstream ran from Fabric API's
     * {@code RegistryEntryAddedCallback}.
     */
    public static void afterRegistration(Runnable task) {
        AFTER_REGISTRATION.add(task);
    }

    /**
     * NeoForge rejects server reload listeners that upstream's ReloadableServerResourcesMixin appended.
     */
    private static void addReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(Create.MOD_ID, "recipe_finder"), RecipeFinder.LISTENER);
        event.addListener(Identifier.fromNamespaceAndPath(Create.MOD_ID, "recipe_trie_finder"), RecipeTrieFinder.LISTENER);
        event.addListener(Identifier.fromNamespaceAndPath(Create.MOD_ID, "belt_helper"), BeltHelper.LISTENER);
        event.addListener(Identifier.fromNamespaceAndPath(Create.MOD_ID, "configs"), AllConfigs.LISTENER);
    }

    /**
     * Vanilla 26.x no longer sends recipes to clients. Upstream asked Fabric API to sync Create's recipe serializers
     * for recipe viewers; NeoForge syncs by recipe type.
     */
    private static void sendRecipes(OnDatapackSyncEvent event) {
        BuiltInRegistries.RECIPE_TYPE.entrySet().forEach(entry -> {
            if (entry.getKey().identifier().getNamespace().equals(Create.MOD_ID)) {
                event.sendRecipes(entry.getValue());
            }
        });
    }
}
