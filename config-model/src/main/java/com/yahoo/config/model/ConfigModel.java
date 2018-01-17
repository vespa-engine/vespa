// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

/**
 * A config model is an abstract representation of a subsystem, which is used
 * by the builder of that subsystem to derive a config producer tree for the subsystem, and by other
 * builders to access information about the subsystem for config production at a suitable abstraction level.
 *
 * @author gjoranv
 * @author bratseth
 * @author Ulf Lilleengen
 */
public abstract class ConfigModel {

    private final String id;

    /**
     * Constructs a new config model given a context.
     *
     * @param modelContext The model context.
     */
    public ConfigModel(ConfigModelContext modelContext) {
        super();
        this.id = modelContext.getProducerId();
    }

    /** Returns the id of this model */
    public final String getId() { return id; }

    /**
     * Initializes this model. All inter-model independent initialization
     * is done by implementing this method.
     * The model will be made available to dependent models by the framework when this returns.
     * <p>
     * TODO: Remove this method, as this is now done by the model builders.
     *
     * This default implementation does nothing.
     *
     * @param configModelRepo The ConfigModelRepo of the VespaModel
     * @deprecated This will go away in the next Vespa major release. Instead, inject the models you depend on
     * in your config model constructor.
     */
    @Deprecated
    public void initialize(ConfigModelRepo configModelRepo) { return; }

    /**
     * Prepares this model to start serving config requests, possibly using properties of other models.
     * The framework will call this method after models have been built. The model
     * should finalize its configurations that depend on other models in this step.
     *
     * This default implementation does nothing.
     *
     * @param configModelRepo The ConfigModelRepo of the system model
     */
    public void prepare(ConfigModelRepo configModelRepo) { return; }

    /**
     * <p>Returns whether this model must be maintained in memory for serving config requests.
     * Models which are used to amend other models at build time should override this to return false.</p>
     *
     * <p>This default implementation returns true.</p>
     */
    public boolean isServing() { return true; }

}
