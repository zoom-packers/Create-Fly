package com.zurrtum.create.neoforge.transfer;

import com.zurrtum.create.AllDataComponents;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A single Create {@link FluidStack} slot. The stack is stored in droplets, the methods take and return millibuckets.
 */
public abstract class SingleFluidStackStorage extends SnapshotJournal<FluidStack> {
    protected abstract FluidStack getStack();

    protected abstract void setStack(FluidStack stack);

    protected boolean canInsert(FluidResource resource) {
        return true;
    }

    protected boolean canExtract(FluidResource resource) {
        return true;
    }

    public int getCapacity(FluidResource insert, FluidStack stack) {
        return stack.getMaxAmount();
    }

    public FluidResource getResource() {
        return FluidInventoryStorage.toResource(getStack());
    }

    public int getAmount() {
        return FluidInventoryStorage.toMbFloor(getStack().getAmount());
    }

    public long getCapacity(FluidResource resource) {
        return FluidInventoryStorage.toMbFloor(getCapacity(resource, getStack()));
    }

    public int insert(FluidResource resource, int maxAmount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, maxAmount);

        FluidStack currentStack = getStack();

        if ((currentStack.isEmpty() || FluidInventoryStorage.matches(resource, currentStack)) && canInsert(resource)) {
            long space = (long) getCapacity(resource, currentStack) - currentStack.getAmount();
            int insertedAmount = (int) Math.min(maxAmount, space / FluidInventoryStorage.DROPLETS_PER_MB);

            if (insertedAmount > 0) {
                updateSnapshots(transaction);
                currentStack = getStack();
                int droplets = FluidInventoryStorage.toDroplets(insertedAmount);

                if (currentStack.isEmpty()) {
                    Integer capacity = currentStack.get(AllDataComponents.FLUID_MAX_CAPACITY);
                    currentStack = FluidInventoryStorage.toStack(resource, droplets);
                    if (capacity != null) {
                        currentStack.set(AllDataComponents.FLUID_MAX_CAPACITY, capacity);
                    }
                } else {
                    currentStack.increment(droplets);
                }

                setStack(currentStack);

                return insertedAmount;
            }
        }

        return 0;
    }

    public int extract(FluidResource resource, int maxAmount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, maxAmount);

        FluidStack currentStack = getStack();

        if (FluidInventoryStorage.matches(resource, currentStack) && canExtract(resource)) {
            int extracted = Math.min(FluidInventoryStorage.toMbFloor(currentStack.getAmount()), maxAmount);

            if (extracted > 0) {
                updateSnapshots(transaction);
                currentStack = getStack();
                currentStack.decrement(FluidInventoryStorage.toDroplets(extracted));
                setStack(currentStack);

                return extracted;
            }
        }

        return 0;
    }

    @Override
    protected FluidStack createSnapshot() {
        FluidStack original = getStack();
        setStack(original.copy());
        return original;
    }

    @Override
    protected void revertToSnapshot(FluidStack snapshot) {
        setStack(snapshot);
    }

    @Override
    public String toString() {
        return "SingleFluidStackStorage[" + getStack() + "]";
    }
}
