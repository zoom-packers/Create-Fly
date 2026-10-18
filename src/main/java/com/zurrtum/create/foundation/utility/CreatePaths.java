package com.zurrtum.create.foundation.utility;

import com.zurrtum.create.neoforge.Platform;

import java.nio.file.Path;

public class CreatePaths {
    // These are all absolute, so anything that is resolved via Path#resolve on these paths will also always be absolute
    public static final Path GAME_DIR = Platform.getGameDir();

    public static final Path SCHEMATICS_DIR = GAME_DIR.resolve("schematics");
    public static final Path UPLOADED_SCHEMATICS_DIR = SCHEMATICS_DIR.resolve("uploaded");

    private CreatePaths() {
    }
}
