package com.zurrtum.create.neoforge.transfer;

import com.zurrtum.create.infrastructure.items.ItemInventory;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntSortedMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Exposes a NeoForge {@code ResourceHandler<ItemResource>} as a Create {@link ItemInventory}.
 * <p>
 * NeoForge port of upstream's Fabric {@code InventoryWrapper}. Every resource handler is index based, so only the
 * {@code Slotted} variant is needed, and handlers cannot report that they never support insertion or extraction, so
 * the {@code Filter} variant is gone too.
 */
public class ResourceHandlerInventoryWrapper implements ItemInventory {
    protected ResourceHandler<ItemResource> handler;
    protected int capacity;

    public ResourceHandlerInventoryWrapper(ResourceHandler<ItemResource> handler) {
        this.handler = handler;
        init();
    }

    public static ItemInventory of(ResourceHandler<ItemResource> handler) {
        return new ResourceHandlerInventoryWrapper(handler);
    }

    public ResourceHandler<ItemResource> getHandler() {
        return handler;
    }

    protected void init() {
        int max = 0;
        for (int i = 0, size = handler.size(); i < size; i++) {
            int capacity = handler.getCapacityAsInt(i, ItemResource.EMPTY);
            if (max < capacity) {
                max = capacity;
            }
        }
        capacity = max;
    }

    protected boolean isEmptySlot(int slot) {
        return handler.getAmountAsLong(slot) == 0 || handler.getResource(slot).isEmpty();
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
    public int count(ItemStack stack) {
        int maxAmount = stack.getCount();
        if (maxAmount == 0) {
            return 0;
        }
        return count(stack, maxAmount);
    }

    @Override
    public int count(ItemStack stack, int maxAmount) {
        if (stack.isEmpty() || maxAmount <= 0) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            return handler.extract(ItemResource.of(stack), maxAmount, transaction);
        }
    }

