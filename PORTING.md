# Porting notes: Create Fly 26.2 (Fabric) → NeoForge 26.3

This branch, `neoforge-26.3`, is a Zoompack 5 fork of [ZurrTum/Create-Fly](https://github.com/ZurrTum/Create-Fly). It
starts from upstream branch `26.2`, commit `9df70162`. Upstream is the git remote `upstream`.

Status, newest first, is in [Status](#status). Everything below it describes the port as it stands.

## How the port is organised

The goal is a small, mechanical diff against upstream, so that rebasing onto new upstream commits stays manageable.

- **New code lives in new packages.**
  - `com.zurrtum.create.neoforge` (main) and `com.zurrtum.create.client.neoforge` (client) hold the NeoForge entrypoints,
    the `Platform` shim that replaces Fabric Loader, the fluid types, and the transfer bridge (`neoforge/transfer`).
  - Helpers for 26.3 vanilla changes sit next to the code that uses them (`foundation/utility/FuelHelper`,
    `foundation/utility/BlockTransformHelper`).
- **Upstream files are edited in place only where they have to be.** Edits carry a short `// NeoForge:` or `// 26.3:`
  comment. Larger rewrites replace a file wholesale (`AllTransfer`, `ItemInHandRendererMixin`).
- **Fabric-only upstream files are excluded in `build.gradle`, not deleted.** Rebases therefore don't conflict on them.
  The excluded files are:
  - the REI, RRV and EIV recipe viewer plugins
  - the Trinkets compat
  - Fabric's transfer and FRAPI bridges
  - mixins replaced by NeoForge hooks
  - `data/fabric`, `data/trinkets` and `assets/fabric`
- **Generated files come from scripts in `tools/`.** Re-run them after a rebase:

| Script | Produces |
|---|---|
| `tools/classtweaker2at.py src/main/resources/create.classtweaker ../zp5-core/build/moddev/artifacts/minecraft-patched-26.3.0.89-merged.jar` | `META-INF/accesstransformer.cfg` and `interfaces.json` from upstream's classtweaker. Adds the overrides NeoForge's recompile needs, merges `tools/accesstransformer-neoforge.cfg` and applies the 26.3 class moves. Pass a patched MC jar *without* this mod's ATs. |
| `tools/remap_26_3_imports.py <26.2 merged jar> <26.3 merged jar> src/client/java src/main/java` | Rewrites references to classes 26.3 moved, mostly `com.mojang.blaze3d` → `com.mojang.renderpearl`, in imports, code and mixin descriptors. Writes `tools/class-moves-26.3.txt`. |
| `tools/worldgen_26_3.py` | Converts upstream's `configured_feature` JSON to 26.3's `worldgen/feature` format. |
| `tools/data_26_3.py` | Converts upstream's 26.2 data JSON (loot tables, enchantments, …) to 26.3. See the section below. |
| `tools/check_mixins.py <MC jar> <NeoForge universal jar> build/classes/java/main build/classes/java/client` | Static mixin check: target classes, `method=` strings, INVOKE/FIELD/NEW `@At` targets in the target bytecode, shadows, accessors and invokers. |

## Build

- **Toolchain:** ModDevGradle 2.0.148, NeoForge 26.3.0.89, Java 25, Gradle 9.2.1. This matches ZP5's
  `mods/build-logic/src/main/groovy/zp5.neoforge-mod.gradle`.
- **Source sets:** upstream's split `main` / `client` source sets are kept. Both go into one mod and one jar.
- **Mod metadata:** `neoforge.mods.toml` is generated from `src/main/templates`.
- **Interface injection:** done with MDG `interfaceInjectionData` (`interfaces.json`). NeoForge's `Container` already
  implements an interface named `ContainerExtension`, so the compile-time injected interface is an alias,
  `neoforge.CreateContainerExtension`. At runtime `ContainerMixin` adds Create's real `ContainerExtension`.
- **Sodium:** the NeoForge jar keeps the mod under `META-INF/jarjar`. `extractJarJarCompileOnly` puts the nested jar on
  the client compile classpath.
- **Run flags:**
  - `-PmixinDebug` turns on `mixin.debug.verbose` and export.
  - `-PmixinScan` adds `mixin.ignoreRequired` and `countInjections`.
  - `-PserverOnly` runs without the client source set.

## Loader and entrypoints

| Fabric | NeoForge |
|---|---|
| `main` entrypoint `Create#onInitialize` | `@Mod("create") neoforge.CreateNeoForge` calls it |
| `client` entrypoint | `@Mod(value = "create", dist = CLIENT) client.neoforge.CreateNeoForgeClient` |
| `create_plugin` entrypoint (`CreateRegisterPlugin`) | Java `ServiceLoader`. Addons list their plugin in `META-INF/services/com.zurrtum.create.api.registry.CreateRegisterPlugin` |
| `jei_mod_plugin` entrypoint | JEI's `@JeiPlugin` (already on `JeiClientPlugin`) |
| `FabricLoader#isModLoaded`, game/config dirs, mod metadata | `neoforge.Platform` (`LoadingModList`, `FMLPaths`) |
| `fabric.mod.json` custom value `vanillin:overrides` | `[modproperties.<modid>] "vanillin:overrides"` in `neoforge.mods.toml` |
| `ModContainer#getRootPaths` (built-in resource packs) | `Platform.getModRootPaths` |

Registration works as it does upstream without Fabric API. Create's content is registered during vanilla bootstrap by
`BlocksMixin`, `FluidsMixin` and `BuiltInRegistriesMixin`. NeoForge runs bootstrap before mod construction, so this
needs no RegisterEvent. `Create.Lazy` is always false.

Things Fabric Loader without Fabric API had to do by hand, which NeoForge already does:
- loading the mod's data and asset packs (`BuiltInPackSourceMixin` / `ClientPackSourceMixin` now only add the dynamic
  data pack and the optional packs)
- providing the `c:` convention tags

## Fabric API replacements

| Fabric API | NeoForge 26.3 |
|---|---|
| Transfer API (`Storage`, `ItemVariant`, `FluidVariant`, `BlockApiCache`) | `ResourceHandler` capabilities. See `neoforge/transfer` and the transfer notes below |
| FRAPI / model loading API | NeoForge's model API. `WrapperBlockStateModel` overrides the level-aware `BlockStateModel#collectParts(level, pos, state, random, parts)`, and NeoForge loads `neoforge:obj` models itself. Upstream's FRAPI-only mixins are excluded and the vanilla-path mixins are used |
| Custom ingredients (`fabric:components`) | `neoforge:components` (`DataComponentIngredient`, an `ICustomIngredient`). `IngredientMixin` and `ComponentsIngredient` are excluded |
| Recipe sync for recipe viewers | `OnDatapackSyncEvent#sendRecipes` for every `create:` recipe type (`CreateNeoForge#sendRecipes`) |
| `FakePlayer` | `net.neoforged.neoforge.common.util.FakePlayer` (`DeployerFabricFakePlayer`) |
| Networking `PacketContext` (`MenuProviderMixin`) | Not needed. Create's packets are appended to the vanilla game protocol (`GameProtocolsMixin`), which works the same on NeoForge |
| `RegistryEntryAddedCallback` (ComputerCraft display sources) | `CreateNeoForge.afterRegistration` (common setup) |
| `FluidVariantRendering` tint | NeoForge `FluidTintSource` (`client.neoforge.NeoForgeFluidTints`) |
| `ReloadListenerMixin` (`FabricResourceReloader`) | Excluded, not needed |

## NeoForge hooks that replace upstream mixins

NeoForge patches the vanilla calls that some of upstream's mixins targeted. For example, `Block#getFriction()` becomes
`IBlockExtension#getFriction(state, level, pos, entity)`. Create's "control block" interfaces now extend
`IBlockExtension` and implement the NeoForge hook as an interface default:

| Interface | NeoForge hook | Mixins removed |
|---|---|---|
| `SlipperinessControlBlock` | `getFriction` | `AbstractBoatMixin`, `ExperienceOrbMixin`, `ItemEntityMixin`, `LeashableMixin`, `LivingEntityMixin#getSlipperiness` |
| `SoundControlBlock` | `getSoundType` | `EntityMixin#getStepSound`, `LivingEntityMixin#getBlockFallSound`, `LevelEventHandlerMixin`, `MultiPlayerGameModeMixin#getHitSound` |
| `BouncinessControlBlock` | `getBounceRestitution` | `EntityMixin#getBlockBounciness` |
| `ResistanceControlBlock` | `getExplosionResistance` | `ExplosionDamageCalculatorMixin` |
| `WeakPowerControlBlock` | `shouldCheckWeakPower` | `SignalGetterMixin` |
| `ScaffoldingControlBlock` | `isScaffolding` | `LivingEntityMixin#isScaffolding` |
| `SwingControlItem` (`IItemExtension`) | `onEntitySwing` | `LivingEntityMixin#swingHand` |
| `CopycatBlock` | `getLightEmission` / `hasDynamicLightEmission` | `LightCoordsUtilMixin` |
| `SyncedBlockEntity` | `handleUpdateTag` / `onDataPacket(Connection, ValueInput)` | `LevelChunkMixin#handleUpdateTag`, `ClientPacketListenerMixin#onDataPacket` |
| Diving helmet | `LivingBreatheEvent` (`CreateNeoForge#breathe`) | `LivingEntityMixin#breatheInLava`, `#canBreatheInWater` |

Other mixins were retargeted to NeoForge's patched code:
- `ItemStackMixin` (server placement goes through `CommonHooks#onPlaceItemIntoWorld`)
- `ServerPlayerGameModeMixin` / `MultiPlayerGameModeMixin` (`removeBlock` / `onDestroyedByPlayer`)
- `BlockItemMixin` (level-aware place sounds)
- `StructureTemplateMixin` (`placeEntities` gained the settings parameter)
- `EntityFluidInteractionMixin` (FluidType-aware `update`)
- `ModelBlockRendererMixin` (NeoForge's lighter factory)

## Minecraft 26.2 → 26.3 changes handled

**Server / common**
- **Block codecs removed.** `codec()`, `simpleCodec` and `propertiesCodec` are gone; the CODEC fields and overrides were removed.
- **Renames.** `PushReaction` constants (`NORMAL` → `PUSH_PULL`, `BLOCK` → `IMMOVEABLE`, …), `RedStoneWireBlock` →
  `RedstoneWireBlock`, `DirtPathBlock` → `PathBlock`, `EnderMan` → `Enderman`, `hurtMarked` → `syncVelocity`.
- **New parameters.** `Prediction` on `drop` / `placeItemBackInInventory`. `SwingAnimation` on `swing`, and there is no
  client swing packet. `BonemealSource`. `SignTextSlot` instead of a front/back boolean.
- **`BlockItem#updateCustomBlockEntityTag` is static.** Post-placement logic moved to NeoForge's
  `BlockItem#onBlockPlacedBy`. Stack fix-ups (tank, vault, factory panel) moved to `placeBlock`.
- **Fuel.** `FuelValues` is gone, replaced by the `cooking_fuel` component with data-driven burn times. Create's fuels are
  added in `ModifyDefaultComponentsEvent` (`CreateNeoForge#addFuels`) and read with `FuelHelper`.
- **Brewing.** `PotionBrewing` is gone and brewing is data-driven (`BrewingRecipe`). Mixer potion recipes are now built
  from the loaded brewing recipes (`PotionRecipe#register`).
- **Recipes are a reloadable registry.** `RecipeManager#prepare` no longer exists. Generated recipes (sequenced-assembly
  steps, potion mixing) are added in `RegistryLoadTaskMixin` just before the recipe registry freezes. `GENERATE_RECIPES`
  is now a concurrent map, because recipe decoding runs on worker threads.
- **Worldgen.** `ConfiguredFeature` is merged into `Feature` (an interface holding its own settings); feature types and
  placement modifier types are now codec registries; block states in JSON are `{"id", "properties"}` or a bare id
  (`tools/worldgen_26_3.py`).
- **Clocks.** `ClockInstance` is now an interface; `ServerClockInstance#timeMarkers` is reached through an AT.
- **Smaller changes.**
  - Axe stripping is now the data-driven `minecraft:axe` block transformer (`BlockTransformHelper`).
  - `ShovelItem` is replaced by the `shovel_douse` item ability.
  - The `compostable` component replaces `COMPOSTABLES`.
  - `MapDecorationType` lost two fields.
  - `RegistryOps#owner` became `getter`.
  - `Recipe.CODEC` is a holder codec; use `DIRECT_CODEC` for inline recipes.
  - `DataComponentPatch` stores raw values instead of `Optional`.
- **Data formats.** Loot tables and the conditions/functions used in enchantments changed format (`tools/data_26_3.py`).

**Client**
- **Rendering library.** Most of `com.mojang.blaze3d`'s GPU API moved to `com.mojang.renderpearl`; this was remapped
  mechanically. 26.3 also has a Vulkan backend. Flywheel's instancing and indirect backends need OpenGL, so `GlCompat`
  falls back to Flywheel's `off` backend on Vulkan.
- **Input.** GLFW was replaced by SDL3. Key constants are now `InputConstants` SDL scancodes; cursor calls use LWJGL `SDLMouse`.
- **First-person rendering.** `ItemInHandRenderer` became `FirstPersonHandsAndItemsRenderer`, which works from render
  states. The zapper, potato cannon and extendo-grip handlers now take `PlayerRenderState` /
  `FirstPersonHandsAndItemsRenderState`.
- **Glint.** Glint is part of the render type instead of a second pass (`armorCutoutNoCullGlint`, `itemCutoutGlint`).
- **Model quads.** `MaterialInfo#shade` became a nullable `shadeDirectionOverride`. `ItemQuads` replaces quad lists.
  `VertexConsumer#setUv3` was added.
- **Feature output target.** `RenderSystem.outputColorTextureOverride` is gone. Ponder and GUI scenes open their own
  render pass and call `FeatureRenderDispatcher.renderAllFeatures(pass, frame)`.
- **Entity renderers.** `shouldRender` / `getBoundingBoxForCulling` take the partial tick. Fog colours are vectors.

## Transfer (items and fluids)

Create's block entities expose `Capabilities.Item.BLOCK` and `Capabilities.Fluid.BLOCK` for the same block entity types
as upstream's `AllTransfer.register()`. Other mods' handlers are wrapped as Create `Container` / `FluidInventory`
(`neoforge/transfer/ResourceHandler*Wrapper`).

**Fluid units.** Create Fly counts fluid in droplets (81000 per bucket) and NeoForge counts millibuckets, so the factor is 81.
- When Create inserts into a NeoForge handler, the amount rounds down to whole mB. When it extracts, it rounds up and
  reports at most the droplets requested. Up to 80 droplets can be voided per operation; fluid is never created.
- Other mods see `floor(droplets / 81)`.
- JEI shows mB.

**Behaviour differences.**
- `BlockCapabilityCache` keeps a result until something invalidates it.
- Only the registered Create block entity types are visible to other mods. Fabric API used to expose every `Container`.

**Upstream bugs fixed while porting.**
- The slotted wrapper's capacity was always 0.
- `setItem` inserted and extracted the wrong way round.
- `preciseExtract` kept using a transaction it had already aborted.

## Disabled, approximated or not ported

- **Recipe viewers.** REI, RRV and EIV plugins are not ported; JEI is the only one.
  `TODO(neoforge 26.3)`: EMI / SEMI (see `client/compat/CompatMod`).
- **Trinkets goggles.** Not ported. `TODO`: Curios support.
- **Potion fluid tint for other mods.** Fabric's `FluidVariantRenderHandler` is not ported. Create's own renderers tint correctly.
- **NeoForge enhanced block lighting** is replaced by Create's lighter in `ModelBlockRendererMixin`, as upstream does with vanilla's.
- **Shade overrides.** Model faces whose shade override is not `up` are treated as unshaded.
- **Flywheel and glint.** Flywheel item models drop the enchantment glint overlay (`ModelUtil` TODO).
- **Flywheel OIT** always composites into the main render target.
- **Ponder translucent shading.** Ponder's translucent entity-block types use vanilla OIT pipelines with improved
  transparency (no Ponder shading). `TODO` in `PonderRenderPipelines`.
- **In-world outlines.** Catnip's in-world outlines use outline render types, which 26.3 only draws into the glow target.
  They may look different; not yet checked in game.
- **`StencilElement`** still makes raw GL11 calls, so it does nothing on Vulkan.
- **Item-ability shovels.** Only items that advertise `shovel_douse` extinguish lit blaze burners.

## Tracking upstream

1. `git fetch upstream` and rebase `neoforge-26.3` onto `upstream/26.2`, or onto `upstream/26.3` once upstream has one.
   When upstream moves to 26.3 itself, most of the "26.2 → 26.3" edits above will conflict with upstream's own versions.
   Prefer upstream's code there, then re-apply only the NeoForge-specific parts.
2. Conflicts should be limited to edited lines marked `// NeoForge:` or `// 26.3:`, plus `build.gradle`, `AllTransfer`
   and the mixin JSON files.
3. Re-run the `tools/` scripts. New upstream classtweaker entries, moved classes and data files are picked up
   automatically.
4. `./gradlew build`, then `tools/check_mixins.py`, then `runGameTestServer`.
5. Upstream files that are excluded but changed need a manual look in their NeoForge replacement:
   - `infrastructure/transfer/*` → `neoforge/transfer/*`
   - `Cached*Behaviour`
   - FRAPI mixins → `WrapperBlockStateModel`
   - `compat/fabric/*`

## Addons

Create Fly addons (Nerfad, Fluid Burner, …) use the `com.zurrtum.create` API. They port to this fork the same way:
- Fabric Loader calls → `com.zurrtum.create.neoforge.Platform`
- the `create_plugin` entrypoint → a `META-INF/services` entry
- Fabric transfer → NeoForge capabilities

ZP4's NeoForge Create addons target `com.simibubi.create` and won't work without a source port.

## Status

Verified on 2026-10-18 (NeoForge 26.3.0.89, Java 25.0.1, Windows):

- **Build.** `./gradlew build` builds `build/libs/create-fly-neoforge-6.0.9+neoforge-26.3.jar` (about 20 MB) and a
  sources jar.
- **Static mixin check.** `tools/check_mixins.py` reports 0 problems for every mixin in both configs, checked against
  the patched 26.3 jar, NeoForge, Sodium 0.9.3 and Iris 1.11.7.
- **Game-test server.** `./gradlew runGameTestServer` loads Create's registries and data, creates the test world, and
  passes all tests. The real test is `create:water_wheel_drives_press` (`neoforge/CreateGameTests`): a small water wheel
  under a water channel turns at 8 RPM and drives a mechanical press through a shaft. The press turns an iron ingot on
  a depot into an iron sheet, at tick 134.
- **Runtime mixins.** With `-PmixinDebug` (`mixin.debug.verbose`), 60 Create mixins applied on the dedicated server with
  no warnings or failures. The other ten are not loaded by a game-test server:
  - `ConfigurationProtocolsMixin`, `ServerConfigurationPacketListenerImplMixin` and `GameProtocolsMixin` (networking)
  - `ExperimentalRedstoneWireEvaluatorMixin`, `PersistentEntitySectionManagerCallbackMixin`, `RailStateMixin`,
    `StartAttackingMixin`, `SynchedEntityDataBuilderMixin` and `TooltipContextMixin`
  - `ArchitecturyMixin`, which is disabled upstream

  The client config can only be exercised by `runClient`.
- **Client.** `runClient` has not been run yet; the user asked to hold off. Still to check on the client:
  - the title screen (client mixins apply, Flywheel and Ponder reload listeners)
  - a world where a water wheel drives a mechanical press
  - the `TODO(neoforge 26.3)` rendering items listed above

Upstream data problems that 26.3 makes fatal: advancements that pointed at recipes that don't exist
(`crafting///tree_fertilizer`, `crafting/materials/copper_ingot`, `crafting/materials/copper_nugget`) were removed.
26.3 resolves recipe references as registry holders. `tools/check_recipe_refs.py` finds any new ones.
