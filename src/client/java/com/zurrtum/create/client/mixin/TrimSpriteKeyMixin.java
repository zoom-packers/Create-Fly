package com.zurrtum.create.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.zurrtum.create.content.equipment.armor.AllEquipmentAssetKeys;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

import static com.zurrtum.create.Create.MOD_ID;

@Mixin(targets = "net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer$TrimTextureKey")
public class TrimSpriteKeyMixin {
    @Final
    @Shadow
    private EquipmentClientInfo equipmentInfo;

    @ModifyExpressionValue(method = "getOrPrepareTexture(Lnet/minecraft/client/resources/palette/PalettedTextureManager;)Lnet/minecraft/client/resources/palette/PalettedTextureManager$Handle;", at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/Identifier;withPath(Ljava/util/function/UnaryOperator;)Lnet/minecraft/resources/Identifier;"))
    private Identifier getTexture(Identifier id) {
        if (id.getNamespace().equals("minecraft") && create$isCardboard()) {
            return Identifier.fromNamespaceAndPath(MOD_ID, id.getPath());
        }
        return id;
    }

    @Unique
    private boolean create$isCardboard() {
        Identifier cardboard = AllEquipmentAssetKeys.CARDBOARD.identifier();
        for (List<EquipmentClientInfo.Layer> layers : equipmentInfo.layers().values()) {
            for (EquipmentClientInfo.Layer layer : layers) {
                if (layer.textureId().equals(cardboard)) {
                    return true;
                }
            }
        }
        return false;
    }
}
