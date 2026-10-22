package com.zurrtum.create.client.mixin;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

// 26.3: lets Flywheel look a texture up without loading it inside a render pass (PendingTextures)
@Mixin(TextureManager.class)
public interface TextureManagerAccessor {
    @Accessor("byPath")
    Map<Identifier, AbstractTexture> create$getByPath();
}
