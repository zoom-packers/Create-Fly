package com.zurrtum.create.mixin;

import com.zurrtum.create.Create;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MixinPlugin implements IMixinConfigPlugin {
    private List<String> mixins;

    @Override
    public void onLoad(String mixinPackage) {
        mixins = new ArrayList<>();
        // NeoForge: registration happens in vanilla bootstrap, as on Fabric without Fabric API
        Create.Lazy = false;
        //        if (FabricLoader.getInstance().isModLoaded("architectury")) {
        //            mixins.add("ArchitecturyMixin");
        //        }
        // NeoForge prefixes modded registry directories with the namespace; Create's data uses bare paths
        mixins.add("RegistriesMixin");
        {
            mixins.add("CreativeModeTabMixin");
            mixins.add("CreativeModeTabsMixin");
            mixins.add("SavedDataStorageMixin");
            // NeoForge: IngredientMixin is replaced by neoforge:components (DataComponentIngredient)
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return mixins;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
