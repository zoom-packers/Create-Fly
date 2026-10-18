package com.zurrtum.create.compat;

import com.zurrtum.create.compat.computercraft.AllComputerDisplaySource;
import com.zurrtum.create.compat.computercraft.AllComputerPeripherals;
import com.zurrtum.create.compat.computercraft.ComputerIntegration;

public class CompatMod {
    public static void register() {
        // NeoForge: recipe sync for recipe viewers is done in CreateNeoForge#sendRecipes
        if (Mods.COMPUTERCRAFT.isLoaded()) {
            ComputerIntegration.register();
            AllComputerPeripherals.register();
            AllComputerDisplaySource.register();
        }
    }
}
