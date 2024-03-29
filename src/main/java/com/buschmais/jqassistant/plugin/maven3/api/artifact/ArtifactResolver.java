package com.buschmais.jqassistant.plugin.maven3.api.artifact;

import com.buschmais.jqassistant.core.scanner.api.ScannerContext;
import com.buschmais.jqassistant.core.store.api.model.Descriptor;
import com.buschmais.jqassistant.plugin.maven3.api.model.MavenArtifactDescriptor;

public interface ArtifactResolver {

    /**
     * Resolves an artifact descriptor for the given coordinates, i.e. by looking up
     * an existing one and creating new one on demand.
     *
     * @param coordinates
     *            The artifact coordinates.
     * @param scannerContext
     *            The scanner context.
     * @return The resolved artifact descriptor.
     */
    MavenArtifactDescriptor resolve(Coordinates coordinates, ScannerContext scannerContext);

    /**
     * Resolves an artifact descriptor for the given coordinates, i.e. by looking up
     * an existing one and creating new one on demand.
     *
     * This method furthermore migrates the {@link MavenArtifactDescriptor} to the
     * required type.
     *
     * @param coordinates
     *            The artifact coordinates.
     * @param requiredType
     *            The required descriptor type.
     * @param scannerContext
     *            The scanner context.
     * @return The resolved artifact descriptor.
     */
    default <D extends Descriptor> D resolve(Coordinates coordinates, Class<D> requiredType, ScannerContext scannerContext) {
        MavenArtifactDescriptor mavenArtifactDescriptor = resolve(coordinates, scannerContext);
        return scannerContext.getStore().addDescriptorType(mavenArtifactDescriptor, requiredType);
    }
}
