// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import org.w3c.dom.Element;

import java.util.Optional;

/**
 * This class contains a context that is passed to a model builder, and can be used to retrieve the application package,
 * logger etc.
 *
 * @author lulf
 * @since 5.1
 */
public class ConfigModelContext {

    private final AbstractConfigProducer parent;
    private final String producerId;
    private final DeployState deployState;
    private final ConfigModelRepoAdder configModelRepoAdder;
    private final Optional<Element> servicesElement;

    private ConfigModelContext(DeployState deployState, ConfigModelRepoAdder configModelRepoAdder,
                               AbstractConfigProducer parent, String producerId, Optional<Element> servicesElement) {
        this.deployState = deployState;
        this.configModelRepoAdder = configModelRepoAdder;
        this.parent = parent;
        this.producerId = producerId;
        this.servicesElement = servicesElement;
    }

    public ApplicationPackage getApplicationPackage() { return deployState.getApplicationPackage(); }
    public String getProducerId() { return producerId; }
    public AbstractConfigProducer getParentProducer() { return parent; }
    public DeployLogger getDeployLogger() { return deployState.getDeployLogger(); }
    public DeployState getDeployState() { return deployState; }
    /** Returns the services element (root) in which this model needs to be built, if any */
    public Optional<Element> servicesElement() { return servicesElement; }

    /** Returns write access to the config model repo, or null (only) if this is improperly initialized during testing */
    public ConfigModelRepoAdder getConfigModelRepoAdder() { return configModelRepoAdder; }

    /** Create a new context with a different parent */
    public ConfigModelContext withParent(AbstractConfigProducer newParent) {
        return ConfigModelContext.create(deployState, configModelRepoAdder, newParent, producerId, servicesElement);
    }

    /** Create a new context with a different config model producer id */
    public ConfigModelContext withId(String producerId) {
        return ConfigModelContext.create(deployState, configModelRepoAdder, parent, producerId, servicesElement);
    }

    /**
     * Create an application context from a parent producer and an id.
     * 
     * @param deployState the global deploy state for this model
     * @param parent the parent to be used for the config model
     * @param producerId the id to be used for the config model
     * @param servicesElement the services element (root) in which this model needs to be built, if any
     * @return a model context that can be passed to a model
     */
    public static ConfigModelContext create(DeployState deployState, ConfigModelRepoAdder configModelRepoAdder,
                                            AbstractConfigProducer parent, String producerId, Optional<Element> servicesElement) {
        return new ConfigModelContext(deployState, configModelRepoAdder, parent, producerId, servicesElement);
    }

    /**
     * Create an application context from a parent producer and an id.
     * 
     * @param parent the parent to be used for the config model.
     * @param producerId the id to be used for the config model.
     * @param servicesElement the services element (root) in which this model needs to be built, if any
     * @return a model context that can be passed to a model.
     */
    public static ConfigModelContext create(ConfigModelRepoAdder configModelRepoAdder,
                                            AbstractConfigProducer parent, String producerId, Optional<Element> servicesElement) {
        return create(parent.getRoot().getDeployState(), configModelRepoAdder, parent, producerId, servicesElement);
    }

}
