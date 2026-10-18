package com.zurrtum.create.neoforge;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.kinetics.base.DirectionalKineticBlock;
import com.zurrtum.create.content.kinetics.base.HorizontalKineticBlock;
import com.zurrtum.create.content.kinetics.base.KineticBlockEntity;
import com.zurrtum.create.content.kinetics.belt.transport.TransportedItemStack;
import com.zurrtum.create.content.logistics.depot.DepotBlockEntity;
import com.zurrtum.create.content.processing.basin.BasinBlockEntity;
import com.zurrtum.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour.TankSegment;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Consumer;

/**
 * ZP5 smoke tests for the NeoForge port, run by {@code runGameTestServer}. They build their own setup in an empty
 * structure (data/create/structure/gametest/empty_9x7x5.nbt).
 */
public final class CreateGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(
        Registries.TEST_FUNCTION,
        Create.MOD_ID
    );
    private static final ResourceKey<Consumer<GameTestHelper>> WATER_WHEEL_PRESS = ResourceKey.create(
        Registries.TEST_FUNCTION,
        Identifier.fromNamespaceAndPath(Create.MOD_ID, "water_wheel_drives_press")
    );
    private static final ResourceKey<Consumer<GameTestHelper>> BASIN_FLUID_SAVE = ResourceKey.create(
        Registries.TEST_FUNCTION,
        Identifier.fromNamespaceAndPath(Create.MOD_ID, "basin_fluid_survives_save")
    );

    static {
        FUNCTIONS.register(WATER_WHEEL_PRESS.identifier().getPath(), () -> CreateGameTests::waterWheelDrivesPress);
        FUNCTIONS.register(BASIN_FLUID_SAVE.identifier().getPath(), () -> CreateGameTests::basinFluidSurvivesSave);
    }

    private CreateGameTests() {
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(CreateGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
            Identifier.fromNamespaceAndPath(Create.MOD_ID, "default")
        );
        event.registerTest(
            WATER_WHEEL_PRESS.identifier(),
            new FunctionGameTestInstance(
                WATER_WHEEL_PRESS,
                new TestData<>(environment, Identifier.fromNamespaceAndPath(Create.MOD_ID, "gametest/empty_9x7x5"), 1200, 0, true)
            )
        );
        event.registerTest(
            BASIN_FLUID_SAVE.identifier(),
            new FunctionGameTestInstance(
                BASIN_FLUID_SAVE,
                new TestData<>(environment, Identifier.fromNamespaceAndPath(Create.MOD_ID, "gametest/empty_9x7x5"), 100, 0, true)
            )
        );
    }

    /**
     * A water channel runs over a small water wheel, which turns a mechanical press through a shaft. The press must
     * turn the iron ingot on the depot below it into an iron sheet.
     */
    private static void waterWheelDrivesPress(GameTestHelper helper) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        // Channel at y=4 along z, walled in on x, with stone under it everywhere except over the wheel
        for (int z = 0; z < 5; z++) {
            helper.setBlock(new BlockPos(1, 4, z), Blocks.GLASS);
            helper.setBlock(new BlockPos(3, 4, z), Blocks.GLASS);
            if (z != 2) {
                helper.setBlock(new BlockPos(2, 3, z), Blocks.STONE);
            }
        }
        BlockPos wheel = new BlockPos(2, 3, 2);
        BlockPos press = new BlockPos(4, 3, 2);
        BlockPos depot = new BlockPos(4, 1, 2);
        helper.setBlock(wheel, AllBlocks.WATER_WHEEL.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(3, 3, 2), AllBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        helper.setBlock(press, AllBlocks.MECHANICAL_PRESS.defaultBlockState().setValue(HorizontalKineticBlock.HORIZONTAL_FACING, Direction.EAST));
        helper.setBlock(depot, AllBlocks.DEPOT);
        helper.setBlock(new BlockPos(2, 4, 0), Blocks.WATER);

        helper.runAfterDelay(
            5, () -> {
                DepotBlockEntity depotBe = helper.getBlockEntity(depot, DepotBlockEntity.class);
                depotBe.depotBehaviour.setHeldItem(new TransportedItemStack(new ItemStack(Items.IRON_INGOT)));
            }
        );
        helper.succeedWhen(() -> {
            KineticBlockEntity wheelBe = helper.getBlockEntity(wheel, KineticBlockEntity.class);
            KineticBlockEntity pressBe = helper.getBlockEntity(press, KineticBlockEntity.class);
            ItemStack held = helper.getBlockEntity(depot, DepotBlockEntity.class).depotBehaviour.getHeldItemStack();
            String state = "depot holds " + held + ", wheel speed " + wheelBe.getSpeed() + ", press speed " + pressBe.getSpeed();
            helper.assertTrue(pressBe.getSpeed() != 0 && held.is(AllItems.IRON_SHEET), state);
            Create.LOGGER.info("water_wheel_drives_press passed at tick {}: {}", helper.getTick(), state);
        });
    }

    /**
     * A basin holding lava (as after create:mixing/lava_from_cobble) must save and load its tank. Create caps a stored
     * fluid with the fluid_max_capacity component, and on 26.3 a stale Optional in the component patch made saving
     * throw a ClassCastException, so the basin lost its contents on every chunk save. Also checks the sync codec.
     */
    private static void basinFluidSurvivesSave(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 1, 2);
        helper.setBlock(pos, AllBlocks.BASIN);
        helper.runAfterDelay(
            2, () -> {
                BasinBlockEntity basin = helper.getBlockEntity(pos, BasinBlockEntity.class);
                RegistryAccess registries = helper.getLevel().registryAccess();
                int inserted = basin.inputTank.getCapability().insert(new FluidStack(Fluids.LAVA, 4050));
                helper.assertValueEqual(inserted, 4050, "lava inserted into the basin");
                TankSegment tank = basin.inputTank.getPrimaryTank();
                helper.assertValueEqual(tank.getFluid().getMaxAmount(), basin.inputTank.getCapability().getMaxAmountPerStack(), "fluid_max_capacity");

                ProblemReporter.Collector problems = new ProblemReporter.Collector();
                TagValueOutput output = TagValueOutput.createWithContext(problems, registries);
                basin.saveWithFullMetadata(output);
                CompoundTag saved = output.buildResult();
                helper.assertTrue(problems.isEmpty(), "basin save reported problems: " + problems.getTreeReport());

                tank.setFluid(FluidStack.EMPTY);
                basin.loadWithComponents(TagValueInput.create(problems, registries, saved));
                helper.assertTrue(problems.isEmpty(), "basin load reported problems: " + problems.getTreeReport());
                FluidStack loaded = tank.getFluid();
                helper.assertTrue(loaded.isOf(Fluids.LAVA) && loaded.getAmount() == 4050, "basin tank after load: " + loaded + " from " + saved);

                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
                FluidStack.OPTIONAL_PACKET_CODEC.encode(buf, loaded);
                FluidStack synced = FluidStack.OPTIONAL_PACKET_CODEC.decode(buf);
                helper.assertTrue(FluidStack.areFluidsAndComponentsEqual(loaded, synced) && synced.getAmount() == 4050, "synced " + synced);
                Create.LOGGER.info("basin_fluid_survives_save passed: {}", saved);
                helper.succeed();
            }
        );
    }
}
