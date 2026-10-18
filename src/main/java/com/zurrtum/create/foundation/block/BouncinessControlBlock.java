package com.zurrtum.create.foundation.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockExtension;

// NeoForge: implemented through IBlockExtension, which NeoForge already calls where upstream needed a mixin
public interface BouncinessControlBlock extends IBlockExtension {
    boolean skipBounciness(BlockState state);

    @Override
    default float getBounceRestitution(Level level, BlockPos pos, BlockState blockState, Entity entity) {
        return skipBounciness(blockState) ? 0 : IBlockExtension.super.getBounceRestitution(level, pos, blockState, entity);
    }
}
