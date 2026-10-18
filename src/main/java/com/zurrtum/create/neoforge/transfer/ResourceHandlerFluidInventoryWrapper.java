package com.zurrtum.create.neoforge.transfer;

import com.zurrtum.create.infrastructure.fluids.FluidInventory;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntSortedMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

import static com.zurrtum.create.neoforge.transfer.FluidInventoryStorage.getCachedStack;
import static com.zurrtum.create.neoforge.transfer.FluidInventoryStorage.toDroplets;
import static com.zurrtum.create.neoforge.transfer.FluidInventoryStorage.toMbCeil;
import static com.zurrtum.create.neoforge.transfer.FluidInventoryStorage.toMbFloor;
import static com.zurrtum.create.neoforge.transfer.FluidInventoryStorage.toResource;

/**
 * Exposes a NeoForge {@code ResourceHandler<FluidResource>} as a Create {@link FluidInventory}.
 * <p>
 * NeoForge port of upstream's Fabric {@code FluidInventoryWrapper}. Create amounts are droplets and NeoForge amounts
 * are millibuckets: insertion rounds down and extraction rounds up to whole millibuckets, so fluid can be voided by
 * less than one millibucket but never created.
 */
public class ResourceHandlerFluidInventoryWrapper implements FluidInventory {
    protected ResourceHandler<FluidResource> handler;
    protected int capacity;

    public ResourceHandlerFluidInventoryWrapper(ResourceHandler<FluidResource> handler) {
        this.handler = handler;
        init();
    }

    public static FluidInventory of(ResourceHandler<FluidResource> handler) {
        return new ResourceHandlerFluidInventoryWrapper(handler);
    }

    public ResourceHandler<FluidResource> getHandler() {
        return handler;
    }

    protected void init() {
        long max = 0;
        for (int i = 0, size = handler.size(); i < size; i++) {
            long capacity = handler.getCapacityAsLong(i, FluidResource.EMPTY);
            if (max < capacity) {
                max = capacity;
            }
        }
        capacity = toDroplets(max);
    }

    protected boolean isEmptySlot(int slot) {
        return handler.getAmountAsLong(slot) == 0 || handler.getResource(slot).isEmpty();
    }

    protected FluidStack getStackInSlot(int slot) {
        int amount = handler.getAmountAsInt(slot);
        if (amount == 0) {
            return FluidStack.EMPTY;
        }
        return FluidInventoryStorage.toStack(handler.getResource(slot), toDroplets(amount));
    }

    // Extracting maxAmount droplets: round up to millibuckets, report at most maxAmount
    private static int extracted(int mb, int maxAmount) {
        return Math.min(toDroplets(mb), maxAmount);
    }

