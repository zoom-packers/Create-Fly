package com.zurrtum.create.foundation.utility;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.component.BlockTransformers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.DataMapHooks;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * 26.3 replaced AxeItem's stripping table with the data-driven minecraft:axe block transformer.
 */
public final class BlockTransformHelper {
    private BlockTransformHelper() {
    }

    /**
     * Replaces {@code AxeItem#getStripped}: the state an axe would strip or scrape the block at pos to, ignoring
     * wax removal.
     */
    public static Optional<BlockState> getStripped(Level level, BlockPos pos) {
        Holder<BlockTransformer> axe = level.registryAccess().lookupOrThrow(Registries.BLOCK_TRANSFORMER)
            .get(BlockTransformers.AXE).orElse(null);
        if (axe == null) {
            return Optional.empty();
        }
        for (BlockTransformer.BlockTransformData data : DataMapHooks.getAllTransformers(axe)) {
            if (data.sound().value() == SoundEvents.AXE_WAX_OFF.value()) {
                continue;
            }
            @Nullable BlockState state = data.blockStateProvider().value().getOptionalState(level, level.getRandom(), pos);
            if (state != null) {
                return Optional.of(state);
            }
        }
        return Optional.empty();
    }
}
