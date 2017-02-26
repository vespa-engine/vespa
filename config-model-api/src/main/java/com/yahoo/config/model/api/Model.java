// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.config.provision.ProvisionInfo;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;

import java.util.Optional;
import java.util.Set;
import java.util.Collection;

/**
 * A {@link Model} represents the interface towards the model of an entire tenant, and defines methods
 * for querying this model.
 *
 * @author lulf
 * @since 5.1
 */
public interface Model {

    /**
     * Resolves a config using a given def file, apply overrides and returns it.
     *
     * @param configKey The key of the config to retrieve.
     * @param targetDef The config definition to use for applying defaults.
     * @return override The global override to apply to the generated config.
     */
    // TODO: Remove when 6.76 is deployed everywhere
    ConfigPayload getConfig(ConfigKey<?> configKey, ConfigDefinition targetDef, ConfigPayload override);


    /**
     * Resolves config for a given key and config definition
     *
     * @param configKey    The key to resolve
     * @param targetDef    The config definition to use for the schema
     */
    ConfigPayload getConfig(ConfigKey<?> configKey, ConfigDefinition targetDef);

    /**
     * Produces a set of the valid config keys for this model.
     */
    Set<ConfigKey<?>> allConfigsProduced();

    /**
     * Returns information about all hosts used in this model.
     */
    Collection<HostInfo> getHosts();

    /**
     * Returns all the config ids available for this model.
     */
    Set<String> allConfigIds();

    /**
     * Asks the {@link Model} instance to distribute files using provided filedistribution instance.
     * @param fileDistribution {@link com.yahoo.config.model.api.FileDistribution} instance that can be called to distribute files.
     */
    void distributeFiles(FileDistribution fileDistribution);


    /**
     * Tells file distributor to rescan all files. At the moment this is a very expensive operation, so should only be done
     * once per deployment.
     * @param fileDistribution {@link com.yahoo.config.model.api.FileDistribution} instance.
     */
    default void reloadDeployFileDistributor(FileDistribution fileDistribution) { }

    /**
     * Get the provisioning info for this model.
     * @return {@link ProvisionInfo} instance, if available.
     */
    Optional<ProvisionInfo> getProvisionInfo();

    /**
     * Returns whether this application allows serving config request for a different version.
     * This is a validation override which is useful when we skip loading old config models
     * due to some problem, or when we need to try a newer version of the platform on some node.
     */
    default boolean allowModelVersionMismatch() { return false; }

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
    default boolean skipOldConfigModels() { return false; }

}
