package com.zurrtum.create.client.neoforge;

import com.zurrtum.create.client.Create;
import com.zurrtum.create.client.content.contraptions.wrench.RadialWrenchHandler;
import com.zurrtum.create.client.content.equipment.toolbox.ToolboxHandlerClient;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * Replaces upstream's KeyboardHandlerMixin. It injected at {@code keyPress}'s release-branch RETURN and at its TAIL,
 * but NeoForge's patch merges both branches before {@code ClientHooks.onKeyInput}, which fires InputEvent.Key at that
 * same spot for every action.
 */
public final class CreateClientInput {
    private CreateClientInput() {
    }

    public static void onKey(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() != null) {
            return;
        }
        // KeyboardHandler#keyPress actions: 0 release, 1 press, 2 repeat
        boolean pressed = event.getAction() != 0;
        if (Create.SCHEMATIC_HANDLER.onKeyInput(event.getKeyEvent(), pressed)) {
            return;
        }
        if (ToolboxHandlerClient.onKeyInput(minecraft, event.getKeyEvent())) {
            return;
        }
        RadialWrenchHandler.onKeyInput(minecraft, event.getKeyEvent(), pressed);
    }
}
