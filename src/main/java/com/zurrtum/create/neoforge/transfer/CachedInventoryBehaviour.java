package com.zurrtum.create.neoforge.transfer;

import com.zurrtum.create.api.behaviour.BlockEntityBehaviour;
import com.zurrtum.create.foundation.blockEntity.SmartBlockEntity;
import com.zurrtum.create.foundation.blockEntity.behaviour.BehaviourType;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

public class CachedInventoryBehaviour<T extends SmartBlockEntity> extends BlockEntityBehaviour<T> {
    public static final BehaviourType<CachedInventoryBehaviour<?>> TYPE = new BehaviourType<>();
    private final Function<T, @Nullable Container> factory;
    private Function<@Nullable Direction, @Nullable ResourceHandler<ItemResource>> getter;

    public CachedInventoryBehaviour(T be, Function<T, @Nullable Container> factory) {
        super(be);
        this.factory = factory;
        getter = this::firstGet;
    }

    public static @Nullable <T extends SmartBlockEntity> ResourceHandler<ItemResource> get(T be, @Nullable Direction side) {
        CachedInventoryBehaviour<?> behaviour = be.getBehaviour(TYPE);
        return behaviour == null ? null : behaviour.get(side);
    }

    @Nullable
    public ResourceHandler<ItemResource> get(@Nullable Direction side) {
        return getter.apply(side);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private ResourceHandler<ItemResource> firstGet(@Nullable Direction direction) {
        Container inventory = factory.apply(blockEntity);
        if (inventory == null) {
            return null;
        }
        ResourceHandler<ItemResource> storage = VanillaContainerWrapper.of(inventory);
        NeoForgeTransfer.registerWrapper(storage, inventory);
        if (inventory instanceof WorldlyContainer worldlyContainer) {
            @Nullable ResourceHandler<ItemResource>[] sides = new ResourceHandler[6];
            getter = side -> {
                if (side == null) {
                    return storage;
                }
                int i = side.get3DDataValue();
                ResourceHandler<ItemResource> sideStorage = sides[i];
                if (sideStorage == null) {
                    sideStorage = sides[i] = new WorldlyContainerWrapper(worldlyContainer, side);
                }
                return sideStorage;
            };
        } else {
            getter = _ -> storage;
        }
        return getter.apply(direction);
    }

    public void reset() {
        getter = this::firstGet;
        blockEntity.invalidateCapabilities();
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }
}
