package com.zurrtum.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.zurrtum.create.content.contraptions.glue.SuperGlueHandler;
import com.zurrtum.create.content.equipment.symmetryWand.SymmetryHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class ItemStackMixin {

    // NeoForge: the server places items through CommonHooks#onPlaceItemIntoWorld
    @WrapOperation(method = "useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/CommonHooks;onPlaceItemIntoWorld(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult useOnBlock(UseOnContext context, Operation<InteractionResult> original) {
        BlockPlaceContext placementContext = ((ItemStack) (Object) this).getItem() instanceof BlockItem ? new BlockPlaceContext(context) : null;
        boolean consumable = ((ItemStack) (Object) this).components.has(DataComponents.CONSUMABLE);
        InteractionResult result = original.call(context);
        Player player = context.getPlayer();
        if (placementContext != null && player != null && result instanceof InteractionResult.Success success && success.wasItemInteraction()) {
            ServerLevel world = (ServerLevel) context.getLevel();
            BlockPos pos = placementContext.getClickedPos();
            SuperGlueHandler.glueListensForBlockPlacement(world, player, pos);
            if (!consumable) {
                SymmetryHandler.onBlockPlaced(world, player, pos, placementContext);
            }
        }
        return result;
    }
}
