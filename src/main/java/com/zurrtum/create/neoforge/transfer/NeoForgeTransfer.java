package com.zurrtum.create.neoforge.transfer;

import com.google.common.collect.MapMaker;
import com.zurrtum.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Registers Create's block entity capabilities. {@link com.zurrtum.create.AllTransfer#register()} runs during vanilla
 * bootstrap, long before {@link RegisterCapabilitiesEvent}, so it only records the registrations here.
 */
public final class NeoForgeTransfer {
    private static final List<Consumer<RegisterCapabilitiesEvent>> REGISTRATIONS = new ArrayList<>();
    // Item handlers created for a Create container, so lookups can return the container instead of wrapping it again
    private static final Map<ResourceHandler<ItemResource>, Container> WRAPPED_CONTAINERS = new MapMaker().weakKeys()
        .makeMap();

    private NeoForgeTransfer() {
    }

    public static <T extends SmartBlockEntity> void registerItem(
        BlockEntityType<T> type,
        ICapabilityProvider<T, @Nullable Direction, ResourceHandler<ItemResource>> provider
    ) {
        REGISTRATIONS.add(event -> event.registerBlockEntity(Capabilities.Item.BLOCK, type, provider));
    }

    public static <T extends SmartBlockEntity> void registerFluid(
        BlockEntityType<T> type,
        ICapabilityProvider<T, @Nullable Direction, ResourceHandler<FluidResource>> provider
    ) {
        REGISTRATIONS.add(event -> event.registerBlockEntity(Capabilities.Fluid.BLOCK, type, provider));
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        REGISTRATIONS.forEach(registration -> registration.accept(event));
    }

    static void registerWrapper(ResourceHandler<ItemResource> handler, Container container) {
        WRAPPED_CONTAINERS.put(handler, container);
    }

    @Nullable
    public static Container unwrap(ResourceHandler<ItemResource> handler) {
        return WRAPPED_CONTAINERS.get(handler);
    }
}
