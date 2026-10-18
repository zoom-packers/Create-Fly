package com.zurrtum.create.neoforge;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforgespi.language.IModInfo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * NeoForge replacements for the Fabric Loader calls upstream Create Fly makes. Upstream code calls these helpers where
 * it called {@code FabricLoader.getInstance()}, so a rebase only conflicts on those lines.
 * <p>
 * Uses {@link LoadingModList} rather than {@link ModList}, because mixin plugins and static initialisers run before
 * FML has constructed any mod.
 */
public final class Platform {
    private static final Map<Path, FileSystem> JAR_FILE_SYSTEMS = new HashMap<>();

    private Platform() {
    }

    /**
     * Replaces Fabric's {@code ModContainer#getRootPaths}: directories that hold the mod's files (in a dev run, the
     * build output folders; in production, the root of the mod jar).
     */
    public static synchronized List<Path> getModRootPaths(String id) {
        List<Path> roots = new ArrayList<>();
        for (Path root : LoadingModList.get().getModFileById(id).getFile().getContents().getContentRoots()) {
            if (Files.isDirectory(root)) {
                roots.add(root);
                continue;
            }
            FileSystem fs = JAR_FILE_SYSTEMS.computeIfAbsent(
                root, path -> {
                    try {
                        return FileSystems.newFileSystem(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            );
            roots.add(fs.getPath("/"));
        }
        return roots;
    }

    public static boolean isModLoaded(String id) {
        ModList modList = ModList.get();
        if (modList != null) {
            return modList.isLoaded(id);
        }
        return LoadingModList.get().getModFileById(id) != null;
    }

    public static Optional<IModInfo> getMod(String id) {
        return getMods().stream().filter(mod -> mod.getModId().equals(id)).<IModInfo>map(mod -> mod).findFirst();
    }

    public static List<? extends IModInfo> getMods() {
        return LoadingModList.get().getMods();
    }

    public static String getModName(String id) {
        return getMod(id).map(IModInfo::getDisplayName).orElse(id);
    }

    public static String getModVersion(String id) {
        return getMod(id).map(mod -> mod.getVersion().toString()).orElse("0.0.0");
    }

    public static Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    public static Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    public static boolean isDevelopmentEnvironment() {
        return !FMLLoader.getCurrent().isProduction();
    }
}
