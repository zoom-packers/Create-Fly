package com.zurrtum.create.neoforge.transfer;

import com.google.common.collect.MapMaker;
import com.zurrtum.create.infrastructure.fluids.FluidInventory;
import com.zurrtum.create.infrastructure.fluids.SidedFluidInventory;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.RootCommitJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FluidInventoryStorageImpl implements FluidInventoryStorage {
    private static final Map<FluidInventory, FluidInventoryStorageImpl> WRAPPERS = new MapMaker().weakValues()
        .makeMap();

    public static FluidInventoryStorage of(FluidInventory inventory) {
        FluidInventoryStorageImpl storage = WRAPPERS.computeIfAbsent(inventory, FluidInventoryStorageImpl::new);
        storage.resizeSlotList();
        return storage;
    }

    public static FluidInventoryStorage of(FluidInventory inventory, @Nullable Direction direction) {
        FluidInventoryStorageImpl storage = WRAPPERS.computeIfAbsent(inventory, FluidInventoryStorageImpl::new);
        storage.resizeSlotList();
        return storage.getSidedWrapper(direction);
    }

    final FluidInventory inventory;
    final List<FluidInventorySlotWrapper> backingList;
    final RootCommitJournal markDirtyJournal;

    public FluidInventoryStorageImpl(FluidInventory inventory) {
        this.inventory = inventory;
        backingList = new ArrayList<>();
        markDirtyJournal = new RootCommitJournal(inventory::markDirty);
    }

    public FluidInventory getInventory() {
        return inventory;
    }

    private void resizeSlotList() {
        int inventorySize = inventory.size();
        while (backingList.size() < inventorySize) {
            backingList.add(new FluidInventorySlotWrapper(this, backingList.size()));
        }
    }

    @Nullable
    FluidInventorySlotWrapper getSlot(int index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException("Negative fluid slot index: " + index);
        }
        if (index >= inventory.size()) {
            return null;
        }
        resizeSlotList();
        return backingList.get(index);
    }

    private FluidInventoryStorage getSidedWrapper(@Nullable Direction direction) {
        if (inventory instanceof SidedFluidInventory && direction != null) {
            return new SidedFluidInventoryStorageImpl(this, direction);
        }
        return this;
    }

    @Override
    public int size() {
        return inventory.size();
    }

    @Override
    public FluidResource getResource(int index) {
        FluidInventorySlotWrapper slot = getSlot(index);
        return slot == null ? FluidResource.EMPTY : slot.getResource();
    }

    @Override
    public long getAmountAsLong(int index) {
        FluidInventorySlotWrapper slot = getSlot(index);
        return slot == null ? 0 : slot.getAmount();
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        FluidInventorySlotWrapper slot = getSlot(index);
        if (slot == null || !resource.isEmpty() && !slot.isValid(resource)) {
            return 0;
        }
        return slot.getCapacity(resource);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        TransferPreconditions.checkNonEmpty(resource);
        FluidInventorySlotWrapper slot = getSlot(index);
        return slot != null && slot.isValid(resource);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        FluidInventorySlotWrapper slot = getSlot(index);
        return slot == null ? 0 : slot.insert(resource, amount, transaction);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        FluidInventorySlotWrapper slot = getSlot(index);
        return slot == null ? 0 : slot.extract(resource, amount, transaction);
    }

    @Override
    public String toString() {
        return "FluidInventoryStorage[" + FluidInventoryStorage.toString(inventory) + "]";
    }
}
