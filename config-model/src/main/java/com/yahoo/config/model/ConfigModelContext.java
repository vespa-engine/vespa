// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;

/**
 * This class contains a context that is passed to a model builder, and can be used to retrieve the application package,
 * logger etc.
 *
 * @author lulf
 * @since 5.1
 */
public class ConfigModelContext {

    private final AbstractConfigProducer producer;
    private final String producerId;
    private final DeployState deployState;
    private final ConfigModelRepoAdder configModelRepoAdder;

    private ConfigModelContext(DeployState deployState, ConfigModelRepoAdder configModelRepoAdder,
                               AbstractConfigProducer producer, String producerId) {
        this.deployState = deployState;
        this.configModelRepoAdder = configModelRepoAdder;
        this.producer = producer;
        this.producerId = producerId;
    }

    public ApplicationPackage getApplicationPackage() { return deployState.getApplicationPackage(); }
    public String getProducerId() { return producerId; }
    public AbstractConfigProducer getParentProducer() { return producer; }
    public DeployLogger getDeployLogger() { return deployState.getDeployLogger(); }
    public DeployState getDeployState() { return deployState; }

    /** Returns write access to the config model repo, or null (only) if this is improperly initialized during testing */
    public ConfigModelRepoAdder getConfigModelRepoAdder() { return configModelRepoAdder; }

    /**
     * Create a new context with a different parent, but with the same id and application package.
     *
     * @param newParent The parent to use for the new context.
     * @return A new context.
     */
    public ConfigModelContext modifyParent(AbstractConfigProducer newParent) {
        return ConfigModelContext.create(deployState, configModelRepoAdder, newParent, producerId);
    }

    /**
     * Create an application context from a parent producer and an id.
     * @param deployState The global deploy state for this model.
     * @param parent The parent to be used for the config model.
     * @param id The id to be used for the config model.
     * @return An model context that can be passed to a model.
     */
    public static ConfigModelContext create(DeployState deployState, ConfigModelRepoAdder configModelRepoAdder,
                                            AbstractConfigProducer parent, String id) {
        return new ConfigModelContext(deployState, configModelRepoAdder, parent, id);
    }

    /**
     * Create an application context from a parent producer and an id.
     * @param parent The parent to be used for the config model.
     * @param id The id to be used for the config model.
     * @return An model context that can be passed to a model.
     */
    public static ConfigModelContext createFromParentAndId(ConfigModelRepoAdder configModelRepoAdder, AbstractConfigProducer parent, String id) {
        return create(parent.getRoot().getDeployState(), configModelRepoAdder, parent, id);
    }

}
