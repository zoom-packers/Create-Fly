package com.zurrtum.create.client.mixin;

import com.zurrtum.create.client.catnip.gui.render.*;
import com.zurrtum.create.client.foundation.gui.render.*;
import com.zurrtum.create.client.ponder.foundation.render.SceneRenderState;
import com.zurrtum.create.client.ponder.foundation.render.SceneRenderer;
import com.zurrtum.create.client.ponder.foundation.render.TitleTextRenderState;
import com.zurrtum.create.client.ponder.foundation.render.TitleTextRenderer;
import net.minecraft.client.gui.render.GuiRenderer;
import net.neoforged.neoforge.client.gui.PictureInPictureRendererRegistration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.List;

@Mixin(GuiRenderer.class)
public class GuiRendererMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/gui/PictureInPictureRendererPool;createPools(Ljava/util/List;)Ljava/util/Map;"))
    private List<PictureInPictureRendererRegistration<?>> addRenderer(List<PictureInPictureRendererRegistration<?>> original) {
        List<PictureInPictureRendererRegistration<?>> renderers = new ArrayList<>(original);
        renderers.add(new PictureInPictureRendererRegistration<>(ItemTransformRenderState.class, ItemTransformElementRenderer::new));
        renderers.add(new PictureInPictureRendererRegistration<>(BlockTransformRenderState.class, BlockTransformElementRenderer::new));
        renderers.add(new PictureInPictureRendererRegistration<>(EntityBlockRenderState.class, EntityBlockRenderer::new));
        renderers.add(new PictureInPictureRendererRegistration<>(PartialRenderState.class, PartialElementRenderer::new));
        renderers.add(new PictureInPictureRendererRegistration<>(BlazeBurnerRenderState.class, BlazeBurnerElementRenderer::new));
        renderers.add(new PictureInPictureRendererRegistration<>(PressBasinRenderState.class, PressBasinRenderer::new));
        renderers.add(new PictureInPictureRendererRegistration<>(PressRenderState.class, PressRenderer::new));
        renderers.add(new PictureInPictureRendererRegistration<>(MixingBasinRenderState.class, MixingBasinRenderer::new));
        renderers.add(new PictureInPictureRendererRegistration<>(BasinBlazeBurnerRenderState.class, BasinBlazeBurnerRenderer::new));
        renderers.add(new PictureInPictureRendererRegistration<>(MillstoneRenderState.class, MillstoneRenderer::new));
        renderers.add(new PictureInPictureRendererRegistration<>(SawRenderState.class, SawRenderer::new));
        renderers.add(new PictureInPictureRendererRegistration<>(CrushWheelRenderState.class, CrushWheelRenderer::new));
        renderers.add(new PictureInPictureRendererRegistration<>(DeployerRenderState.class, DeployerRenderer::new));
        renderers.add(new PictureInPictureRendererRegistration<>(ManualBlockRenderState.class, ManualBlockRenderer::new));
        renderers.add(new PictureInPictureRendererRegistration<>(SpoutRenderState.class, SpoutRenderer::new));
        renderers.add(new PictureInPictureRendererRegistration<>(CrafterRenderState.class, CrafterRenderer::new));
        renderers.add(new PictureInPictureRendererRegistration<>(DrainRenderState.class, DrainRenderer::new));
        renderers.add(new PictureInPictureRendererRegistration<>(SandPaperRenderState.class, SandPaperRenderer::new));
        renderers.add(new PictureInPictureRendererRegistration<>(TitleTextRenderState.class, TitleTextRenderer::new));
        renderers.add(new PictureInPictureRendererRegistration<>(SceneRenderState.class, SceneRenderer::new));
        renderers.add(new PictureInPictureRendererRegistration<>(FanRenderState.class, FanRenderer::new));
        return renderers;
    }
}
