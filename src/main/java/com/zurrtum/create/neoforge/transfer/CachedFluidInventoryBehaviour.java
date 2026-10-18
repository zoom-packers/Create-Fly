package com.zurrtum.create.neoforge.transfer;

import com.zurrtum.create.api.behaviour.BlockEntityBehaviour;
import com.zurrtum.create.foundation.blockEntity.SmartBlockEntity;
import com.zurrtum.create.foundation.blockEntity.behaviour.BehaviourType;
import com.zurrtum.create.infrastructure.fluids.FluidInventory;
import com.zurrtum.create.infrastructure.fluids.SidedFluidInventory;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

public class CachedFluidInventoryBehaviour<T extends SmartBlockEntity> extends BlockEntityBehaviour<T> {
    public static final BehaviourType<CachedFluidInventoryBehaviour<?>> TYPE = new BehaviourType<>();
    private final Function<T, @Nullable FluidInventory> factory;
    private Function<@Nullable Direction, @Nullable ResourceHandler<FluidResource>> getter;

    public CachedFluidInventoryBehaviour(T be, Function<T, @Nullable FluidInventory> factory) {
        super(be);
        this.factory = factory;
        getter = this::firstGet;
    }

    public static @Nullable <T extends SmartBlockEntity> ResourceHandler<FluidResource> get(T be, @Nullable Direction side) {
        CachedFluidInventoryBehaviour<?> behaviour = be.getBehaviour(TYPE);
        return behaviour == null ? null : behaviour.get(side);
    }

    @Nullable
    public ResourceHandler<FluidResource> get(@Nullable Direction side) {
        return getter.apply(side);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private ResourceHandler<FluidResource> firstGet(@Nullable Direction direction) {
        FluidInventory inventory = factory.apply(blockEntity);
        if (inventory == null) {
            return null;
        }
        ResourceHandler<FluidResource> storage = FluidInventoryStorage.of(inventory, null);
        if (inventory instanceof SidedFluidInventory) {
            @Nullable ResourceHandler<FluidResource>[] sides = new ResourceHandler[6];
            getter = side -> {
                if (side == null) {
                    return storage;
                }
                int i = side.get3DDataValue();
                ResourceHandler<FluidResource> sideStorage = sides[i];
                if (sideStorage == null) {
                    sideStorage = sides[i] = FluidInventoryStorage.of(inventory, side);
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
