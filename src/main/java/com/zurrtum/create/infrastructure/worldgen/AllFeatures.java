package com.zurrtum.create.infrastructure.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import static com.zurrtum.create.Create.MOD_ID;

public class AllFeatures {
    // 26.3: the feature type registry holds codecs; feature instances are data (worldgen/feature)
    public static final MapCodec<LayeredOreFeature> LAYERED_ORE = Registry.register(
        BuiltInRegistries.FEATURE_TYPE,
        Identifier.fromNamespaceAndPath(MOD_ID, "layered_ore"),
        LayeredOreFeature.CODEC
    );

    public static void register() {
    }
}
