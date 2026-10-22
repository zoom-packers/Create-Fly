package com.zurrtum.create.devclient;

import com.mojang.blaze3d.platform.InputConstants;
import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.client.flywheel.api.backend.Backend;
import com.zurrtum.create.client.flywheel.api.backend.BackendManager;
import com.zurrtum.create.client.flywheel.api.model.IndexSequence;
import com.zurrtum.create.client.flywheel.api.model.Mesh;
import com.zurrtum.create.client.flywheel.api.model.Model;
import com.zurrtum.create.client.flywheel.api.vertex.MutableVertexList;
import com.zurrtum.create.client.flywheel.api.visual.Effect;
import com.zurrtum.create.client.flywheel.api.visual.EffectVisual;
import com.zurrtum.create.client.flywheel.api.visualization.VisualizationContext;
import com.zurrtum.create.client.flywheel.api.visualization.VisualizationManager;
import com.zurrtum.create.client.flywheel.lib.instance.InstanceTypes;
import com.zurrtum.create.client.flywheel.lib.instance.TransformedInstance;
import com.zurrtum.create.client.flywheel.lib.material.Materials;
import com.zurrtum.create.client.flywheel.lib.material.SimpleMaterial;
import com.zurrtum.create.client.flywheel.lib.model.Models;
import com.zurrtum.create.client.flywheel.lib.model.SingleMeshModel;
import com.zurrtum.create.client.mixin.TextureManagerAccessor;
import com.zurrtum.create.client.ponder.foundation.ui.PonderUI;
import com.zurrtum.create.content.contraptions.AbstractContraptionEntity;
import com.zurrtum.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.zurrtum.create.content.kinetics.base.DirectionalKineticBlock;
import com.zurrtum.create.content.kinetics.base.HorizontalKineticBlock;
import com.zurrtum.create.content.kinetics.base.KineticBlockEntity;
import com.zurrtum.create.content.kinetics.belt.transport.TransportedItemStack;
import com.zurrtum.create.content.logistics.depot.DepotBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.CreativeTabsScreenPage;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector4fc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Dev-only client smoke test, loaded only by the {@code clientSmoke} runs (never shipped in the jar). It waits for the
 * title screen, creates a flat world, runs the scenarios named in {@code create.clientSmoke.scenarios}, takes
 * screenshots into the run's screenshots folder, logs one {@code SMOKE RESULT} line per check and quits.
 * <ul>
 *     <li>{@code press}: the CreateGameTests water wheel → shaft → press setup, checked on the server</li>
 *     <li>{@code contraption}: a creative motor turning a mechanical bearing with a stair block on it</li>
 *     <li>{@code oit}: the same scene with improved transparency switched on</li>
 *     <li>{@code ponder}: the water wheel ponder scene</li>
 *     <li>{@code jei}: the creative inventory with JEI's overlay, then JEI's recipes for the iron sheet</li>
 *     <li>{@code creative}: every tab on every page of the creative inventory can be clicked and selected</li>
 *     <li>{@code texture}: a Flywheel visual whose material uses a texture nothing has loaded yet; Flywheel must stay
 *     on its backend</li>
 * </ul>
 */
