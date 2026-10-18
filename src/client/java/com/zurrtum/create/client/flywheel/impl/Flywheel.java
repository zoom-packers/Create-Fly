package com.zurrtum.create.client.flywheel.impl;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import org.jetbrains.annotations.UnknownNullability;

public final class Flywheel {
    public static final String MOD_ID = "flywheel";
    @UnknownNullability
    private static final ArtifactVersion version = new DefaultArtifactVersion("1.0.6+create");

    public void onInitializeClient() {
        setupImpl();
        FlwImpl.init();
        DebugEntryFlw.register();
    }

    private static void setupImpl() {
        // We can't use ArgumentTypeRegistry from Fabric API here as it also registers to BuiltInRegistries.COMMAND_ARGUMENT_TYPE.
        // We can't register anything to BuiltInRegistries.COMMAND_ARGUMENT_TYPE because it is a synced registry but
        // Flywheel is a client-side only mod.
        ArgumentTypeInfos.BY_CLASS.put(BackendArgument.class, BackendArgument.INFO);
        ArgumentTypeInfos.BY_CLASS.put(DebugModeArgument.class, DebugModeArgument.INFO);
        ArgumentTypeInfos.BY_CLASS.put(LightSmoothnessArgument.class, LightSmoothnessArgument.INFO);
    }

    public static ArtifactVersion version() {
        return version;
    }
}
