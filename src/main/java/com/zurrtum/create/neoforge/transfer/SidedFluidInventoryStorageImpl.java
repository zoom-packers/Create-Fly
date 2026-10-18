package com.zurrtum.create.neoforge.transfer;

import com.zurrtum.create.infrastructure.fluids.SidedFluidInventory;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Side-restricted view of a {@link FluidInventoryStorageImpl}, merging upstream's sided storage and sided slot wrapper.
 */
public class SidedFluidInventoryStorageImpl implements FluidInventoryStorage {
    private final FluidInventoryStorageImpl backingStorage;
    private final SidedFluidInventory inventory;
    private final Direction direction;
    private final int[] availableSlots;

    SidedFluidInventoryStorageImpl(FluidInventoryStorageImpl storage, Direction direction) {
        backingStorage = storage;
        inventory = (SidedFluidInventory) storage.inventory;
        this.direction = direction;
        availableSlots = inventory.getAvailableSlots(direction);
    }

    @Nullable
    private FluidInventorySlotWrapper getSlot(int index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException("Negative fluid slot index: " + index);
        }
        if (index >= availableSlots.length) {
            return null;
        }
        return backingStorage.getSlot(availableSlots[index]);
    }

    @Override
    public int size() {
        return availableSlots.length;
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
        if (slot == null || !inventory.canInsert(slot.slot, FluidInventoryStorage.getCachedStack(resource), direction)) {
            return 0;
        }
        return slot.insert(resource, amount, transaction);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        FluidInventorySlotWrapper slot = getSlot(index);
        if (slot == null || !inventory.canExtract(slot.slot, FluidInventoryStorage.getCachedStack(resource), direction)) {
            return 0;
        }
        return slot.extract(resource, amount, transaction);
    }

    @Override
    public String toString() {
        return "SidedFluidInventoryStorage[%s/%s]".formatted(
            FluidInventoryStorage.toString(inventory),
            direction.name()
        );
    }
}
