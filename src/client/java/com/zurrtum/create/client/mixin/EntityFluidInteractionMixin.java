package com.zurrtum.create.client.mixin;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.function.Predicate;
import com.llamalad7.mixinextras.sugar.Local;
import com.zurrtum.create.infrastructure.fluids.FlowableFluid;
import com.zurrtum.create.infrastructure.fluids.FluidInteractionPredicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityFluidInteraction.class)
public class EntityFluidInteractionMixin implements FluidInteractionPredicate {
    @Unique
    private boolean inModFluid;

    @Inject(method = "update(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;)Z", at = @At("HEAD"))
    private void clear(Entity entity, Predicate<?> typePushPredicate, CallbackInfoReturnable<Boolean> cir) {
        inModFluid = false;
    }

    @Inject(method = "update(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos$MutableBlockPos;getY()I"))
    private void update(Entity entity, Predicate<?> typePushPredicate, CallbackInfoReturnable<Boolean> cir, @Local FluidState fluidState) {
        if (!inModFluid) {
            inModFluid = fluidState.getType() instanceof FlowableFluid;
        }
    }

    @Override
    public boolean create$inModFluid() {
        return inModFluid;
    }
}