    @Override
    public ItemStack count(Predicate<ItemStack> predicate) {
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    continue;
                }
                ItemResource resource = handler.getResource(i);
                if (resource.test(predicate)) {
                    int extract = handler.extract(i, resource, handler.getAmountAsInt(i), transaction);
                    if (extract == 0) {
                        continue;
                    }
                    return resource.toStack(extract);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack count(Predicate<ItemStack> predicate, int maxAmount) {
        if (maxAmount <= 0) {
            return ItemStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    continue;
                }
                ItemResource resource = handler.getResource(i);
                if (resource.test(predicate)) {
                    int extract = handler.extract(resource, maxAmount, transaction);
                    if (extract == 0) {
                        continue;
                    }
                    return resource.toStack(extract);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack preciseCount(Predicate<ItemStack> predicate, int maxAmount) {
        if (maxAmount <= 0) {
            return ItemStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    continue;
                }
                ItemResource resource = handler.getResource(i);
                if (resource.test(predicate)) {
                    try (Transaction nested = Transaction.open(transaction)) {
                        int extract = handler.extract(resource, maxAmount, nested);
                        if (extract != maxAmount) {
                            continue;
                        }
                    }
                    return resource.toStack(maxAmount);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public int countAll(Predicate<ItemStack> predicate, int maxAmount) {
        if (maxAmount <= 0) {
            return 0;
        }
        int count = 0;
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    continue;
                }
                ItemResource resource = handler.getResource(i);
                if (resource.test(predicate)) {
                    count += handler.extract(i, resource, handler.getAmountAsInt(i), transaction);
                    if (count >= maxAmount) {
                        return maxAmount;
                    }
                }
            }
        }
        return count;
    }

    @Override
    public ItemStack countAny() {
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    continue;
                }
                ItemResource resource = handler.getResource(i);
                int extract = handler.extract(i, resource, handler.getAmountAsInt(i), transaction);
                if (extract == 0) {
                    continue;
                }
                return resource.toStack(extract);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack countAny(int maxAmount) {
        if (maxAmount <= 0) {
            return ItemStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    continue;
                }
                ItemResource resource = handler.getResource(i);
                int extract = handler.extract(resource, maxAmount, transaction);
                if (extract == 0) {
                    continue;
                }
                return resource.toStack(extract);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public int countSpace(ItemStack stack) {
        int maxAmount = stack.getCount();
        if (maxAmount == 0) {
            return 0;
        }
        return countSpace(stack, maxAmount);
    }

    @Override
    public int countSpace(ItemStack stack, int maxAmount) {
        if (stack.isEmpty() || maxAmount <= 0) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            return handler.insert(ItemResource.of(stack), maxAmount, transaction);
        }
    }

    @Override
    public boolean countSpace(List<ItemStack> stacks) {
        int listSize = stacks.size();
        if (listSize == 0) {
            return true;
        }
        if (listSize == 1) {
            ItemStack stack = stacks.getFirst();
            int count = stack.getCount();
            return countSpace(stack, count) == count;
        }
        Object2IntLinkedOpenCustomHashMap<ItemStack> map = new Object2IntLinkedOpenCustomHashMap<>(
            ITEM_STACK_HASH_STRATEGY);
        for (ItemStack stack : stacks) {
            map.merge(stack, stack.getCount(), Integer::sum);
        }
        Object2IntSortedMap.FastSortedEntrySet<ItemStack> entries = map.object2IntEntrySet();
        if (entries.size() == 1) {
            Object2IntMap.Entry<ItemStack> entry = entries.first();
            ItemStack stack = entry.getKey();
            int count = entry.getIntValue();
            return countSpace(stack, count) == count;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            ObjectIterator<Object2IntMap.Entry<ItemStack>> iterator = entries.fastIterator();
            do {
                Object2IntMap.Entry<ItemStack> entry = iterator.next();
                ItemStack stack = entry.getKey();
                int count = entry.getIntValue();
                if (stack.isEmpty() || count <= 0) {
                    continue;
                }
                int insert = handler.insert(ItemResource.of(stack), count, transaction);
                if (insert < count) {
                    return false;
                }
            } while (iterator.hasNext());
            return true;
        }
    }

    @Override
    public int countSpace(ItemStack stack, int maxAmount, int start, int end) {
        if (stack.isEmpty() || maxAmount <= 0) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            ItemResource resource = ItemResource.of(stack);
            int remaining = maxAmount;
            for (int i = start; i <= end; i++) {
                int insert = handler.insert(i, resource, remaining, transaction);
                if (insert == remaining) {
                    return maxAmount;
                }
                remaining -= insert;
            }
            return maxAmount - remaining;
        }
    }

    @Override
    public boolean countSpace(List<ItemStack> stacks, int start, int end) {
        int listSize = stacks.size();
        if (listSize == 0) {
            return true;
        }
        if (listSize == 1) {
            ItemStack stack = stacks.getFirst();
            int count = stack.getCount();
            return countSpace(stack, count, start, end) == count;
        }
        Object2IntLinkedOpenCustomHashMap<ItemStack> map = new Object2IntLinkedOpenCustomHashMap<>(
            ITEM_STACK_HASH_STRATEGY);
        for (ItemStack stack : stacks) {
            map.merge(stack, stack.getCount(), Integer::sum);
        }
        Object2IntSortedMap.FastSortedEntrySet<ItemStack> entries = map.object2IntEntrySet();
        if (entries.size() == 1) {
            Object2IntMap.Entry<ItemStack> entry = entries.first();
            ItemStack stack = entry.getKey();
            int count = entry.getIntValue();
            return countSpace(stack, count, start, end) == count;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            ObjectIterator<Object2IntMap.Entry<ItemStack>> iterator = entries.fastIterator();
            do {
                Object2IntMap.Entry<ItemStack> entry = iterator.next();
                ItemStack stack = entry.getKey();
                int count = entry.getIntValue();
                if (stack.isEmpty() || count <= 0) {
                    iterator.remove();
                    if (entries.isEmpty()) {
                        return true;
                    }
                    continue;
                }
                ItemResource resource = ItemResource.of(stack);
                int remaining = count;
                for (int i = start; i <= end; i++) {
                    remaining -= handler.insert(i, resource, remaining, transaction);
                    if (remaining == 0) {
                        break;
                    }
                }
                if (remaining == 0) {
                    iterator.remove();
                    if (entries.isEmpty()) {
                        return true;
                    }
                } else if (remaining != count) {
                    entry.setValue(remaining);
                }
            } while (iterator.hasNext());
            return false;
        }
    }

    @Override
    public int extract(ItemStack stack) {
        int maxAmount = stack.getCount();
        if (maxAmount == 0) {
            return 0;
        }
        return extract(stack, maxAmount);
    }

    @Override
    public int extract(ItemStack stack, int maxAmount) {
        if (stack.isEmpty() || maxAmount <= 0) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int extract = handler.extract(ItemResource.of(stack), maxAmount, transaction);
            transaction.commit();
            return extract;
        }
    }

