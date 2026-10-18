package com.zurrtum.create.api.registry;

import com.zurrtum.create.catnip.data.Pair;
import java.util.ServiceLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public interface CreateRegisterPlugin {
    List<CreateRegisterPlugin> PLUGINS = createPlugins();

    default void onBlockRegister() {
    }

    default void onFluidRegister() {
    }

    default void onDataLoaderRegister() {
    }

    default void onEntityAttributeRegister() {
    }

    static List<CreateRegisterPlugin> createPlugins() {
        // NeoForge: addons provide plugins through META-INF/services/com.zurrtum.create.api.registry.CreateRegisterPlugin
        // instead of the "create_plugin" Fabric entrypoint. They are keyed by provider class name, Create's own first.
        List<ServiceLoader.Provider<CreateRegisterPlugin>> list = ServiceLoader.load(
            CreateRegisterPlugin.class,
            CreateRegisterPlugin.class.getClassLoader()
        ).stream().toList();
        List<Pair<String, CreateRegisterPlugin>> entries = new ArrayList<>(list.size());
        Comparator<Pair<String, CreateRegisterPlugin>> pluginComparator = Comparator.comparing((Pair<String, CreateRegisterPlugin> pair) -> !pair.getFirst()
            .equals("create")).thenComparing(Pair::getFirst);
        for (ServiceLoader.Provider<CreateRegisterPlugin> container : list) {
            String name = container.type().getName();
            Pair<String, CreateRegisterPlugin> plugin = Pair.of(
                name.startsWith("com.zurrtum.create.") ? "create" : name,
                container.get()
            );
            int index = Collections.binarySearch(entries, plugin, pluginComparator);
            int insertionPoint = index >= 0 ? index : -index - 1;
            entries.add(insertionPoint, plugin);
        }
        return entries.stream().map(Pair::getSecond).toList();
    }

    static void registerBlock() {
        PLUGINS.forEach(CreateRegisterPlugin::onBlockRegister);
    }

    static void registerFluid() {
        PLUGINS.forEach(CreateRegisterPlugin::onFluidRegister);
    }

    static void registerDataLoader() {
        PLUGINS.forEach(CreateRegisterPlugin::onDataLoaderRegister);
    }

    static void registerEntityAttributes() {
        PLUGINS.forEach(CreateRegisterPlugin::onEntityAttributeRegister);
    }
}
