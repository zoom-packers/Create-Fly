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
  - `-PdisableMixins=A,B` skips the named client mixins (dev-only bisecting aid in the client `MixinPlugin`).
- **Client smoke test:** `./gradlew runClientSmoke` starts a client with the dev-only `src/devclient` helper mod
  (`create_devclient`, never in the jar). It screenshots the title screen, creates a flat world, runs the chosen
  scenarios, logs one `SMOKE RESULT <check> PASS|FAIL` line per check plus a `SMOKE SUMMARY`, and quits by itself.
  Screenshots land in `run-smoke/screenshots`. It opens a game window, so leave the window alone while it runs.
  - `-PsmokeScenarios=press,contraption,oit,ponder,jei` (default `press`):
    - `press`: the `CreateGameTests` water wheel → shaft → press setup
    - `contraption`: a creative motor turning a mechanical bearing with a stair block
    - `oit`: the scene with improved transparency on
    - `ponder`: the water wheel Ponder scene
    - `jei`: the creative inventory with JEI's overlay, then JEI's recipes for the iron sheet
  - `-PwithJei` and `-PwithSodium` (Sodium + Iris) add those mods to the dev runs.
  - `-PsmokeTag=name-` prefixes the screenshot names; `-PsmokeProps=k=v,...` adds system properties.
  - `runClientSmokeVanilla` places the same vanilla blocks without Create loaded, as a rendering control.

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
| Schematic, toolbox and wrench keys | `InputEvent.Key` (`client/neoforge/CreateClientInput`) | `KeyboardHandlerMixin` (its `RETURN` ordinal 4 no longer exists: NeoForge merges the release and press branches before `ClientHooks.onKeyInput`) |
| Diving helmet | `LivingBreatheEvent` (`CreateNeoForge#breathe`) | `LivingEntityMixin#breatheInLava`, `#canBreatheInWater` |
| Train carriages leaving ticking chunks | `EntityEvent.EnteringSection` (`CreateNeoForge#enterSection`, server levels only; NeoForge also fires it on the client) | `PersistentEntitySectionManagerCallbackMixin` (its implicit `@Local long` is ambiguous in NeoForge's patched `onMove` and crashed the server the first time an entity changed section) |

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
  Mouse buttons are SDL codes too: `MOUSE_BUTTON_LEFT` = 1, `MIDDLE` = 2, `RIGHT` = 3, where GLFW used 0/2/1.
  `AbstractWidget#isValidClickButton` only accepts 1. Every raw GLFW number in the client code now uses `InputConstants`:
  - mouse buttons: the radial wrench and toolbox menus, the clipboard, the schematic and schematic-and-quill
    handlers, and Ponder's block selector
  - the synthetic `MouseButtonInfo(0, 0)` clicks that focus edit boxes, which vanilla ignored
  - key codes: Escape, Enter, keypad Enter, Backspace, Delete, the arrows, Page Up/Down, Home and End, in the
    clipboard, schedule, stock keeper, elevator contact and schematic prompt screens
- **First-person rendering.** `ItemInHandRenderer` became `FirstPersonHandsAndItemsRenderer`, which works from render
  states. The zapper, potato cannon and extendo-grip handlers now take `PlayerRenderState` /
  `FirstPersonHandsAndItemsRenderState`.
- **Glint.** Glint is part of the render type instead of a second pass (`armorCutoutNoCullGlint`, `itemCutoutGlint`).
- **Model quads.** `MaterialInfo#shade` became a nullable `shadeDirectionOverride`. `ItemQuads` replaces quad lists.
  `VertexConsumer#setUv3` was added.
- **Feature output target.** `RenderSystem.outputColorTextureOverride` is gone. Ponder and GUI scenes open their own
  render pass and call `FeatureRenderDispatcher.renderAllFeatures(pass, frame)`.
- **Core shaders.** Shaders now compile to SPIR-V: `#moj_import` became `#include`, every shader needs
  `#extension GL_ARB_separate_shader_objects`, and every `in`/`out` needs `layout(location = N)`. Vertex inputs are
  numbered in vertex format order (`ENTITY`: Position, Color, UV0, UV1, UV2, Normal). Ponder's `entity_block` and
  Create's `glowing_shader` were rewritten; with the old ones the first resource reload fails and the client quits.
- **Render passes bind their target once.** renderpearl binds a pass's framebuffer when the pass opens, not per draw.
  Flywheel draws in the middle of the main pass (`LevelRendererMixin#flywheel$beforeSolid`), and
  `MaterialRenderState#reset` used to bind framebuffer 0 afterwards. Everything later in that pass then went to the
  default framebuffer: translucent terrain (glass and water, which is translucent in 26.3), clouds, weather, and
  items on depots. `reset` now rebinds the framebuffer that was bound before `setupFrameBuffer`.
- **Pipelines start with no color targets.** `RenderPipeline.Builder` has zero color targets unless one is set, and
  vanilla sets `ColorTargetState.DEFAULT` explicitly. Ponder's `entity_block` snippets didn't, so their solid and
  cutout pipelines crashed (`Render pass color attachment count must match pipeline color target state count`)
  whenever they were drawn outside Flywheel, for example with Flywheel's backend set to `OFF`. The smoke test logs
  any non-vanilla pipeline without a color target.
- **Structure NBT needs data fixing.** Block states in structure NBT are `{id, properties}` now, and
  `StructureTemplate#load` doesn't fix old data. Vanilla's `TemplateSource` runs `DataFixTypes.STRUCTURE` first;
  Ponder's `PonderSceneRegistry#loadSchematic` and `SchematicItem#loadSchematic` didn't. Ponder's schematics are at
  data version 2975, so every block read as air and Ponder scenes showed only their shadow, outlines and text. Both
  loaders now data-fix first, which also keeps players' older schematic files loading.
- **GUI draws run at the end of the frame.** `GuiRenderer` records picture-in-picture blits during `prepare` and
  draws them later. Create's cached GUI textures (`catnip/gui/render/GpuTexture`, used by the Ponder, JEI and
  item-transform renderers) were closed mid-frame when an element was cleared or resized, which crashed with
  `Texture view Sampler0 (UI Item Transform texture) has been closed!`, for example when opening Ponder.
  `GpuTexture#close` now queues the texture, and `closePending` runs at the start of each client tick.
