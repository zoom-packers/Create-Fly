package com.zurrtum.create.foundation.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockExtension;

// NeoForge: implemented through IBlockExtension, which NeoForge already calls where upstream needed a mixin
public interface ResistanceControlBlock extends IBlockExtension {
    float getResistance(BlockGetter world, BlockPos pos);

    @Override
    default float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return getResistance(level, pos);
    }
}