@Mod(value = "create_devclient", dist = Dist.CLIENT)
public final class ClientSmokeTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("create-smoke");
    private static final String WORLD = "create_smoke";
    private static final int TIMEOUT_TICKS = 20 * 60 * 6;
    /** Control run without Create loaded (clientSmokeVanilla): only the vanilla blocks of the setup are placed. */
    private static final boolean VANILLA = Boolean.getBoolean("create.clientSmoke.vanilla");
    private static final Set<String> SCENARIOS = Set.of(System.getProperty("create.clientSmoke.scenarios", "press").split(","));
    /** Screenshot name prefix, so runs with different mods (Sodium, JEI, ...) don't overwrite each other. */
    private static final String TAG = System.getProperty("create.clientSmoke.tag", "");

    private enum Stage { TITLE, LOADING, RUNNING, DONE }

    private Stage stage = Stage.TITLE;
    private int ticks;
    private int totalTicks;
    private Class<?> lastScreen;
    private final TreeMap<Integer, List<Runnable>> timeline = new TreeMap<>();
    private final List<String> results = new ArrayList<>();
    private volatile BlockPos origin;
    private volatile float earlierAngle = Float.NaN;
    private Backend backendBeforeTexture;
    /** An entity texture a flat world never loads, so the Flywheel material below is the first to use it. */
    private static final Identifier UNLOADED_TEXTURE = Identifier.withDefaultNamespace("textures/entity/sniffer/sniffer.png");

    public ClientSmokeTest() {
        if (Boolean.getBoolean("create.clientSmoke")) {
            NeoForge.EVENT_BUS.addListener(this::tick);
        }
    }

    private void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        Class<?> screenClass = screen == null ? null : screen.getClass();
        if (screenClass != lastScreen) {
            LOGGER.info("SMOKE screen {} at tick {}", screenClass == null ? "none" : screenClass.getName(), totalTicks);
            lastScreen = screenClass;
        }
        if (++totalTicks > TIMEOUT_TICKS && stage != Stage.DONE) {
            result("timeout", false, "stuck in stage " + stage);
            shot(mc, "timeout");
            stage = Stage.DONE;
            ticks = 0;
            return;
        }
        switch (stage) {
            case TITLE -> {
                if (screen instanceof net.neoforged.neoforge.client.gui.LoadingErrorScreen) {
                    // Mod loading warnings (e.g. a deprecated mods.toml key in another mod); they are in the log
                    result("loading-warnings", true, "dismissed NeoForge's loading warnings screen");
                    mc.gui.setScreen(new TitleScreen());
                    break;
                }
                if (screen instanceof TitleScreen && ++ticks == 100) {
                    shot(mc, "1-title");
                    result("title", true, "title screen reached at tick " + totalTicks);
                    RenderPipelines.PIPELINES_BY_LOCATION.forEach((id, pipeline) -> {
                        if (!id.getNamespace().equals("minecraft") && pipeline.getColorTargetStates().isEmpty()) {
                            result("pipeline", false, id + " has no color target");
                        }
                    });
                    stage = Stage.LOADING;
                    ticks = 0;
                    LevelSettings settings = new LevelSettings(
                        WORLD,
                        GameType.CREATIVE,
                        LevelSettings.DifficultySettings.DEFAULT,
                        true,
                        WorldDataConfiguration.DEFAULT
                    );
                    mc.createWorldOpenFlows()
                        .createFreshLevel(WORLD, settings, WorldOptions.testWorldWithRandomSeed(), WorldPresets::createTestWorldDimensions, new TitleScreen());
                }
            }
            case LOADING -> {
                if (mc.player != null && mc.level != null && mc.getSingleplayerServer() != null && screen == null && ++ticks == 60) {
                    LOGGER.info("SMOKE world loaded at tick {}", totalTicks);
                    build(mc.getSingleplayerServer(), mc.player.getUUID());
                    planTimeline(mc);
                    stage = Stage.RUNNING;
                    ticks = 0;
                }
            }
            case RUNNING -> {
                ticks++;
                List<Runnable> actions = timeline.remove(ticks);
                if (actions != null) {
                    for (Runnable action : actions) {
                        try {
                            action.run();
                        } catch (Throwable t) {
                            LOGGER.error("SMOKE action at tick {} threw", ticks, t);
                            result("action@" + ticks, false, t.toString());
                        }
                    }
                }
                if (timeline.isEmpty()) {
                    stage = Stage.DONE;
                    ticks = 0;
                }
            }
            case DONE -> {
                if (++ticks == 40) {
                    LOGGER.info("SMOKE SUMMARY ({} checks): {}", results.size(), String.join("; ", results));
                    LOGGER.info("SMOKE done, stopping the client");
                    mc.stop();
                }
            }
        }
    }

    private void at(int tick, Runnable action) {
        timeline.computeIfAbsent(tick, k -> new ArrayList<>()).add(action);
    }

    private void planTimeline(Minecraft mc) {
        if (!VANILLA && SCENARIOS.contains("press")) {
            at(10, () -> onServer(mc, (server, level, player) -> {
                DepotBlockEntity depot = (DepotBlockEntity) level.getBlockEntity(origin.offset(4, 1, 2));
                depot.depotBehaviour.setHeldItem(new TransportedItemStack(new ItemStack(Items.IRON_INGOT)));
            }));
        }
        at(70, () -> shot(mc, "2-running"));
        at(300, () -> shot(mc, "3-after"));
        if (!VANILLA && SCENARIOS.contains("press")) {
            at(300, () -> onServer(mc, (server, level, player) -> {
                KineticBlockEntity wheel = (KineticBlockEntity) level.getBlockEntity(origin.offset(2, 3, 2));
                KineticBlockEntity press = (KineticBlockEntity) level.getBlockEntity(origin.offset(4, 3, 2));
                ItemStack held = ((DepotBlockEntity) level.getBlockEntity(origin.offset(4, 1, 2))).depotBehaviour.getHeldItemStack();
                result(
                    "press",
                    press.getSpeed() != 0 && held.is(AllItems.IRON_SHEET),
                    "depot holds " + held + ", wheel speed " + wheel.getSpeed() + ", press speed " + press.getSpeed()
                );
            }));
        }
        if (!VANILLA && SCENARIOS.contains("contraption")) {
            // 16 RPM turns the bearing exactly four times in 300 ticks, so compare against an earlier reading
            at(290, () -> onServer(mc, (server, level, player) -> {
                if (level.getBlockEntity(origin.offset(-1, 2, 2)) instanceof MechanicalBearingBlockEntity bearing) {
                    earlierAngle = bearing.getInterpolatedAngle(0);
                }
            }));
            at(300, () -> {
                BlockPos bearingPos = origin.offset(-1, 2, 2);
                List<AbstractContraptionEntity> clientEntities = mc.level.getEntitiesOfClass(AbstractContraptionEntity.class, new AABB(bearingPos).inflate(3));
                float clientAngle = mc.level.getBlockEntity(bearingPos) instanceof MechanicalBearingBlockEntity bearing ? bearing.getInterpolatedAngle(0) : Float.NaN;
                onServer(mc, (server, level, player) -> {
                    List<AbstractContraptionEntity> entities = level.getEntitiesOfClass(AbstractContraptionEntity.class, new AABB(bearingPos).inflate(3));
                    MechanicalBearingBlockEntity bearing = (MechanicalBearingBlockEntity) level.getBlockEntity(bearingPos);
                    float angle = bearing.getInterpolatedAngle(0);
                    result(
                        "contraption",
                        !entities.isEmpty() && !clientEntities.isEmpty() && angle != earlierAngle,
                        "server contraption entities " + entities.size() + ", client " + clientEntities.size() + ", bearing angle server "
                            + earlierAngle + " -> " + angle + " over 10 ticks, client " + clientAngle + ", speed " + bearing.getSpeed()
                    );
                });
            });
        }
        int t = 300;
        if (SCENARIOS.contains("oit")) {
            at(t + 20, () -> mc.options.improvedTransparency().set(true));
            at(t + 80, () -> {
                shot(mc, "4-oit");
                result("oit", mc.gameRenderer.useImprovedTransparency(), "improved transparency active, see the 4-oit screenshot");
            });
            at(t + 90, () -> mc.options.improvedTransparency().set(false));
            t += 100;
        }
        if (!VANILLA && SCENARIOS.contains("ponder")) {
            at(t + 10, () -> mc.gui.setScreen(PonderUI.of(new ItemStack(AllBlocks.WATER_WHEEL))));
            at(t + 50, () -> shot(mc, "5a-ponder"));
            at(t + 110, () -> shot(mc, "5b-ponder"));
            at(t + 170, () -> {
                shot(mc, "5-ponder");
                result("ponder", mc.gui.screen() instanceof PonderUI, "screen " + mc.gui.screen());
            });
            at(t + 180, () -> mc.gui.setScreen(null));
            t += 200;
        }
        if (SCENARIOS.contains("jei")) {
            boolean jei = ModList.get().isLoaded("jei");
            if (!jei) {
                result("jei", false, "JEI is not loaded (run with -PwithJei)");
            } else {
                at(t + 10, () -> mc.gui.setScreen(new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), true)));
                at(t + 70, () -> shot(mc, "6-jei-overlay"));
                at(t + 80, () -> JeiAccess.showRecipesFor(new ItemStack(AllItems.IRON_SHEET)));
                at(t + 170, () -> {
                    shot(mc, "7-jei-recipes");
                    Screen current = mc.gui.screen();
                    result("jei", JeiAccess.hasRuntime() && current != null && current.getClass().getName().startsWith("mezz.jei"), "screen " + current);
                });
                at(t + 180, () -> mc.gui.setScreen(null));
                t += 200;
            }
        }
        if (SCENARIOS.contains("creative")) {
            at(t + 10, () -> mc.gui.setScreen(new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), true)));
            at(t + 40, () -> checkCreativeTabs(mc));
            at(t + 50, () -> mc.gui.setScreen(null));
            t += 60;
        }
        if (!VANILLA && SCENARIOS.contains("texture")) {
            at(t + 10, () -> {
                backendBeforeTexture = BackendManager.currentBackend();
                if (((TextureManagerAccessor) mc.getTextureManager()).create$getByPath().containsKey(UNLOADED_TEXTURE)) {
                    result("flywheel-texture", false, UNLOADED_TEXTURE + " was already loaded, so the check proves nothing");
                }
                VisualizationManager.get(mc.level).effects().queueAdd(new TextureEffect(mc.level, origin.offset(6, 2, 6)));
            });
            at(t + 60, () -> {
                shot(mc, "8-texture");
                Backend backend = BackendManager.currentBackend();
                boolean loaded = ((TextureManagerAccessor) mc.getTextureManager()).create$getByPath().containsKey(UNLOADED_TEXTURE);
                result(
                    "flywheel-texture",
                    BackendManager.isBackendOn() && backend == backendBeforeTexture && loaded,
                    "backend " + Backend.REGISTRY.getId(backendBeforeTexture) + " -> " + Backend.REGISTRY.getId(backend) + ", " + UNLOADED_TEXTURE + " loaded " + loaded
                );
            });
            t += 70;
        }
        at(t + 20, () -> {
        });
    }

    /**
     * Clicks every tab on every page of NeoForge's creative tab pages, as a player would, and checks that it gets
     * selected. Create's Fabric creative-tab paging hid every tab whose Fabric page differed from its own page counter,
     * which left NeoForge's mod-tab pages without tabs.
     */
    private void checkCreativeTabs(Minecraft mc) {
        if (!(mc.gui.screen() instanceof CreativeModeInventoryScreen screen)) {
            result("creative-tabs", false, "creative inventory not open: " + mc.gui.screen());
            return;
        }
        try {
            Field pagesField = CreativeModeInventoryScreen.class.getDeclaredField("pages");
            pagesField.setAccessible(true);
            Field selectedField = CreativeModeInventoryScreen.class.getDeclaredField("selectedTab");
            selectedField.setAccessible(true);
            Method tabX = CreativeModeInventoryScreen.class.getDeclaredMethod("getTabX", CreativeModeTab.class);
            tabX.setAccessible(true);
            Method tabY = CreativeModeInventoryScreen.class.getDeclaredMethod("getTabY", CreativeModeTab.class);
            tabY.setAccessible(true);
            Field leftField = AbstractContainerScreen.class.getDeclaredField("leftPos");
            leftField.setAccessible(true);
            Field topField = AbstractContainerScreen.class.getDeclaredField("topPos");
            topField.setAccessible(true);
            int left = leftField.getInt(screen);
            int top = topField.getInt(screen);
            @SuppressWarnings("unchecked")
            List<CreativeTabsScreenPage> pages = List.copyOf((List<CreativeTabsScreenPage>) pagesField.get(screen));
            List<String> broken = new ArrayList<>();
            int clicked = 0;
            for (int i = 0; i < pages.size(); i++) {
                CreativeTabsScreenPage page = pages.get(i);
                screen.setCurrentPage(page);
                for (CreativeModeTab tab : page.getVisibleTabs()) {
                    // The tab's centre
                    double x = left + (int) tabX.invoke(screen, tab) + 13;
                    double y = top + (int) tabY.invoke(screen, tab) + 16;
                    MouseButtonEvent click = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
                    screen.mouseClicked(click, false);
                    screen.mouseReleased(click);
                    clicked++;
                    if (selectedField.get(null) != tab) {
                        broken.add("page " + (i + 1) + ": " + BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab));
                    }
                }
            }
            result(
                "creative-tabs",
                !pages.isEmpty() && broken.isEmpty(),
                pages.size() + " pages, " + clicked + " tab clicks" + (broken.isEmpty() ? "" : ", not selectable: " + String.join(", ", broken))
            );
        } catch (ReflectiveOperationException e) {
            result("creative-tabs", false, e.toString());
        }
    }

    private void result(String check, boolean ok, String detail) {
        String line = check + " " + (ok ? "PASS" : "FAIL") + ": " + detail;
        results.add(check + "=" + (ok ? "PASS" : "FAIL"));
        if (ok) {
            LOGGER.info("SMOKE RESULT {}", line);
        } else {
            LOGGER.error("SMOKE RESULT {}", line);
        }
    }

    private interface ServerTask {
        void run(IntegratedServer server, ServerLevel level, ServerPlayer player);
    }

    private void onServer(Minecraft mc, ServerTask task) {
        IntegratedServer server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                task.run(server, player.level(), player);
            } catch (Throwable t) {
                LOGGER.error("SMOKE server task threw", t);
                result("server-task", false, t.toString());
            }
        });
    }

    /** The CreateGameTests layout 12 blocks north of the player, viewed from the south, plus the scenario extras. */
    private void build(IntegratedServer server, UUID id) {
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            ServerLevel level = player.level();
            BlockPos o = player.blockPosition().offset(-4, 0, -12);
            for (int x = 0; x < 9; x++) {
                for (int z = 0; z < 5; z++) {
                    set(level, o.offset(x, 0, z), Blocks.STONE.defaultBlockState());
                }
            }
            for (int z = 0; z < 5; z++) {
                set(level, o.offset(1, 4, z), Blocks.GLASS.defaultBlockState());
                set(level, o.offset(3, 4, z), Blocks.GLASS.defaultBlockState());
                if (z != 2) {
                    set(level, o.offset(2, 3, z), Blocks.STONE.defaultBlockState());
                }
            }
            if (!VANILLA) {
                placePress(level, o);
                if (SCENARIOS.contains("contraption")) {
                    placeContraption(level, o);
                }
            }
            // Cap the channel's open end, or the water flows off it towards the nearest drop instead of over the wheel
            set(level, o.offset(2, 4, -1), Blocks.GLASS.defaultBlockState());
            set(level, o.offset(2, 4, 0), Blocks.WATER.defaultBlockState());
            // A pillar for the camera, south of the setup and looking north at it
            set(level, o.offset(4, 0, 9), Blocks.STONE.defaultBlockState());
            set(level, o.offset(4, 1, 9), Blocks.STONE.defaultBlockState());
            origin = o;
            var source = player.createCommandSourceStack().withSuppressedOutput();
            server.getCommands().performPrefixedCommand(source, "difficulty peaceful");
            server.getCommands().performPrefixedCommand(source, "time set noon");
            // Fly, or the water running off the setup pushes the camera away
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(level, o.getX() + 4.5, o.getY() + 2, o.getZ() + 9.5, Set.of(), 180, 25, true);
            LOGGER.info("SMOKE built the setup at {} for scenarios {}", o, SCENARIOS);
        });
    }

    private static void placePress(ServerLevel level, BlockPos o) {
        set(level, o.offset(2, 3, 2), AllBlocks.WATER_WHEEL.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        set(level, o.offset(3, 3, 2), AllBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        set(level, o.offset(4, 3, 2), AllBlocks.MECHANICAL_PRESS.defaultBlockState().setValue(HorizontalKineticBlock.HORIZONTAL_FACING, Direction.EAST));
        set(level, o.offset(4, 1, 2), AllBlocks.DEPOT.defaultBlockState());
    }

    /** A creative motor turns a south-facing bearing; the stair block in front of it becomes a rotating contraption. */
    private static void placeContraption(ServerLevel level, BlockPos o) {
        set(level, o.offset(-1, 2, 1), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.SOUTH));
        set(level, o.offset(-1, 2, 2), AllBlocks.MECHANICAL_BEARING.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.SOUTH));
        set(level, o.offset(-1, 2, 3), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 3);
    }

    /** A sniffer-textured cube drawn by Flywheel, for the {@code texture} scenario. */
    private record TextureEffect(LevelAccessor level, BlockPos pos) implements Effect {
        @Override
        public EffectVisual<?> visualize(VisualizationContext ctx, float partialTick) {
            return new TextureEffectVisual(ctx, pos);
        }
    }

    /** Stretches the block mesh's atlas UVs over the whole texture, so the screenshot shows which texture was drawn. */
    private record WholeTextureMesh(Mesh mesh) implements Mesh {
        @Override
        public int vertexCount() {
            return mesh.vertexCount();
        }

        @Override
        public void write(MutableVertexList vertexList) {
            mesh.write(vertexList);
            float minU = Float.MAX_VALUE, maxU = -Float.MAX_VALUE, minV = Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
            for (int i = 0; i < vertexCount(); i++) {
                minU = Math.min(minU, vertexList.u(i));
                maxU = Math.max(maxU, vertexList.u(i));
                minV = Math.min(minV, vertexList.v(i));
                maxV = Math.max(maxV, vertexList.v(i));
            }
            for (int i = 0; i < vertexCount(); i++) {
                vertexList.u(i, (vertexList.u(i) - minU) / (maxU - minU));
                vertexList.v(i, (vertexList.v(i) - minV) / (maxV - minV));
            }
        }

        @Override
        public IndexSequence indexSequence() {
            return mesh.indexSequence();
        }

        @Override
        public int indexCount() {
            return mesh.indexCount();
        }

        @Override
        public Vector4fc boundingSphere() {
            return mesh.boundingSphere();
        }
    }

    private static final class TextureEffectVisual implements EffectVisual<TextureEffect> {
        private final TransformedInstance instance;

        TextureEffectVisual(VisualizationContext ctx, BlockPos pos) {
            Model stone = Models.block(Blocks.STONE.defaultBlockState());
            Model model = new SingleMeshModel(
                new WholeTextureMesh(stone.meshes().getFirst().mesh()),
                SimpleMaterial.builderOf(Materials.SOLID_BLOCK).texture(UNLOADED_TEXTURE).build()
            );
            instance = ctx.instancerProvider().instancer(InstanceTypes.TRANSFORMED, model).createInstance();
            Vec3i o = ctx.renderOrigin();
            instance.setIdentityTransform().translate(pos.getX() - o.getX(), pos.getY() - o.getY(), pos.getZ() - o.getZ())
                .light(LightCoordsUtil.FULL_BRIGHT).setChanged();
        }

        @Override
        public void update(float partialTick) {
        }

        @Override
        public void delete() {
            instance.delete();
        }
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "smoke-" + TAG + name + ".png", mc.gameRenderer.mainRenderTarget(), 1, message -> LOGGER.info("SMOKE screenshot: {}", message.getString()));
    }
}
