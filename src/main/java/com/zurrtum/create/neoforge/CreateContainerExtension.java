package com.zurrtum.create.neoforge;

import com.zurrtum.create.infrastructure.items.ContainerExtension;

/**
 * Compile-time alias injected into {@link net.minecraft.world.Container} through interfaces.json.
 * <p>
 * NeoForge's Container already implements {@code net.neoforged.neoforge.common.extensions.ContainerExtension}, and
 * the source transformer cannot inject a second interface with the same simple name. At runtime
 * {@code ContainerMixin} adds {@link ContainerExtension} itself; this alias is never referenced from bytecode.
 */
public interface CreateContainerExtension extends ContainerExtension {
}
