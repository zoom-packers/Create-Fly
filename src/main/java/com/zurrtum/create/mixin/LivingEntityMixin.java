package com.zurrtum.create.mixin;

import net.minecraft.util.Prediction;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.zurrtum.create.AllDamageTypes;
import com.zurrtum.create.AllSynchedDatas;
import com.zurrtum.create.content.equipment.armor.CardboardArmorHandler;
import com.zurrtum.create.content.equipment.armor.DivingBootsItem;
import com.zurrtum.create.content.equipment.armor.DivingHelmetItem;
import com.zurrtum.create.content.equipment.armor.NetheriteDivingHandler;
import com.zurrtum.create.content.kinetics.deployer.DeployerPlayer;
import com.zurrtum.create.foundation.block.LandingEffectControlBlock;
import com.zurrtum.create.foundation.block.ScaffoldingControlBlock;
import com.zurrtum.create.foundation.block.SlipperinessControlBlock;
import com.zurrtum.create.foundation.block.SoundControlBlock;
import com.zurrtum.create.foundation.item.SwingControlItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
    @Shadow
    public abstract @Nullable Player getLastHurtByPlayer();

    @Shadow
    public abstract ItemStack getItemInHand(InteractionHand hand);

    public LivingEntityMixin(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Inject(method = "collectEquipmentChanges(Ljava/util/Map;)Ljava/util/Map;", at = @At(value = "INVOKE", target = "Ljava/util/Map;entrySet()Ljava/util/Set;"))
    private void onLivingEquipmentChange(CallbackInfoReturnable<Map<EquipmentSlot, ItemStack>> cir) {
        if ((Object) this instanceof Player player) {
            CardboardArmorHandler.playerChangesEquipment(player);
            NetheriteDivingHandler.onEquipmentChange(player);
        }
    }

    @Inject(method = "travelInLava(Lnet/minecraft/world/phys/Vec3;DZD)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))
    private void setOnGround(
        Vec3 input,
        double baseGravity,
        boolean isFalling,
        double oldY,
        CallbackInfo ci,
        @Share("onGround") LocalBooleanRef onGround
    ) {
        if ((Object) this instanceof Player player) {
            onGround.set(player.onGround());
        }
    }

    @Inject(method = "travelInLava(Lnet/minecraft/world/phys/Vec3;DZD)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isInShallowFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private void onTravelInFluid(
        Vec3 input,
        double baseGravity,
        boolean isFalling,
        double oldY,
        CallbackInfo ci,
        @Share("onGround") LocalBooleanRef onGround
    ) {
        if ((Object) this instanceof Player player) {
            DivingBootsItem.onLavaTravel(player, onGround.get());
        }
    }

    @WrapOperation(method = "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean captureDrops(Level world, Entity entity, Operation<Boolean> original) {
        if (AllSynchedDatas.CRUSH_DROP.get(this)) {
            entity.setDeltaMovement(Vec3.ZERO);
        } else if (world instanceof ServerLevel) {
            Optional<List<ItemStack>> value = AllSynchedDatas.CAPTURE_DROPS.get(this);
            if (value.isPresent()) {
                value.get().add(((ItemEntity) entity).getItem());
                return true;
            }
        }
        return original.call(world, entity);
    }

    @Inject(method = "dropExperience(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"), cancellable = true)
    private void onDropExperience(ServerLevel level, Entity killer, CallbackInfo ci) {
        if (getLastHurtByPlayer() instanceof DeployerPlayer) {
            ci.cancel();
        }
    }

    @Inject(method = "dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
    private void onDropPre(
        ServerLevel level,
        DamageSource source,
        CallbackInfo ci,
        @Share("handler") LocalIntRef handler
    ) {
        if (source.is(AllDamageTypes.CRUSH)) {
            AllSynchedDatas.CRUSH_DROP.set(this, true);
            handler.set(1);
        } else if (source.getEntity() instanceof DeployerPlayer) {
            AllSynchedDatas.CAPTURE_DROPS.set(this, Optional.of(new ArrayList<>()));
            handler.set(2);
        }
    }

    @Inject(method = "dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("TAIL"))
    private void onDropPost(
        ServerLevel level,
        DamageSource source,
        CallbackInfo ci,
        @Share("handler") LocalIntRef handler
    ) {
        switch (handler.get()) {
            case 1 -> AllSynchedDatas.CRUSH_DROP.set(this, false);
            case 2 -> AllSynchedDatas.CAPTURE_DROPS.get(this).ifPresent(drops -> {
                Inventory inventory = ((DeployerPlayer) source.getEntity()).cast().getInventory();
                drops.forEach(drop -> inventory.placeItemBackInInventory(drop, Prediction.SERVER_ONLY));
                AllSynchedDatas.CAPTURE_DROPS.set(this, Optional.empty());
            });
        }
    }

    @WrapOperation(method = "checkFallDamage(DZLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;isAir()Z"))
    private boolean onLandingEffect(
        BlockState state,
        Operation<Boolean> original,
        @Local(argsOnly = true) BlockPos pos,
        @Local ServerLevel level,
        @Local(ordinal = 1) double power
    ) {
        if (original.call(state)) {
            return true;
        }
        if (state.getBlock() instanceof LandingEffectControlBlock block) {
            return block.addLandingEffects(state, level, pos, (LivingEntity) (Object) this, power);
        }
        return false;
    }

    @Inject(method = "getVisibilityPercent(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)D", at = @At("HEAD"), cancellable = true)
    private void getAttackDistanceScalingFactor(ServerLevel serverLevel, Entity targetingEntity, CallbackInfoReturnable<Double> cir) {
        if (CardboardArmorHandler.testForStealth(targetingEntity)) {
            cir.setReturnValue(0.0d);
        }
    }
}
