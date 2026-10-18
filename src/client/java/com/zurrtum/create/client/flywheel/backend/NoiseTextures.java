package com.zurrtum.create.client.flywheel.backend;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.zurrtum.create.client.flywheel.lib.util.ResourceUtil;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.UnknownNullability;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;

public class NoiseTextures {
    public static final Identifier NOISE_TEXTURE = ResourceUtil.rl("textures/flywheel/noise/blue.png");

    @UnknownNullability
    public static AbstractTexture BLUE_NOISE;

    public static void reload(ResourceManager manager) {
        if (BLUE_NOISE != null) {
            BLUE_NOISE.close();
            BLUE_NOISE = null;
        }
        var optional = manager.getResource(NOISE_TEXTURE);

        if (optional.isEmpty()) {
            return;
        }

        // 26.3 dropped NativeImage.read(Format, InputStream), images are always decoded as RGBA.
        try (var is = optional.get().open(); var rgba = NativeImage.read(is)) {
            int[] pixels = rgba.getPixels();
            var image = new NativeImage(NativeImage.Format.LUMINANCE, rgba.getWidth(), rgba.getHeight(), false);
            long ptr = image.getPointer();
            for (int i = 0; i < pixels.length; i++) {
                MemoryUtil.memPutByte(ptr + i, (byte) ARGB.red(pixels[i]));
            }
            BLUE_NOISE = new NoiseTexture(image);
        } catch (IOException e) {

        }
    }

    private static class NoiseTexture extends AbstractTexture {
        private final NativeImage pixels;

        public NoiseTexture(NativeImage image) {
            pixels = image;
            GpuDevice device = RenderSystem.getDevice();
            texture = device.createTexture(
                () -> "Flywheel Blue Noise",
                5,
                GpuFormat.R8_UNORM,
                pixels.getWidth(),
                pixels.getHeight(),
                1,
                1
            );
            sampler = RenderSystem.getSamplerCache()
                .getSampler(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.LINEAR, FilterMode.LINEAR, false);
            textureView = device.createTextureView(texture);
            device.createCommandEncoder().writeToTexture(texture, pixels);
        }

        @Override
        public void close() {
            pixels.close();
            super.close();
        }
    }
}
