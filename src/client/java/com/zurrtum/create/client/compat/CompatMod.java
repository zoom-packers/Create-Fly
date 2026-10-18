package com.zurrtum.create.client.compat;


public class CompatMod {
    public static void register() {
        // NeoForge: the Fabric potion fluid renderer and the Trinkets goggle renderer are not ported (see PORTING.md)
        // TODO(neoforge 26.3): EMI / SEMI recipe viewer plugin. Upstream's REI, RRV and EIV plugins are excluded from
        //  the build; JEI is the only recipe viewer supported for now. Curios support for the goggles is also TODO.
    }
}
