// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.component.Version;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.FileReference;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.Collection;

/**
 * A {@link Model} represents the interface towards the model of an entire tenant, and defines methods
 * for querying this model.
 *
 * @author Ulf Lilleengen
 */
public interface Model {

    /**
     * Resolves config for a given key and config definition
     *
     * @param configKey the key to resolve
     * @param configDefinition the config definition to use for the schema
     */
    ConfigInstance.Builder getConfigInstance(ConfigKey<?> configKey, ConfigDefinition configDefinition);

    /** Produces a set of the valid config keys for this model. */
    Set<ConfigKey<?>> allConfigsProduced();

    /** Returns information about all hosts used in this model. */
    Collection<HostInfo> getHosts();

    /** Returns all the config ids available for this model. */
    Set<String> allConfigIds();

    /** The set of files that should be distributed to the hosts in this model. */
    Set<FileReference> fileReferences();

    /**
     * Gets the allocated hosts for this model.
     * 
     * @return {@link AllocatedHosts} instance, if available
     */
    AllocatedHosts allocatedHosts();

    /**
     * Returns whether this application allows serving config request for a different version.
     * This is a validation override which is useful when we skip loading old config models
     * due to some problem, or when we need to try a newer version of the platform on some node.
     */
    default boolean allowModelVersionMismatch(Instant now) { return false; }

    /**
     * Returns whether old config models should be loaded (default) or not.
     * Skipping old config models is a validation override which is useful when the old model
     * version is known to contain some incompatibility with the application package
     * and it is believed that the latest model version will deliver usable config for old versions
     * requesting config.
     * <p>
     * If a model returns true to this it should also return true to {@link #allowModelVersionMismatch}
     * or clients requesting config for those old models will not get config at all.
     */
    default boolean skipOldConfigModels(Instant now) { return false; }

    /** Returns the version of this model. */
    default Version version() { return Version.emptyVersion; }

    /** Returns the wanted node version of this model. */
    default Version wantedNodeVersion() { return Version.emptyVersion; }

    /** Returns the provisioned hosts of this. */
    default Provisioned provisioned() { return new Provisioned(); }

    /** Returns the set of document types in each content cluster. */
    default Map<String, Set<String>> documentTypesByCluster() { return Map.of(); }

    /** Returns the set of document types in each cluster, that have an index for one of more fields. */
    default Map<String, Set<String>> indexedDocumentTypesByCluster() { return Map.of(); }

    /** Returns the set of container clusters */
    default Set<ApplicationClusterInfo> applicationClusterInfo() { return Set.of(); }
}
