package com.zurrtum.create.api.entity;

import com.zurrtum.create.infrastructure.player.FakePlayerEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.minecraft.world.entity.Entity;

public interface FakePlayerHandler {
    // NeoForge: always use the platform fake player, so protection mods recognise deployers
    boolean FABRIC = true;

    static boolean has(Entity player) {
        if (FABRIC && player instanceof FakePlayer) {
            return true;
        }
        return player instanceof FakePlayerEntity;
    }
}
