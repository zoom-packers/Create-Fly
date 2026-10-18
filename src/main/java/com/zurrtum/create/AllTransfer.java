package com.zurrtum.create;

// NeoForge version: bridges Create's inventories to NeoForge's transfer API (ResourceHandler) instead of Fabric's.

import com.google.common.collect.MapMaker;
import com.zurrtum.create.api.behaviour.BlockEntityBehaviour;
import com.zurrtum.create.content.kinetics.belt.BeltBlock;
import com.zurrtum.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.zurrtum.create.foundation.blockEntity.SmartBlockEntity;
import com.zurrtum.create.foundation.item.ItemHelper;
import com.zurrtum.create.infrastructure.fluids.FluidInventory;
import com.zurrtum.create.infrastructure.fluids.FluidItemInventory;
import com.zurrtum.create.neoforge.transfer.CachedDirectionInventoryBehaviour;
import com.zurrtum.create.neoforge.transfer.CachedFluidInventoryBehaviour;
import com.zurrtum.create.neoforge.transfer.CachedInventoryBehaviour;
import com.zurrtum.create.neoforge.transfer.FluidInventoryStorageImpl;
import com.zurrtum.create.neoforge.transfer.FluidItemContext;
import com.zurrtum.create.neoforge.transfer.NeoForgeTransfer;
import com.zurrtum.create.neoforge.transfer.ResourceHandlerFluidInventoryWrapper;
import com.zurrtum.create.neoforge.transfer.ResourceHandlerFluidItemInventoryWrapper;
import com.zurrtum.create.neoforge.transfer.ResourceHandlerInventoryWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

public class AllTransfer {
    private static final Map<ResourceHandler<ItemResource>, Container> WRAPPERS_ITEM = new MapMaker().weakValues()
        .makeMap();
    private static final Map<ResourceHandler<FluidResource>, FluidInventory> WRAPPERS_FLUID = new MapMaker().weakValues()
        .makeMap();

    private static Container wrapItem(ResourceHandler<ItemResource> inventory) {
        Container container = NeoForgeTransfer.unwrap(inventory);
        if (container != null) {
            return container;
        }
        return WRAPPERS_ITEM.computeIfAbsent(inventory, ResourceHandlerInventoryWrapper::of);
    }

    private static FluidInventory wrapFluid(ResourceHandler<FluidResource> inventory) {
        if (inventory instanceof FluidInventoryStorageImpl storage) {
            return storage.getInventory();
        }
        return WRAPPERS_FLUID.computeIfAbsent(inventory, ResourceHandlerFluidInventoryWrapper::of);
    }

    @Nullable
    public static Supplier<@Nullable Container> getCacheInventory(
        ServerLevel world,
        BlockPos pos,
        Direction direction,
        @Nullable BiPredicate<@Nullable BlockEntity, Direction> filter
    ) {
        BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction> cache = BlockCapabilityCache.create(
            Capabilities.Item.BLOCK,
            world,
            pos,
            direction
        );
        return () -> {
            ResourceHandler<ItemResource> inventory = cache.getCapability();
            if (inventory == null || filter != null && !filter.test(world.getBlockEntity(pos), direction)) {
                return null;
            }
            return wrapItem(inventory);
        };
    }

    @Nullable
    public static Container getInventory(
        Level world,
        BlockPos pos,
        @Nullable BlockState state,
        @Nullable BlockEntity blockEntity,
        @Nullable Direction direction
    ) {
        ResourceHandler<ItemResource> inventory = world.getCapability(
            Capabilities.Item.BLOCK,
            pos,
            state,
            blockEntity,
            direction
        );
        if (inventory == null) {
            return null;
        }
        return wrapItem(inventory);
    }

    public static boolean hasFluidInventory(
        Level world,
        BlockPos pos,
        @Nullable BlockState state,
        @Nullable BlockEntity blockEntity,
        @Nullable Direction direction
    ) {
        return world.getCapability(Capabilities.Fluid.BLOCK, pos, state, blockEntity, direction) != null;
    }

    @Nullable
    public static Supplier<@Nullable FluidInventory> getCacheFluidInventory(
        ServerLevel world,
        BlockPos pos,
        Direction direction
    ) {
        BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction> cache = BlockCapabilityCache.create(
            Capabilities.Fluid.BLOCK,
            world,
            pos,
            direction
        );
        return () -> {
            ResourceHandler<FluidResource> inventory = cache.getCapability();
            if (inventory == null) {
                return null;
            }
            return wrapFluid(inventory);
        };
    }

    @Nullable
    public static FluidInventory getFluidInventory(
        Level world,
        BlockPos pos,
        @Nullable BlockState state,
        @Nullable BlockEntity blockEntity,
        @Nullable Direction direction
    ) {
        ResourceHandler<FluidResource> inventory = world.getCapability(
            Capabilities.Fluid.BLOCK,
            pos,
            state,
            blockEntity,
            direction
        );
        if (inventory == null) {
            return null;
        }
        return wrapFluid(inventory);
    }

    public static boolean hasFluidInventory(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        FluidItemContext context = FluidItemContext.of(stack);
        boolean result = Capabilities.Fluid.ITEM.getCapability(stack, context) != null;
        context.close();
        return result;
    }

    @Nullable
    public static FluidItemInventory getFluidInventory(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        FluidItemContext context = FluidItemContext.of(stack);
        ResourceHandler<FluidResource> inventory = Capabilities.Fluid.ITEM.getCapability(stack, context);
        if (inventory == null) {
            context.close();
            return null;
        }
        return ResourceHandlerFluidItemInventoryWrapper.of(inventory, context);
    }

