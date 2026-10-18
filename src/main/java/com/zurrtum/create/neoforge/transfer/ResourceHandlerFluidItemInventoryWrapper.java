package com.zurrtum.create.neoforge.transfer;

import com.zurrtum.create.infrastructure.fluids.FluidItemInventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * NeoForge port of upstream's Fabric {@code FluidItemInventoryWrapper}: an item fluid handler, queried with a
 * {@link FluidItemContext}, exposed as a Create {@link FluidItemInventory}.
 */
public class ResourceHandlerFluidItemInventoryWrapper extends ResourceHandlerFluidInventoryWrapper implements FluidItemInventory {
    private static final Deque<ResourceHandlerFluidItemInventoryWrapper> POOL = new ArrayDeque<>();
    private FluidItemContext context;

    private ResourceHandlerFluidItemInventoryWrapper(ResourceHandler<FluidResource> handler, FluidItemContext context) {
        super(handler);
        this.context = context;
    }

    public static FluidItemInventory of(ResourceHandler<FluidResource> handler, FluidItemContext context) {
        ResourceHandlerFluidItemInventoryWrapper inventory = POOL.pollFirst();
        if (inventory == null) {
            inventory = new ResourceHandlerFluidItemInventoryWrapper(handler, context);
        } else {
            inventory.handler = handler;
            inventory.context = context;
            inventory.init();
        }
        return inventory;
    }

    @Override
    public void close() {
        context.close();
        handler = null;
        context = null;
        POOL.addLast(this);
    }

    @Override
    public ItemStack getContainer() {
        return context.getStack();
    }

    @Override
    public boolean isEmpty() {
        return context.getStack().isEmpty();
    }
}
