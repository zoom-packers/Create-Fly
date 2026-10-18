package com.zurrtum.create.client.neoforge;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zurrtum.create.client.Create;
import com.zurrtum.create.client.content.trains.schedule.hat.TrainHatInfoReloadListener;
import com.zurrtum.create.client.flywheel.backend.compile.FlwProgramsReloader;
import com.zurrtum.create.client.model.obj.ObjLoader;
import com.zurrtum.create.client.ponder.Ponder;
import com.zurrtum.create.foundation.utility.CreateResourceReloader;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

import static com.zurrtum.create.Create.MOD_ID;

// Replaces the Fabric-side MinecraftMixin registration: NeoForge rejects listeners added to the resource manager directly
public final class CreateClientReloadListeners {
    private CreateClientReloadListeners() {
    }

    public static void register(AddClientReloadListenersEvent event) {
        if (RenderSystem.getDevice().getDeviceInfo().backendName().equals("OpenGL")) {
            add(event, FlwProgramsReloader.INSTANCE);
        }
        add(event, ObjLoader.INSTANCE);
        add(event, Create.RESOURCE_RELOAD_LISTENER);
        event.addListener(Identifier.fromNamespaceAndPath(MOD_ID, "hat"), TrainHatInfoReloadListener.LISTENER);
        add(event, Ponder.RESOURCE_RELOAD_LISTENER);
    }

    private static void add(AddClientReloadListenersEvent event, CreateResourceReloader listener) {
        event.addListener(listener.getId(), listener);
    }
}
