package com.zurrtum.create.client.flywheel.backend.engine;

import com.zurrtum.create.client.flywheel.api.model.Model;
import com.zurrtum.create.client.mixin.TextureManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// 26.3: TextureManager#getTexture loads and uploads a texture it hasn't seen yet. Flywheel draws inside vanilla's open
// main render pass, and an upload there throws "Close the existing render pass before performing additional commands",
// so the backend falls back until Flywheel is off. Create's own visuals only use textures that are already loaded, but
// an addon's entity texture often isn't. Material textures are queued when an instancer is created and loaded by
// load(), which runs before the main pass opens. Draws only look textures up: one that still isn't loaded is queued
// and drawn with the missing texture for that frame.
public final class PendingTextures {
    private static final Set<Identifier> PENDING = ConcurrentHashMap.newKeySet();

    private PendingTextures() {
    }

    public static void queue(Model model) {
        for (Model.ConfiguredMesh mesh : model.meshes()) {
            PENDING.add(mesh.material().texture());
        }
    }

    /**
     * Load every queued texture. Call on the render thread, outside any render pass.
     */
    public static void load() {
        if (PENDING.isEmpty()) {
            return;
        }
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        for (Iterator<Identifier> it = PENDING.iterator(); it.hasNext(); ) {
            Identifier texture = it.next();
            it.remove();
            textureManager.getTexture(texture);
        }
    }

    /**
     * Look up a loaded texture without loading it, for use inside a render pass.
     */
    public static AbstractTexture get(Identifier texture) {
        Map<Identifier, AbstractTexture> loaded = ((TextureManagerAccessor) Minecraft.getInstance()
            .getTextureManager()).create$getByPath();
        AbstractTexture out = loaded.get(texture);
        if (out == null) {
            PENDING.add(texture);            out =loaded.get(MissingTextureAtlasSprite.getLocation());
        }
        return out;
    }
}
