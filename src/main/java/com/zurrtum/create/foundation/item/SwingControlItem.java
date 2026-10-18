package com.zurrtum.create.foundation.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IItemExtension;

// NeoForge: IItemExtension#onEntitySwing is called by LivingEntity#swing, so no mixin is needed
public interface SwingControlItem extends IItemExtension {
    @Override
    boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand);
}
