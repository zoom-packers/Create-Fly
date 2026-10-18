package com.zurrtum.create.neoforge.transfer;

import com.zurrtum.create.AllDataComponents;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.Objects;

public class FluidInventorySlotWrapper extends SingleFluidStackStorage {
    private final FluidInventoryStorageImpl storage;
    final int slot;

    FluidInventorySlotWrapper(FluidInventoryStorageImpl storage, int slot) {
        this.storage = storage;
        this.slot = slot;
    }

    @Override
    protected FluidStack getStack() {
        return storage.inventory.getStack(slot);
    }

    @Override
    protected void setStack(FluidStack stack) {
        storage.inventory.setStack(slot, stack);
    }

    @Override
    public int insert(FluidResource resource, int maxAmount, TransactionContext transaction) {
        if (!canInsert(slot, FluidInventoryStorage.getCachedStack(resource))) {
            return 0;
        }

        return super.insert(resource, maxAmount, transaction);
    }

    private boolean canInsert(int slot, FluidStack stack) {
        return storage.inventory.isValid(slot, stack);
    }

    public boolean isValid(FluidResource resource) {
        return canInsert(slot, FluidInventoryStorage.getCachedStack(resource));
    }

    @Override
    public int getCapacity(FluidResource insert, FluidStack stack) {
        Integer capacity = stack.get(AllDataComponents.FLUID_MAX_CAPACITY);
        if (capacity != null) {
            return Math.min(storage.inventory.getMaxAmountPerStack(), capacity);
        }
        if (insert.isEmpty()) {
            return storage.inventory.getMaxAmountPerStack();
        }
        return storage.inventory.getMaxAmount(FluidInventoryStorage.getCachedStack(insert));
    }

    @Override
    public void updateSnapshots(TransactionContext transaction) {
        storage.markDirtyJournal.updateSnapshots(transaction);
        super.updateSnapshots(transaction);
    }

    @Override
    protected void onRootCommit(FluidStack original) {
        FluidStack currentStack = getStack();

        if (!original.isEmpty() && original.getFluid() == currentStack.getFluid()) {
            if (!Objects.equals(original.getComponentChanges(), currentStack.getComponentChanges())) {
                for (DataComponentType<?> type : original.getComponents().keySet()) {
                    original.set(type, null);
                }

                original.applyComponentsFrom(currentStack.getComponents());
            }

            original.setAmount(currentStack.getAmount());
            setStack(original);
        } else {
            original.setAmount(0);
        }
    }

    @Override
    public String toString() {
        return "InventorySlotWrapper[%s#%d]".formatted(FluidInventoryStorage.toString(storage.inventory), slot);
    }
}
