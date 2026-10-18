package com.zurrtum.create.devclient;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.content.kinetics.base.DirectionalKineticBlock;
import com.zurrtum.create.content.kinetics.base.HorizontalKineticBlock;
import com.zurrtum.create.content.kinetics.base.KineticBlockEntity;
import com.zurrtum.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import com.zurrtum.create.content.kinetics.belt.transport.TransportedItemStack;
import com.zurrtum.create.content.logistics.depot.DepotBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;

/**
 * Dev-only client smoke test, loaded only by the {@code clientSmoke} run (never shipped in the jar). It waits for the
 * title screen, creates a flat world, builds the water wheel → shaft → press setup from CreateGameTests in front of the
 * player, takes screenshots into the run's screenshots folder, logs the result with a {@code SMOKE} prefix and quits.
 */
@Mod(value = "create_devclient", dist = Dist.CLIENT)
public final class ClientSmokeTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("create-smoke");
    private static final String WORLD = "create_smoke";
    private static final int TIMEOUT_TICKS = 20 * 60 * 6;
    /** Control run without Create loaded (clientSmokeVanilla): only the vanilla blocks of the setup are placed. */
    private static final boolean VANILLA = Boolean.getBoolean("create.clientSmoke.vanilla");

    private enum Stage { TITLE, LOADING, RUNNING, DONE }

    private Stage stage = Stage.TITLE;
    private int ticks;
    private int totalTicks;
    private Class<?> lastScreen;
    private volatile BlockPos origin;
    private volatile long buildTime;

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
            LOGGER.error("SMOKE FAIL: timed out in stage {}", stage);
            finish(mc, "smoke-timeout.png");
            return;
        }
        switch (stage) {
            case TITLE -> {
                if (screen instanceof TitleScreen && ++ticks == 100) {
                    shot(mc, "smoke-1-title.png");
                    LOGGER.info("SMOKE title screen reached at tick {}", totalTicks);
                    net.minecraft.client.renderer.RenderPipelines.PIPELINES_BY_LOCATION.forEach((id, pipeline) -> {
                        if (!id.getNamespace().equals("minecraft") && pipeline.getColorTargetStates().isEmpty()) {
                            LOGGER.error("SMOKE pipeline {} has no color target", id);
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
                    stage = Stage.RUNNING;
                    ticks = 0;
                }
            }
            case RUNNING -> {
                ticks++;
                if (ticks == 10 && !VANILLA) {
                    onServer(mc, (server, level, player) -> {
                        DepotBlockEntity depot = (DepotBlockEntity) level.getBlockEntity(origin.offset(4, 1, 2));
                        depot.depotBehaviour.setHeldItem(new TransportedItemStack(new ItemStack(Items.IRON_INGOT)));
                    });
                }
                if (ticks == 70) {
                    shot(mc, "smoke-2-running.png");
                    logModels(mc);
                    LOGGER.info(
                        "SMOKE client sees glass {}, water {}, wheel {}",
                        mc.level.getBlockState(origin.offset(1, 4, 2)),
                        mc.level.getBlockState(origin.offset(2, 4, 0)),
                        mc.level.getBlockState(origin.offset(2, 3, 2))
                    );
                }
                if (ticks == 300 && VANILLA) {
                    shot(mc, "smoke-3-after.png");
                    stage = Stage.DONE;
                    ticks = 0;
                } else if (ticks == 300) {
                    shot(mc, "smoke-3-after.png");
                    onServer(mc, (server, level, player) -> {
                        KineticBlockEntity wheel = (KineticBlockEntity) level.getBlockEntity(origin.offset(2, 3, 2));
                        KineticBlockEntity press = (KineticBlockEntity) level.getBlockEntity(origin.offset(4, 3, 2));
                        ItemStack held = ((DepotBlockEntity) level.getBlockEntity(origin.offset(4, 1, 2))).depotBehaviour.getHeldItemStack();
                        boolean ok = press.getSpeed() != 0 && held.is(AllItems.IRON_SHEET);
                        logChannel(level, origin, "at check");
                        LOGGER.info(
                            "SMOKE control water: source {}, east neighbour {}, below {}",
                            level.getFluidState(origin.offset(-6, 0, 0)),
                            level.getFluidState(origin.offset(-5, 0, 0)),
                            level.getBlockState(origin.offset(-6, -1, 0))
                        );
                        LOGGER.info(
                            "SMOKE water tick scheduled {}, fluid ticks pending {}, block ticks pending {}",
                            level.getFluidTicks().hasScheduledTick(origin.offset(2, 4, 0), Fluids.WATER),
                            level.getFluidTicks().count(),
                            level.getBlockTicks().count()
                        );
                        LOGGER.info(
                            "SMOKE server ticks since build {}, frozen {}, water over wheel {}, wheel flow score {}, wheel state {}",
                            level.getGameTime() - buildTime,
                            level.tickRateManager().isFrozen(),
                            level.getFluidState(origin.offset(2, 4, 2)),
                            ((WaterWheelBlockEntity) wheel).flowScore,
                            level.getBlockState(origin.offset(2, 3, 2))
                        );
                        LOGGER.info(
                            "SMOKE {}: depot holds {}, wheel speed {}, press speed {}",
                            ok ? "PASS" : "FAIL",
                            held,
                            wheel.getSpeed(),
                            press.getSpeed()
                        );
                    });
                    stage = Stage.DONE;
                    ticks = 0;
                }
            }
            case DONE -> {
                if (++ticks == 40) {
                    LOGGER.info("SMOKE done, stopping the client");
                    mc.stop();
                }
            }
        }
    }

    private interface ServerTask {
        void run(IntegratedServer server, ServerLevel level, ServerPlayer player);
    }

    private static void onServer(Minecraft mc, ServerTask task) {
        IntegratedServer server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            task.run(server, player.level(), player);
        });
    }

    /** The CreateGameTests layout, 12 blocks north of the player, viewed from the south. */
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
                placeCreateBlocks(level, o);
            }
            // Cap the channel's open end, or the water flows off it towards the nearest drop instead of over the wheel
            set(level, o.offset(2, 4, -1), Blocks.GLASS.defaultBlockState());
            set(level, o.offset(2, 4, 0), Blocks.WATER.defaultBlockState());
            // Control: a plain water source on the ground, away from Create blocks
            set(level, o.offset(-6, 0, 0), Blocks.WATER.defaultBlockState());
            // A pillar for the camera, south of the setup and looking north at it
            set(level, o.offset(4, 0, 9), Blocks.STONE.defaultBlockState());
            set(level, o.offset(4, 1, 9), Blocks.STONE.defaultBlockState());
            origin = o;
            var source = player.createCommandSourceStack().withSuppressedOutput();
            server.getCommands().performPrefixedCommand(source, "difficulty peaceful");
            server.getCommands().performPrefixedCommand(source, "time set noon");
            player.teleportTo(level, o.getX() + 4.5, o.getY() + 2, o.getZ() + 9.5, Set.of(), 180, 25, true);
            buildTime = level.getGameTime();
            LOGGER.info("SMOKE built the setup at {}, player now at {}", o, player.blockPosition());
            logChannel(level, o, "after build");
            LOGGER.info(
                "SMOKE water tick scheduled {}, fluid ticks pending {}, water state {}",
                level.getFluidTicks().hasScheduledTick(o.offset(2, 4, 0), Fluids.WATER),
                level.getFluidTicks().count(),
                level.getFluidState(o.offset(2, 4, 0))
            );
        });
    }

    private static void placeCreateBlocks(ServerLevel level, BlockPos o) {
            set(level, o.offset(2, 3, 2), AllBlocks.WATER_WHEEL.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
            set(level, o.offset(3, 3, 2), AllBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
            set(level, o.offset(4, 3, 2), AllBlocks.MECHANICAL_PRESS.defaultBlockState().setValue(HorizontalKineticBlock.HORIZONTAL_FACING, Direction.EAST));
            set(level, o.offset(4, 1, 2), AllBlocks.DEPOT.defaultBlockState());
    }

    /** Which chunk layer and sprite the client-side models put glass and water in. */
    private static void logModels(Minecraft mc) {
        var models = mc.getModelManager();
        java.util.List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts = new java.util.ArrayList<>();
        models.getBlockStateModelSet().get(Blocks.GLASS.defaultBlockState()).collectParts(net.minecraft.util.RandomSource.create(0), parts);
        StringBuilder sb = new StringBuilder();
        for (var part : parts) {
            for (Direction d : Direction.values()) {
                for (var quad : part.getQuads(d)) {
                    sb.append(' ').append(d).append(':').append(quad.materialInfo().layer()).append('/').append(quad.materialInfo().sprite().contents().name());
                }
            }
        }
        var water = models.getFluidStateModelSet().get(net.minecraft.world.level.material.Fluids.WATER.defaultFluidState());
        LOGGER.info("SMOKE glass parts {} quads:{}; water model layer {} still {}", parts.size(), sb, water.layer(), water.stillMaterial().sprite().contents().name());
    }

    private static void logChannel(ServerLevel level, BlockPos o, String when) {
        StringBuilder sb = new StringBuilder();
        for (int z = 0; z < 5; z++) {
            sb.append(" z").append(z).append('=');
            for (int x = 1; x <= 3; x++) {
                sb.append(level.getBlockState(o.offset(x, 4, z)).getBlock().getDescriptionId().replaceAll(".*[.]", "")).append(x < 3 ? "," : "");
            }
        }
        LOGGER.info("SMOKE channel (y+4, x=1..3) {}:{}", when, sb);
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 3);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, message -> LOGGER.info("SMOKE screenshot: {}", message.getString()));
    }

    private void finish(Minecraft mc, String name) {
        shot(mc, name);
        stage = Stage.DONE;
        ticks = 0;
    }
}
