package com.zurrtum.create.neoforge;

import com.zurrtum.create.Create;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * NeoForge requires every mod fluid to return a {@link FluidType}. Upstream's fluids are plain vanilla fluids, so
 * {@code FlowableFluid#getFluidType} looks the type up here by fluid id. Properties follow Create's NeoForge
 * originals.
 */
public final class CreateFluidTypes {
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(
        NeoForgeRegistries.FLUID_TYPES,
        Create.MOD_ID
    );
    private static final Map<String, Supplier<FluidType>> BY_NAME = new HashMap<>();

    static {
        type("honey", FluidType.Properties.create().viscosity(2000).density(1400).canSwim(false).canDrown(true));
        type("chocolate", FluidType.Properties.create().viscosity(1500).density(1400).canSwim(false).canDrown(true));
        type("tea", FluidType.Properties.create().viscosity(1500).density(1000).temperature(320));
        type("potion", FluidType.Properties.create().viscosity(1500).density(1000));
        type("milk", FluidType.Properties.create().viscosity(1500).density(1024));
    }

    private CreateFluidTypes() {
    }

    private static void type(String name, FluidType.Properties properties) {
        properties.descriptionId("fluid.create." + name).canExtinguish(true).supportsBoating(false);
        BY_NAME.put(name, TYPES.register(name, () -> new FluidType(properties)));
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    public static FluidType get(Fluid fluid) {
        Identifier id = BuiltInRegistries.FLUID.getKey(fluid);
        String name = id.getPath().startsWith("flowing_") ? id.getPath().substring("flowing_".length()) : id.getPath();
        Supplier<FluidType> type = BY_NAME.get(name);
        if (type == null) {
            throw new IllegalStateException("No NeoForge FluidType for " + id);
        }
        return type.get();
    }
}