- **Mod construction order.** NeoForge constructs mods before `Minecraft` exists, while Fabric's client entrypoint runs
  after it. `SchematicHandler` therefore creates its `ToolSelectionScreen` on first use.
- **NeoForge overloads.** `ModelManager#discoverModelDependencies` gained a standalone-models parameter, and the vanilla
  3-argument method now only delegates. `ModelManagerMixin` targets the full descriptor. `tools/check_mixins.py`
  flags name-only injector targets that match several overloads.
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
- **`ModelBlockRendererMixin` overwrites `tesselateBlock` and `tesselateFlat`.** It runs at priority 900 so other
  client mods can still inject into them; Mixin refuses injections into a method overwritten at equal or higher
  priority. In the full pack, Fusion's `ModelBlockRendererMixin` wraps `getOffset(pos)` and crashed at the first chunk.
  The overwrites also follow NeoForge's patched bodies call for call, so injections still find their targets:
  - `lighter.reset()`
  - the per-part ambient-occlusion config and `BlockStateModelPart#ambientOcclusion()`
  - the 5-argument `shouldRenderFace`, which carries NeoForge's face-hiding hooks
  The only other `@Overwrite`s target Create's own classes, and Create has no `@Redirect`s on vanilla code.
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
- **Fluid and item saving (fixed 2026-10-18).** 26.3 changed `PatchedDataComponentMap.patch` and
  `DataComponentPatch.map` to hold raw component values (with a package-private `Removed` marker) instead of
  `Optional`s. Create's `FluidInventory.setMaxSize` and `ContainerExtension.setMaxSize` still put an `Optional<Integer>`
  for `create:fluid_max_capacity` / `minecraft:max_stack_size` into the patch, so encoding a stored stack threw
  `ClassCastException: Optional cannot be cast to Integer` and every fluid-holding block entity (basin, spout, drain,
  tank, ...) lost its contents on chunk save. Both now store `max.orElseThrow()`. `create:basin_fluid_survives_save`
  puts 4050 droplets of lava in a basin, saves it with `saveWithFullMetadata`, loads it back with `loadWithComponents`
  and round-trips the stack through `FluidStack.OPTIONAL_PACKET_CODEC`. It fails with the old code and passes now.
- **Runtime mixins.** With `-PmixinDebug` (`mixin.debug.verbose`), 60 Create mixins applied on the dedicated server with
  no warnings or failures. The other nine are not loaded by a game-test server:
  - `ConfigurationProtocolsMixin`, `ServerConfigurationPacketListenerImplMixin` and `GameProtocolsMixin` (networking)
  - `ExperimentalRedstoneWireEvaluatorMixin`, `RailStateMixin`,
    `StartAttackingMixin`, `SynchedEntityDataBuilderMixin` and `TooltipContextMixin`
  - `ArchitecturyMixin`, which is disabled upstream

  The client config can only be exercised by `runClient`.
- **Full ZP5 pack.** With the 136 server and both-side jars from `pack/mods` (Not Enough Crashes held out) in `run/mods`, `runGameTestServer` boots, and `water_wheel_drives_press` passes at tick 134. The 74 failures are the pack's known baseline (72 Easy Mob Farm, 2 Enderite mod: the game-test mock player isn't a `ServerPlayer`). The parent session's full-pack server boot found the section-move crash above.
- **Client (`runClientSmoke`, 2026-10-18, OpenGL on an RTX 5090).** All client mixins apply. Every check passes,
  with screenshots for each:
  - `title`
  - `press`: wheel 8 RPM, press 8 RPM, iron sheet on the depot; the glass channel and water render
  - `contraption`: the bearing assembles and turns a stair block; the contraption entity exists on both sides
  - `oit`: improved transparency on; glass and water still render
  - `ponder`: the water wheel scene, with its base plate, water wheel, shaft, gauge and text
  - `jei`: JEI's overlay and creative tabs, and Create's Pressing category with the animated press
  Variants:
  - Flywheel backend `OFF`: `press` passes and renders the same.
  - Sodium 0.9.3-beta.1 + Iris 1.11.7 (`-PwithSodium`, no shader pack): `press`, `contraption` and `ponder` pass and
    render correctly. Iris's deprecated `logoFile` raises a NeoForge loading-warnings screen, which the smoke test
    dismisses.
- **Not checked on the client:** trains (assembling one needs track targeting and station assembly, which the smoke
  test can't script reliably yet), the schematic and toolbox UIs, Iris with a shader pack, and the Vulkan backend.
  The `TODO(neoforge 26.3)` rendering items listed above are still open.

Upstream data problems that 26.3 makes fatal: advancements that pointed at recipes that don't exist
(`crafting///tree_fertilizer`, `crafting/materials/copper_ingot`, `crafting/materials/copper_nugget`) were removed.
26.3 resolves recipe references as registry holders. `tools/check_recipe_refs.py` finds any new ones.
