package com.zurrtum.create.infrastructure.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import static com.zurrtum.create.Create.MOD_ID;

public class AllPlacementModifiers {
    // 26.3: the placement modifier type registry holds codecs
    public static final MapCodec<ConfigPlacementFilter> CONFIG_FILTER = Registry.register(
        BuiltInRegistries.PLACEMENT_MODIFIER_TYPE,
        Identifier.fromNamespaceAndPath(MOD_ID, "config_filter"),
        ConfigPlacementFilter.CODEC
    );

    public static void register() {
    }
}
