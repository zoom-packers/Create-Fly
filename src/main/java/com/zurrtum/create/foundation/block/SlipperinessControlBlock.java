package com.zurrtum.create.foundation.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockExtension;
import org.jspecify.annotations.Nullable;

// NeoForge: implemented through IBlockExtension, which NeoForge already calls where upstream needed a mixin
public interface SlipperinessControlBlock extends IBlockExtension {
    float getSlipperiness(LevelReader world, BlockPos pos);

    @Override
    default float getFriction(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        return getSlipperiness(level, pos);
    }
}
