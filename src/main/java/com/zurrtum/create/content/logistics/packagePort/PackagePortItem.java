package com.zurrtum.create.content.logistics.packagePort;

import net.minecraft.world.item.context.BlockPlaceContext;
import com.zurrtum.create.infrastructure.packet.s2c.PackagePortPlacementRequestPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class PackagePortItem extends BlockItem {

    public PackagePortItem(Block pBlock, Properties pProperties) {
        super(pBlock, pProperties);
    }

    // NeoForge 26.3: BlockItem#updateCustomBlockEntityTag is static now; this runs at the same point
    @Override
    public void onBlockPlacedBy(
        BlockPlaceContext context,
        Level world,
        BlockPos pos,
        BlockState state,
        @Nullable Player player,
        ItemStack stack
    ) {
        if (!world.isClientSide() && player instanceof ServerPlayer sp) {
            sp.connection.send(new PackagePortPlacementRequestPacket(pos));
        }
    }

}