    @Override
    public ItemStack extract(Predicate<ItemStack> predicate) {
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    continue;
                }
                ItemResource resource = handler.getResource(i);
                if (resource.test(predicate)) {
                    int extract = handler.extract(i, resource, handler.getAmountAsInt(i), transaction);
                    if (extract == 0) {
                        continue;
                    }
                    transaction.commit();
                    return resource.toStack(extract);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack extract(Predicate<ItemStack> predicate, int maxAmount) {
        if (maxAmount <= 0) {
            return ItemStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    continue;
                }
                ItemResource resource = handler.getResource(i);
                if (resource.test(predicate)) {
                    int extract = handler.extract(resource, maxAmount, transaction);
                    if (extract == 0) {
                        continue;
                    }
                    transaction.commit();
                    return resource.toStack(extract);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public List<ItemStack> extract(List<ItemStack> stacks) {
        int listSize = stacks.size();
        if (listSize == 0) {
            return stacks;
        }
        if (listSize == 1) {
            ItemStack stack = stacks.getFirst();
            int count = stack.getCount();
            int extract = extract(stacks.getFirst(), count);
            if (count == extract) {
                return List.of();
            }
            if (extract == 0) {
                return stacks;
            }
            return List.of(directCopy(stack, count - extract));
        }
        Object2IntLinkedOpenCustomHashMap<ItemStack> map = new Object2IntLinkedOpenCustomHashMap<>(
            ITEM_STACK_HASH_STRATEGY);
        for (ItemStack stack : stacks) {
            map.merge(stack, stack.getCount(), Integer::sum);
        }
        Object2IntSortedMap.FastSortedEntrySet<ItemStack> entries = map.object2IntEntrySet();
        if (entries.size() == 1) {
            Object2IntMap.Entry<ItemStack> entry = entries.first();
            ItemStack stack = entry.getKey();
            int count = entry.getIntValue();
            int extract = extract(stack, count);
            if (count == extract) {
                return List.of();
            }
            if (extract == 0) {
                return stacks;
            }
            return List.of(directCopy(stack, count - extract));
        }
        try (Transaction transaction = Transaction.openRoot()) {
            boolean dirty = false;
            Map<ItemStack, ItemResource> cache = new IdentityHashMap<>();
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    continue;
                }
                ObjectIterator<Object2IntMap.Entry<ItemStack>> iterator = entries.fastIterator();
                do {
                    Object2IntMap.Entry<ItemStack> entry = iterator.next();
                    ItemStack stack = entry.getKey();
                    int count = entry.getIntValue();
                    ItemResource resource = cache.computeIfAbsent(stack, ItemResource::of);
                    if (resource.isEmpty() || count <= 0) {
                        continue;
                    }
                    int extract = handler.extract(i, resource, count, transaction);
                    if (extract == 0) {
                        continue;
                    }
                    if (extract == count) {
                        iterator.remove();
                        if (entries.isEmpty()) {
                            transaction.commit();
                            return List.of();
                        }
                    } else {
                        entry.setValue(count - extract);
                    }
                    dirty = true;
                    break;
                } while (iterator.hasNext());
            }
            if (dirty) {
                List<ItemStack> result = new ArrayList<>();
                for (Object2IntMap.Entry<ItemStack> entry : entries) {
                    ItemStack stack = entry.getKey();
                    int count = entry.getIntValue();
                    if (stack.getCount() == count) {
                        result.add(stack);
                    } else {
                        result.add(directCopy(stack, count));
                    }
                }
                transaction.commit();
                return result;
            }
            return stacks;
        }
    }

    @Override
    public ItemStack extractAny() {
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    continue;
                }
                ItemResource resource = handler.getResource(i);
                int extract = handler.extract(i, resource, handler.getAmountAsInt(i), transaction);
                if (extract == 0) {
                    continue;
                }
                transaction.commit();
                return resource.toStack(extract);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack extractAny(int maxAmount) {
        if (maxAmount <= 0) {
            return ItemStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    continue;
                }
                ItemResource resource = handler.getResource(i);
                int extract = handler.extract(resource, maxAmount, transaction);
                if (extract == 0) {
                    continue;
                }
                transaction.commit();
                return resource.toStack(extract);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack extractAnyMax() {
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    continue;
                }
                ItemResource resource = handler.getResource(i);
                int extract = handler.extract(resource, resource.getMaxStackSize(), transaction);
                if (extract == 0) {
                    continue;
                }
                transaction.commit();
                return resource.toStack(extract);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack extractMax(Predicate<ItemStack> predicate) {
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    continue;
                }
                ItemResource resource = handler.getResource(i);
                if (resource.test(predicate)) {
                    int extract = handler.extract(resource, resource.getMaxStackSize(), transaction);
                    if (extract == 0) {
                        continue;
                    }
                    transaction.commit();
                    return resource.toStack(extract);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public int extractAll(Predicate<ItemStack> predicate, int maxAmount) {
        if (maxAmount <= 0) {
            return 0;
        }
        int remaining = maxAmount;
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    continue;
                }
                ItemResource resource = handler.getResource(i);
                if (resource.test(predicate)) {
                    int extract = handler.extract(i, resource, remaining, transaction);
                    if (extract == remaining) {
                        transaction.commit();
                        return maxAmount;
                    }
                    remaining -= extract;
                }
            }
            transaction.commit();
        }
        return maxAmount - remaining;
    }