    @Override
    public void clearContent() {
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    continue;
                }
                handler.extract(i, handler.getResource(i), handler.getAmountAsInt(i), transaction);
            }
            transaction.commit();
        }
    }

    @Override
    public int count(FluidStack stack) {
        int maxAmount = stack.getAmount();
        if (maxAmount == 0) {
            return 0;
        }
        return count(stack, maxAmount);
    }

    @Override
    public int count(FluidStack stack, int maxAmount) {
        FluidResource resource = toResource(stack);
        int mb = toMbCeil(maxAmount);
        if (resource.isEmpty() || mb == 0) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            return extracted(handler.extract(resource, mb, transaction), maxAmount);
        }
    }

    @Override
    public FluidStack count(Predicate<FluidStack> predicate) {
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    continue;
                }
                FluidResource resource = handler.getResource(i);
                FluidStack stack = getCachedStack(resource);
                if (predicate.test(stack)) {
                    int extract = handler.extract(i, resource, handler.getAmountAsInt(i), transaction);
                    if (extract == 0) {
                        continue;
                    }
                    return stack.directCopy(toDroplets(extract));
                }
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack count(Predicate<FluidStack> predicate, int maxAmount) {
        int mb = toMbCeil(maxAmount);
        if (mb == 0) {
            return FluidStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    continue;
                }
                FluidResource resource = handler.getResource(i);
                FluidStack stack = getCachedStack(resource);
                if (predicate.test(stack)) {
                    int extract = handler.extract(resource, mb, transaction);
                    if (extract == 0) {
                        continue;
                    }
                    return stack.directCopy(extracted(extract, maxAmount));
                }
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack countAny() {
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    continue;
                }
                FluidResource resource = handler.getResource(i);
                int extract = handler.extract(i, resource, handler.getAmountAsInt(i), transaction);
                if (extract == 0) {
                    continue;
                }
                return getCachedStack(resource).directCopy(toDroplets(extract));
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack countAny(int maxAmount) {
        int mb = toMbCeil(maxAmount);
        if (mb == 0) {
            return FluidStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    continue;
                }
                FluidResource resource = handler.getResource(i);
                int extract = handler.extract(resource, mb, transaction);
                if (extract == 0) {
                    continue;
                }
                return getCachedStack(resource).directCopy(extracted(extract, maxAmount));
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public int countSpace(FluidStack stack) {
        int maxAmount = stack.getAmount();
        if (maxAmount == 0) {
            return 0;
        }
        return countSpace(stack, maxAmount);
    }

    @Override
    public int countSpace(FluidStack stack, int maxAmount) {
        FluidResource resource = toResource(stack);
        int mb = toMbFloor(maxAmount);
        if (resource.isEmpty() || mb == 0) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            return toDroplets(handler.insert(resource, mb, transaction));
        }
    }

    @Override
    public boolean countSpace(List<FluidStack> stacks) {
        if (stacks.isEmpty()) {
            return true;
        }
        Object2IntLinkedOpenCustomHashMap<FluidStack> map = new Object2IntLinkedOpenCustomHashMap<>(
            FLUID_STACK_HASH_STRATEGY);
        for (FluidStack stack : stacks) {
            map.merge(stack, stack.getAmount(), Integer::sum);
        }
        Object2IntSortedMap.FastSortedEntrySet<FluidStack> entries = map.object2IntEntrySet();
        try (Transaction transaction = Transaction.openRoot()) {
            ObjectIterator<Object2IntMap.Entry<FluidStack>> iterator = entries.fastIterator();
            do {
                Object2IntMap.Entry<FluidStack> entry = iterator.next();
                FluidResource resource = toResource(entry.getKey());
                int mb = toMbFloor(entry.getIntValue());
                if (resource.isEmpty() || mb == 0) {
                    return false;
                }
                int insert = handler.insert(resource, mb, transaction);
                if (insert < mb) {
                    return false;
                }
            } while (iterator.hasNext());
            return true;
        }
    }

    @Override
    public int extract(FluidStack stack) {
        int maxAmount = stack.getAmount();
        if (maxAmount == 0) {
            return 0;
        }
        return extract(stack, maxAmount);
    }

    @Override
    public int extract(FluidStack stack, int maxAmount) {
        FluidResource resource = toResource(stack);
        int mb = toMbCeil(maxAmount);
        if (resource.isEmpty() || mb == 0) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int extract = handler.extract(resource, mb, transaction);
            transaction.commit();
            return extracted(extract, maxAmount);
        }
    }

    @Override
    public FluidStack extract(Predicate<FluidStack> predicate) {
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    continue;
                }
                FluidResource resource = handler.getResource(i);
                FluidStack stack = getCachedStack(resource);
                if (predicate.test(stack)) {
                    int extract = handler.extract(i, resource, handler.getAmountAsInt(i), transaction);
                    if (extract == 0) {
                        continue;
                    }
                    transaction.commit();
                    return stack.directCopy(toDroplets(extract));
                }
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack extract(Predicate<FluidStack> predicate, int maxAmount) {
        int mb = toMbCeil(maxAmount);
        if (mb == 0) {
            return FluidStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    continue;
                }
                FluidResource resource = handler.getResource(i);
                FluidStack stack = getCachedStack(resource);
                if (predicate.test(stack)) {
                    int extract = handler.extract(resource, mb, transaction);
                    if (extract == 0) {
                        continue;
                    }
                    transaction.commit();
                    return stack.directCopy(extracted(extract, maxAmount));
                }
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack extractAny() {
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    continue;
                }
                FluidResource resource = handler.getResource(i);
                int extract = handler.extract(i, resource, handler.getAmountAsInt(i), transaction);
                if (extract == 0) {
                    continue;
                }
                transaction.commit();
                return getCachedStack(resource).directCopy(toDroplets(extract));
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack extractAny(int maxAmount) {
        int mb = toMbCeil(maxAmount);
        if (mb == 0) {
            return FluidStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    continue;
                }
                FluidResource resource = handler.getResource(i);
                int extract = handler.extract(i, resource, mb, transaction);
                if (extract == 0) {
                    continue;
                }
                transaction.commit();
                return getCachedStack(resource).directCopy(extracted(extract, maxAmount));
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public int forceInsert(FluidStack stack) {
        return insert(stack);
    }

    @Override
    public int forceInsert(FluidStack stack, int maxAmount) {
        return insert(stack, maxAmount);
    }

    @Override
    public boolean forcePreciseInsert(FluidStack stack) {
        return preciseInsert(stack);
    }

    @Override
    public boolean forcePreciseInsert(FluidStack stack, int maxAmount) {
        return preciseInsert(stack, maxAmount);
    }

    @Override
    public int getMaxAmountPerStack() {
        return capacity;
    }

    @Override
    public FluidStack getStack(int slot) {
        if (slot < 0 || slot >= size()) {
            return FluidStack.EMPTY;
        }
        return getStackInSlot(slot);
    }

    @Override
    public int insert(FluidStack stack) {
        int maxAmount = stack.getAmount();
        if (maxAmount == 0) {
            return 0;
        }
        return insert(stack, maxAmount);
    }

    @Override
    public int insert(FluidStack stack, int maxAmount) {
        FluidResource resource = toResource(stack);
        int mb = toMbFloor(maxAmount);
        if (resource.isEmpty() || mb == 0) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int insert = handler.insert(resource, mb, transaction);
            transaction.commit();
            return toDroplets(insert);
        }
    }

    @Override
    public List<FluidStack> insert(List<FluidStack> stacks) {
        int listSize = stacks.size();
        if (listSize == 0) {
            return stacks;
        }
        if (listSize == 1) {
            FluidStack stack = stacks.getFirst();
            int amount = stack.getAmount();
            int insert = insert(stacks.getFirst());
            if (amount == insert) {
                return List.of();
            }
            if (insert == 0) {
                return stacks;
            }
            return List.of(stack.directCopy(amount - insert));
        }
        Object2IntLinkedOpenCustomHashMap<FluidStack> map = new Object2IntLinkedOpenCustomHashMap<>(
            FLUID_STACK_HASH_STRATEGY);
        for (FluidStack stack : stacks) {
            map.merge(stack, stack.getAmount(), Integer::sum);
        }
        Object2IntSortedMap.FastSortedEntrySet<FluidStack> entries = map.object2IntEntrySet();
        if (entries.size() == 1) {
            Object2IntMap.Entry<FluidStack> entry = entries.first();
            FluidStack stack = entry.getKey();
            int amount = entry.getIntValue();
            int insert = insert(stack, amount);
            if (amount == insert) {
                return List.of();
            }
            if (insert == 0) {
                return stacks;
            }
            return List.of(stack.directCopy(amount - insert));
        }
        try (Transaction transaction = Transaction.openRoot()) {
            boolean dirty = false;
            ObjectIterator<Object2IntMap.Entry<FluidStack>> iterator = entries.fastIterator();
            do {
                Object2IntMap.Entry<FluidStack> entry = iterator.next();
                FluidResource resource = toResource(entry.getKey());
                int amount = entry.getIntValue();
                int mb = toMbFloor(amount);
                if (resource.isEmpty() || mb == 0) {
                    continue;
                }
                int insert = toDroplets(handler.insert(resource, mb, transaction));
                if (insert == amount) {
                    iterator.remove();
                    if (entries.isEmpty()) {
                        transaction.commit();
                        return List.of();
                    }
                    dirty = true;
                } else if (insert != 0) {
                    entry.setValue(amount - insert);
                    dirty = true;
                }
            } while (iterator.hasNext());
            if (dirty) {
                List<FluidStack> result = new ArrayList<>();
                for (Object2IntMap.Entry<FluidStack> entry : entries) {
                    FluidStack stack = entry.getKey();
                    int amount = entry.getIntValue();
                    if (stack.getAmount() == amount) {
                        result.add(stack);
                    } else {
                        result.add(stack.directCopy(amount));
                    }
                }
                transaction.commit();
                return result;
            }
            return stacks;
        }
    }

    @Override
    public int insertExist(FluidStack stack) {
        FluidResource resource = toResource(stack);
        int mb = toMbFloor(stack.getAmount());
        if (resource.isEmpty() || mb == 0) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            List<Integer> emptys = new ArrayList<>();
            int remaining = mb;
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    emptys.add(i);
                } else {
                    int insert = handler.insert(i, resource, remaining, transaction);
                    if (insert == remaining) {
                        transaction.commit();
                        return toDroplets(mb);
                    }
                    remaining -= insert;
                }
            }
            for (int i : emptys) {
                int insert = handler.insert(i, resource, remaining, transaction);
                if (insert == remaining) {
                    transaction.commit();
                    return toDroplets(mb);
                }
                remaining -= insert;
            }
            transaction.commit();
            return toDroplets(mb - remaining);
        }
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0, size = handler.size(); i < size; i++) {
            if (!isEmptySlot(i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public java.util.Iterator<FluidStack> iterator() {
        return new Iterator(this);
    }

    @Override
    public boolean preciseExtract(FluidStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        FluidResource resource = toResource(stack);
        int mb = toMbCeil(stack.getAmount());
        try (Transaction transaction = Transaction.openRoot()) {
            int extract = handler.extract(resource, mb, transaction);
            if (extract < mb) {
                return false;
            }
            transaction.commit();
            return true;
        }
    }

    @Override
    public FluidStack preciseExtract(Predicate<FluidStack> predicate, int maxAmount) {
        int mb = toMbCeil(maxAmount);
        if (mb == 0) {
            return FluidStack.EMPTY;
        }
        for (int i = 0, size = handler.size(); i < size; i++) {
            if (isEmptySlot(i)) {
                continue;
            }
            FluidResource resource = handler.getResource(i);
            FluidStack stack = getCachedStack(resource);
            if (predicate.test(stack)) {
                try (Transaction transaction = Transaction.openRoot()) {
                    int extract = handler.extract(resource, mb, transaction);
                    if (extract != mb) {
                        continue;
                    }
                    transaction.commit();
                    return stack.directCopy(maxAmount);
                }
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public boolean preciseInsert(FluidStack stack) {
        int maxAmount = stack.getAmount();
        if (maxAmount == 0) {
            return true;
        }
        return preciseInsert(stack, maxAmount);
    }

    @Override
    public boolean preciseInsert(FluidStack stack, int maxAmount) {
        FluidResource resource = toResource(stack);
        int mb = toMbFloor(maxAmount);
        if (resource.isEmpty() || mb == 0) {
            return false;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int insert = handler.insert(resource, mb, transaction);
            if (insert < mb) {
                return false;
            }
            transaction.commit();
            return true;
        }
    }

    @Override
    public boolean preciseInsert(List<FluidStack> stacks) {
        int listSize = stacks.size();
        if (listSize == 0) {
            return true;
        }
        if (listSize == 1) {
            return preciseInsert(stacks.getFirst());
        }
        Object2IntLinkedOpenCustomHashMap<FluidStack> map = new Object2IntLinkedOpenCustomHashMap<>(
            FLUID_STACK_HASH_STRATEGY);
        for (FluidStack stack : stacks) {
            map.merge(stack, stack.getAmount(), Integer::sum);
        }
        Object2IntSortedMap.FastSortedEntrySet<FluidStack> entries = map.object2IntEntrySet();
        if (entries.size() == 1) {
            Object2IntMap.Entry<FluidStack> entry = entries.first();
            return preciseInsert(entry.getKey(), entry.getIntValue());
        }
        try (Transaction transaction = Transaction.openRoot()) {
            ObjectIterator<Object2IntMap.Entry<FluidStack>> iterator = entries.fastIterator();
            do {
                Object2IntMap.Entry<FluidStack> entry = iterator.next();
                FluidResource resource = toResource(entry.getKey());
                int mb = toMbFloor(entry.getIntValue());
                if (resource.isEmpty() || mb == 0) {
                    return false;
                }
                int insert = handler.insert(resource, mb, transaction);
                if (insert < mb) {
                    return false;
                }
            } while (iterator.hasNext());
            transaction.commit();
            return true;
        }
    }

    @Override
    public FluidStack removeStack(int slot) {
        if (slot < 0 || slot >= size() || isEmptySlot(slot)) {
            return FluidStack.EMPTY;
        }
        FluidResource resource = handler.getResource(slot);
        int amount;
        try (Transaction transaction = Transaction.openRoot()) {
            amount = handler.extract(slot, resource, handler.getAmountAsInt(slot), transaction);
            transaction.commit();
        }
        return FluidInventoryStorage.toStack(resource, toDroplets(amount));
    }

    @Override
    public FluidStack removeStack(int slot, int amount) {
        int mb = toMbCeil(amount);
        if (slot < 0 || slot >= size() || mb == 0 || isEmptySlot(slot)) {
            return FluidStack.EMPTY;
        }
        FluidResource resource = handler.getResource(slot);
        int extract;
        try (Transaction transaction = Transaction.openRoot()) {
            extract = handler.extract(slot, resource, mb, transaction);
            transaction.commit();
        }
        return FluidInventoryStorage.toStack(resource, extracted(extract, amount));
    }

    @Override
    public void setStack(int slot, FluidStack stack) {
        if (slot < 0 || slot >= size()) {
            return;
        }
        FluidResource resource = handler.getResource(slot);
        int currentAmount = handler.getAmountAsInt(slot);
        int targetAmount = toMbFloor(stack.getAmount());
        try (Transaction transaction = Transaction.openRoot()) {
            if (resource.isEmpty() || currentAmount == 0) {
                FluidResource target = toResource(stack);
                if (target.isEmpty() || targetAmount == 0) {
                    return;
                }
                handler.insert(slot, target, targetAmount, transaction);
            } else if (FluidInventoryStorage.matches(resource, stack)) {
                if (targetAmount == currentAmount) {
                    return;
                }
                if (targetAmount > currentAmount) {
                    handler.insert(slot, resource, targetAmount - currentAmount, transaction);
                } else {
                    handler.extract(slot, resource, currentAmount - targetAmount, transaction);
                }
            } else {
                handler.extract(slot, resource, currentAmount, transaction);
                FluidResource target = toResource(stack);
                if (!target.isEmpty() && targetAmount > 0) {
                    handler.insert(slot, target, targetAmount, transaction);
                }
            }
            transaction.commit();
        }
    }

    @Override
    public int size() {
        return handler.size();
    }

    @Override
    public String toString() {
        return "ResourceHandlerFluidInventoryWrapper[" + handler + "]";
    }

    static class Iterator implements java.util.Iterator<FluidStack> {
        private final ResourceHandlerFluidInventoryWrapper inventory;
        private final int size;
        private int index;

        public Iterator(ResourceHandlerFluidInventoryWrapper inventory) {
            this.inventory = inventory;
            size = inventory.size();
        }

        @Override
        public boolean hasNext() {
            return index < size;
        }

        @Override
        public FluidStack next() {
            if (index >= size) {
                throw new NoSuchElementException();
            }
            return inventory.getStackInSlot(index++);
        }
    }
}
