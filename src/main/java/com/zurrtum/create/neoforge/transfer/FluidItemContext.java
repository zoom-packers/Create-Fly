package com.zurrtum.create.neoforge.transfer;

import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * An {@link ItemAccess} over a single detached stack, which may be replaced (e.g. empty bucket to filled bucket).
 * Nothing overflows: an item that does not stack with the current one is rejected.
 */
public class FluidItemContext extends SnapshotJournal<ItemStack> implements ItemAccess, AutoCloseable {
    private static final Deque<FluidItemContext> POOL = new ArrayDeque<>();
    private ItemStack stack = ItemStack.EMPTY;

    public static FluidItemContext of(ItemStack stack) {
        FluidItemContext ctx = POOL.pollFirst();
        if (ctx == null) {
            ctx = new FluidItemContext();
        }
        ctx.stack = stack;
        return ctx;
    }

    @Override
    public void close() {
        stack = ItemStack.EMPTY;
        POOL.addLast(this);
    }

    public ItemStack getStack() {
        return stack;
    }

    @Override
    public ItemResource getResource() {
        return ItemResource.of(stack);
    }

    @Override
    public int getAmount() {
        return stack.getCount();
    }

    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (stack.isEmpty() || resource.matches(stack)) {
            int insertedAmount = Math.min(amount, resource.getMaxStackSize() - stack.getCount());
            if (insertedAmount > 0) {
                updateSnapshots(transaction);
                if (stack.isEmpty()) {
                    stack = resource.toStack(insertedAmount);
                } else {
                    stack.grow(insertedAmount);
                }
                return insertedAmount;
            }
        }
        return 0;
    }

    @Override
    public int extract(ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (resource.matches(stack)) {
            int extracted = Math.min(stack.getCount(), amount);
            if (extracted > 0) {
                updateSnapshots(transaction);
                stack.shrink(extracted);
                return extracted;
            }
        }
        return 0;
    }

    @Override
    protected ItemStack createSnapshot() {
        ItemStack original = stack;
        stack = original.copy();
        return original;
    }

    @Override
    protected void revertToSnapshot(ItemStack snapshot) {
        stack = snapshot;
    }

    @Override
    protected void onRootCommit(ItemStack original) {
        ItemStack currentStack = stack;
        if (!original.isEmpty() && original.getItem() == currentStack.getItem()) {
            ((PatchedDataComponentMap) original.getComponents()).restorePatch(currentStack.getComponentsPatch());
            original.setCount(currentStack.getCount());
            stack = original;
        } else {
            original.setCount(0);
        }
    }
}