    @Override
    public int forceInsert(ItemStack stack) {
        return insert(stack);
    }

    @Override
    public int forceInsert(ItemStack stack, int maxAmount) {
        return insert(stack, maxAmount);
    }

    @Override
    public boolean forcePreciseInsert(ItemStack stack) {
        return preciseInsert(stack);
    }

    @Override
    public boolean forcePreciseInsert(ItemStack stack, int maxAmount) {
        return preciseInsert(stack, maxAmount);
    }

    @Override
    public int getMaxStackSize() {
        return capacity;
    }

    @Override
    public int getContainerSize() {
        return handler.size();
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= getContainerSize()) {
            return ItemStack.EMPTY;
        }
        int amount = handler.getAmountAsInt(slot);
        if (amount == 0) {
            return ItemStack.EMPTY;
        }
        return handler.getResource(slot).toStack(amount);
    }

    @Override
    public int insert(ItemStack stack) {
        int maxAmount = stack.getCount();
        if (maxAmount == 0) {
            return 0;
        }
        return insert(stack, maxAmount);
    }

    @Override
    public int insert(ItemStack stack, int maxAmount) {
        if (stack.isEmpty() || maxAmount <= 0) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int insert = handler.insert(ItemResource.of(stack), maxAmount, transaction);
            transaction.commit();
            return insert;
        }
    }

    @Override
    public List<ItemStack> insert(List<ItemStack> stacks) {
        int listSize = stacks.size();
        if (listSize == 0) {
            return stacks;
        }
        if (listSize == 1) {
            ItemStack stack = stacks.getFirst();
            int count = stack.getCount();
            int insert = insert(stacks.getFirst(), count);
            if (count == insert) {
                return List.of();
            }
            if (insert == 0) {
                return stacks;
            }
            return List.of(directCopy(stack, count - insert));
        }
        Object2IntLinkedOpenCustomHashMap<ItemStack> map = new Object2IntLinkedOpenCustomHashMap<>(
            ITEM_STACK_HASH_STRATEGY);
        for (ItemStack stack : stacks) {
            map.merge(stack, stack.getCount(), Integer::sum);
        }
        Object2IntSortedMap.FastSortedEntrySet<ItemStack> entries = map.object2IntEntrySet();
        if (entries.size() == 1) {
            Object2IntMap.Entry<ItemStack> entry = entries.first();
            ItemStack stack = entry.getKey();
            int count = entry.getIntValue();
            int insert = insert(stack, count);
            if (count == insert) {
                return List.of();
            }
            if (insert == 0) {
                return stacks;
            }
            return List.of(directCopy(stack, count - insert));
        }
        try (Transaction transaction = Transaction.openRoot()) {
            boolean dirty = false;
            Map<ItemStack, ItemResource> cache = new IdentityHashMap<>();
            for (int i = 0, size = handler.size(); i < size; i++) {
                ObjectIterator<Object2IntMap.Entry<ItemStack>> iterator = entries.fastIterator();
                do {
                    Object2IntMap.Entry<ItemStack> entry = iterator.next();
                    ItemStack stack = entry.getKey();
                    int count = entry.getIntValue();
                    ItemResource resource = cache.computeIfAbsent(stack, ItemResource::of);
                    if (resource.isEmpty() || count <= 0) {
                        continue;
                    }
                    int insert = handler.insert(i, resource, count, transaction);
                    if (insert == 0) {
                        continue;
                    }
                    if (insert == count) {
                        iterator.remove();
                        if (entries.isEmpty()) {
                            transaction.commit();
                            return List.of();
                        }
                    } else {
                        entry.setValue(count - insert);
                    }
                    dirty = true;
                    break;
                } while (iterator.hasNext());
            }
            if (dirty) {
                List<ItemStack> result = new ArrayList<>();
                for (Object2IntMap.Entry<ItemStack> entry : entries) {
                    ItemStack stack = entry.getKey();
                    int count = entry.getIntValue();
                    if (stack.getCount() == count) {
                        result.add(stack);
                    } else {
                        result.add(directCopy(stack, count));
                    }
                }
                transaction.commit();
                return result;
            }
            return stacks;
        }
    }

    @Override
    public int insert(ItemStack stack, int maxAmount, int start, int end) {
        if (stack.isEmpty() || maxAmount <= 0) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            ItemResource resource = ItemResource.of(stack);
            int remaining = maxAmount;
            for (int i = start; i <= end; i++) {
                int insert = handler.insert(i, resource, remaining, transaction);
                if (insert == remaining) {
                    transaction.commit();
                    return maxAmount;
                }
                remaining -= insert;
            }
            transaction.commit();
            return maxAmount - remaining;
        }
    }

    @Override
    public List<ItemStack> insert(List<ItemStack> stacks, int start, int end) {
        int listSize = stacks.size();
        if (listSize == 0) {
            return stacks;
        }
        if (listSize == 1) {
            ItemStack stack = stacks.getFirst();
            int count = stack.getCount();
            int insert = insert(stacks.getFirst(), count, start, end);
            if (count == insert) {
                return List.of();
            }
            if (insert == 0) {
                return stacks;
            }
            return List.of(directCopy(stack, count - insert));
        }
        Object2IntLinkedOpenCustomHashMap<ItemStack> map = new Object2IntLinkedOpenCustomHashMap<>(
            ITEM_STACK_HASH_STRATEGY);
        for (ItemStack stack : stacks) {
            map.merge(stack, stack.getCount(), Integer::sum);
        }
        Object2IntSortedMap.FastSortedEntrySet<ItemStack> entries = map.object2IntEntrySet();
        if (entries.size() == 1) {
            Object2IntMap.Entry<ItemStack> entry = entries.first();
            ItemStack stack = entry.getKey();
            int count = entry.getIntValue();
            int insert = insert(stack, count, start, end);
            if (count == insert) {
                return List.of();
            }
            if (insert == 0) {
                return stacks;
            }
            return List.of(directCopy(stack, count - insert));
        }
        try (Transaction transaction = Transaction.openRoot()) {
            boolean dirty = false;
            ObjectIterator<Object2IntMap.Entry<ItemStack>> iterator = entries.fastIterator();
            do {
                Object2IntMap.Entry<ItemStack> entry = iterator.next();
                ItemStack stack = entry.getKey();
                int count = entry.getIntValue();
                if (stack.isEmpty() || count <= 0) {
                    continue;
                }
                ItemResource resource = ItemResource.of(stack);
                int remaining = count;
                for (int i = start; i <= end; i++) {
                    remaining -= handler.insert(i, resource, remaining, transaction);
                    if (remaining == 0) {
                        break;
                    }
                }
                if (remaining == 0) {
                    iterator.remove();
                    if (entries.isEmpty()) {
                        transaction.commit();
                        return List.of();
                    }
                    dirty = true;
                } else if (remaining != count) {
                    entry.setValue(remaining);
                    dirty = true;
                }
            } while (iterator.hasNext());
            if (dirty) {
                List<ItemStack> result = new ArrayList<>();
                for (Object2IntMap.Entry<ItemStack> entry : entries) {
                    ItemStack stack = entry.getKey();
                    int count = entry.getIntValue();
                    if (stack.getCount() == count) {
                        result.add(stack);
                    } else {
                        result.add(directCopy(stack, count));
                    }
                }
                transaction.commit();
                return result;
            }
            return stacks;
        }
    }

    @Override
    public int insertExist(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            List<Integer> emptys = new ArrayList<>();
            ItemResource resource = ItemResource.of(stack);
            int maxAmount = stack.getCount();
            int remaining = maxAmount;
            for (int i = 0, size = handler.size(); i < size; i++) {
                if (isEmptySlot(i)) {
                    emptys.add(i);
                } else {
                    int insert = handler.insert(i, resource, remaining, transaction);
                    if (insert == remaining) {
                        transaction.commit();
                        return maxAmount;
                    }
                    remaining -= insert;
                }
            }
            for (int i : emptys) {
                int insert = handler.insert(i, resource, remaining, transaction);
                if (insert == remaining) {
                    transaction.commit();
                    return maxAmount;
                }
                remaining -= insert;
            }
            transaction.commit();
            return maxAmount - remaining;
        }
    }

    @Override
    public int insertExist(ItemStack stack, int maxAmount) {
        if (stack.isEmpty() || maxAmount <= 0) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int insert = handler.insert(ItemResource.of(stack), maxAmount, transaction);
            transaction.commit();
            return insert;
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
    public java.util.Iterator<ItemStack> iterator() {
        return new Iterator(handler);
    }

    @Override
    public boolean preciseExtract(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int amount = stack.getCount();
            int extract = handler.extract(ItemResource.of(stack), amount, transaction);
            if (extract < amount) {
                return false;
            }
            transaction.commit();
            return true;
        }
    }

    @Override
    public ItemStack preciseExtract(Predicate<ItemStack> predicate, int maxAmount) {
        if (maxAmount <= 0) {
            return ItemStack.EMPTY;
        }
        for (int i = 0, size = handler.size(); i < size; i++) {
            if (isEmptySlot(i)) {
                continue;
            }
            ItemResource resource = handler.getResource(i);
            if (resource.test(predicate)) {
                try (Transaction transaction = Transaction.openRoot()) {
                    int extract = handler.extract(resource, maxAmount, transaction);
                    if (extract != maxAmount) {
                        continue;
                    }
                    transaction.commit();
                    return resource.toStack(maxAmount);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean preciseInsert(ItemStack stack) {
        int maxAmount = stack.getCount();
        if (maxAmount == 0) {
            return true;
        }
        return preciseInsert(stack, maxAmount);
    }

    @Override
    public boolean preciseInsert(ItemStack stack, int maxAmount) {
        if (stack.isEmpty() || maxAmount <= 0) {
            return true;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int insert = handler.insert(ItemResource.of(stack), maxAmount, transaction);
            if (insert < maxAmount) {
                return false;
            }
            transaction.commit();
            return true;
        }
    }

    @Override
    public boolean preciseInsert(List<ItemStack> stacks) {
        int listSize = stacks.size();
        if (listSize == 0) {
            return true;
        }
        if (listSize == 1) {
            return preciseInsert(stacks.getFirst());
        }
        Object2IntLinkedOpenCustomHashMap<ItemStack> map = new Object2IntLinkedOpenCustomHashMap<>(
            ITEM_STACK_HASH_STRATEGY);
        for (ItemStack stack : stacks) {
            map.merge(stack, stack.getCount(), Integer::sum);
        }
        Object2IntSortedMap.FastSortedEntrySet<ItemStack> entries = map.object2IntEntrySet();
        if (entries.size() == 1) {
            Object2IntMap.Entry<ItemStack> entry = entries.first();
            return preciseInsert(entry.getKey(), entry.getIntValue());
        }
        try (Transaction transaction = Transaction.openRoot()) {
            ObjectIterator<Object2IntMap.Entry<ItemStack>> iterator = entries.fastIterator();
            do {
                Object2IntMap.Entry<ItemStack> entry = iterator.next();
                ItemStack stack = entry.getKey();
                int count = entry.getIntValue();
                if (stack.isEmpty() || count <= 0) {
                    continue;
                }
                int insert = handler.insert(ItemResource.of(stack), count, transaction);
                if (insert < count) {
                    return false;
                }
            } while (iterator.hasNext());
            transaction.commit();
            return true;
        }
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= getContainerSize() || isEmptySlot(slot)) {
            return ItemStack.EMPTY;
        }
        ItemResource resource = handler.getResource(slot);
        int amount;
        try (Transaction transaction = Transaction.openRoot()) {
            amount = handler.extract(slot, resource, handler.getAmountAsInt(slot), transaction);
            transaction.commit();
        }
        return resource.toStack(amount);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= getContainerSize() || amount <= 0 || isEmptySlot(slot)) {
            return ItemStack.EMPTY;
        }
        ItemResource resource = handler.getResource(slot);
        try (Transaction transaction = Transaction.openRoot()) {
            amount = handler.extract(slot, resource, amount, transaction);
            transaction.commit();
        }
        return resource.toStack(amount);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= getContainerSize()) {
            return;
        }
        ItemResource resource = handler.getResource(slot);
        int currentAmount = handler.getAmountAsInt(slot);
        try (Transaction transaction = Transaction.openRoot()) {
            if (resource.isEmpty() || currentAmount == 0) {
                if (stack.isEmpty()) {
                    return;
                }
                handler.insert(slot, ItemResource.of(stack), stack.getCount(), transaction);
            } else if (resource.matches(stack)) {
                int amount = stack.getCount();
                if (amount == currentAmount) {
                    return;
                }
                if (amount > currentAmount) {
                    handler.insert(slot, resource, amount - currentAmount, transaction);
                } else {
                    handler.extract(slot, resource, currentAmount - amount, transaction);
                }
            } else {
                handler.extract(slot, resource, currentAmount, transaction);
                if (!stack.isEmpty()) {
                    handler.insert(slot, ItemResource.of(stack), stack.getCount(), transaction);
                }
            }
            transaction.commit();
        }
    }

    @Override
    public boolean update(Predicate<ItemStack> predicate, Function<ItemStack, ItemStack> update) {
        for (int i = 0, size = handler.size(); i < size; i++) {
            if (isEmptySlot(i)) {
                continue;
            }
            ItemResource resource = handler.getResource(i);
            if (resource.test(predicate)) {
                try (Transaction transaction = Transaction.openRoot()) {
                    int amount = handler.getAmountAsInt(i);
                    ItemStack replace = update.apply(resource.toStack(amount));
                    if (resource.matches(replace)) {
                        int count = replace.getCount();
                        if (count == amount) {
                            return true;
                        }
                        int change;
                        if (count < amount) {
                            change = handler.extract(i, resource, amount - count, transaction);
                        } else {
                            change = handler.insert(i, resource, count - amount, transaction);
                        }
                        if (change != 0) {
                            transaction.commit();
                            return true;
                        }
                    } else {
                        int extract = handler.extract(i, resource, amount, transaction);
                        if (extract != 0) {
                            if (replace.isEmpty()) {
                                transaction.commit();
                                return true;
                            }
                            int insert = handler.insert(
                                i,
                                ItemResource.of(replace),
                                replace.getCount(),
                                transaction
                            );
                            if (insert != 0) {
                                transaction.commit();
                                return true;
                            }
                        }
                        return false;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "ResourceHandlerInventoryWrapper[" + handler + "]";
    }

    static class Iterator implements java.util.Iterator<ItemStack> {
        private final ResourceHandler<ItemResource> handler;
        private final int size;
        private int index;

        public Iterator(ResourceHandler<ItemResource> handler) {
            this.handler = handler;
            size = handler.size();
        }

        @Override
        public boolean hasNext() {
            return index < size;
        }

        @Override
        public ItemStack next() {
            if (index >= size) {
                throw new NoSuchElementException();
            }
            int slot = index++;
            int amount = handler.getAmountAsInt(slot);
            if (amount == 0) {
                return ItemStack.EMPTY;
            }
            ItemResource resource = handler.getResource(slot);
            if (resource.isEmpty()) {
                return ItemStack.EMPTY;
            }
            return resource.toStack(amount);
        }
    }
}
