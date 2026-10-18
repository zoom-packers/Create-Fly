package com.zurrtum.create.neoforge;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.kinetics.base.DirectionalKineticBlock;
import com.zurrtum.create.content.kinetics.base.HorizontalKineticBlock;
import com.zurrtum.create.content.kinetics.base.KineticBlockEntity;
import com.zurrtum.create.content.kinetics.belt.transport.TransportedItemStack;
import com.zurrtum.create.content.logistics.depot.DepotBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
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

    static {
        FUNCTIONS.register(WATER_WHEEL_PRESS.identifier().getPath(), () -> CreateGameTests::waterWheelDrivesPress);
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
}