    private static <T extends SmartBlockEntity> void registerItemSide(
        BlockEntityType<T> type,
        Function<T, @Nullable Container> factory
    ) {
        BlockEntityBehaviour.add(type, (T be) -> new CachedInventoryBehaviour<>(be, factory));
        NeoForgeTransfer.registerItem(type, CachedInventoryBehaviour::get);
    }

    private static <T extends SmartBlockEntity> void registerItemSide(
        BlockEntityType<T> type,
        BiFunction<T, @Nullable Direction, @Nullable Container> factory
    ) {
        BlockEntityBehaviour.add(type, (T be) -> new CachedDirectionInventoryBehaviour<>(be, factory));
        NeoForgeTransfer.registerItem(type, CachedDirectionInventoryBehaviour::get);
    }

    private static <T extends SmartBlockEntity> void registerFluidSide(
        BlockEntityType<T> type,
        Function<T, @Nullable FluidInventory> factory
    ) {
        BlockEntityBehaviour.add(type, (T be) -> new CachedFluidInventoryBehaviour<>(be, factory));
        NeoForgeTransfer.registerFluid(type, CachedFluidInventoryBehaviour::get);
    }

    public static void register() {
        registerItemSide(AllBlockEntityTypes.DEPOT, be -> be.depotBehaviour.itemHandler);
        registerItemSide(AllBlockEntityTypes.WEIGHTED_EJECTOR, be -> be.depotBehaviour.itemHandler);
        registerItemSide(
            AllBlockEntityTypes.BELT, be -> {
                if (!BeltBlock.canTransportObjects(be.getBlockState())) {
                    return null;
                }
                if (!be.isRemoved() && be.itemHandler == null) {
                    be.initializeItemHandler();
                }
                return be.itemHandler;
            }
        );
        registerItemSide(AllBlockEntityTypes.MILLSTONE, be -> be.capability);
        registerItemSide(AllBlockEntityTypes.SAW, be -> be.inventory);
        registerItemSide(AllBlockEntityTypes.BASIN, be -> be.itemCapability);
        registerItemSide(
            AllBlockEntityTypes.ANDESITE_TUNNEL, be -> {
                if (be.cap == null) {
                    Level world = be.getLevel();
                    BlockPos pos = be.getBlockPos();
                    BlockState state = world.getBlockState(pos.below());
                    if (state.is(AllBlocks.BELT)) {
                        BlockEntity beBelow = world.getBlockEntity(pos.below());
                        if (beBelow != null) {
                            Container capBelow = ItemHelper.getInventory(
                                world,
                                pos.below(),
                                state,
                                beBelow,
                                Direction.UP
                            );
                            if (capBelow != null) {
                                be.cap = capBelow;
                            }
                        }
                    }
                }
                return be.cap;
            }
        );
        registerItemSide(AllBlockEntityTypes.BRASS_TUNNEL, be -> be.tunnelCapability);
        registerItemSide(AllBlockEntityTypes.CHUTE, be -> be.itemHandler);
        registerItemSide(AllBlockEntityTypes.SMART_CHUTE, be -> be.itemHandler);
        registerItemSide(AllBlockEntityTypes.PORTABLE_STORAGE_INTERFACE, be -> be.capability);
        registerItemSide(
            AllBlockEntityTypes.ITEM_DRAIN, (be, context) -> {
                if (context != null && context.getAxis().isHorizontal()) {
                    return be.itemHandlers.get(context);
                }
                return null;
            }
        );
        registerItemSide(
            AllBlockEntityTypes.DEPLOYER, be -> {
                if (be.invHandler == null) {
                    be.initHandler();
                }
                return be.invHandler;
            }
        );
        registerItemSide(AllBlockEntityTypes.CRUSHING_WHEEL_CONTROLLER, be -> be.inventory);
        registerItemSide(AllBlockEntityTypes.MECHANICAL_CRAFTER, MechanicalCrafterBlockEntity::getInvCapability);
        registerItemSide(AllBlockEntityTypes.CREATIVE_CRATE, be -> be.inv);
        registerItemSide(AllBlockEntityTypes.PACKAGER, be -> be.inventory);
        registerItemSide(AllBlockEntityTypes.REPACKAGER, be -> be.inventory);
        registerItemSide(AllBlockEntityTypes.PACKAGE_POSTBOX, be -> be.inventory);
        registerItemSide(AllBlockEntityTypes.PACKAGE_FROGPORT, be -> be.inventory);
        registerItemSide(AllBlockEntityTypes.TOOLBOX, be -> be.inventory);
        registerItemSide(AllBlockEntityTypes.TRACK_STATION, be -> be.depotBehaviour.itemHandler);
        registerFluidSide(
            AllBlockEntityTypes.FLUID_TANK, be -> {
                if (be.fluidCapability == null) {
                    be.refreshCapability();
                }
                return be.fluidCapability;
            }
        );
        registerFluidSide(AllBlockEntityTypes.BASIN, be -> be.fluidCapability);
        registerFluidSide(AllBlockEntityTypes.PORTABLE_FLUID_INTERFACE, be -> be.capability);
        registerFluidSide(AllBlockEntityTypes.HOSE_PULLEY, be -> be.handler);
        registerFluidSide(AllBlockEntityTypes.SPOUT, be -> be.tank.getCapability());
    }
}
