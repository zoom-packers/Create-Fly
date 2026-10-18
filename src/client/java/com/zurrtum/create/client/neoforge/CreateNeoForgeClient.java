package com.zurrtum.create.client.neoforge;

import com.zurrtum.create.Create;
import com.zurrtum.create.client.catnip.gui.render.GpuTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge client entrypoint, replacing the Fabric {@code client} entrypoint
 * ({@link com.zurrtum.create.client.Create#onInitializeClient()}).
 */
@Mod(value = Create.MOD_ID, dist = Dist.CLIENT)
public class CreateNeoForgeClient {
    public CreateNeoForgeClient(IEventBus modBus, ModContainer container) {
        new com.zurrtum.create.client.Create().onInitializeClient();
        // NeoForge 26.3: reload listeners can only be added through AddClientReloadListenersEvent
        modBus.addListener(CreateClientReloadListeners::register);
        NeoForge.EVENT_BUS.addListener(CreateClientInput::onKey);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre event) -> GpuTexture.closePending());
    }
}
