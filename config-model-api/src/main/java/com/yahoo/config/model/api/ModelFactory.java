// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.config.provision.Version;

/**
 * Factory for config models.
 */
public interface ModelFactory {

    /**
     * Gets version of this {@link ModelFactory}. The version will be used to dispatch deployments to the correct
     * {@link ModelFactory}.
     *
     * @return The version of a {@link Model} instance that this factory can create.
     */
    Version getVersion();

    /**
     * Creates an instance of a {@link Model}. The resulting instance will be used to serve config. No model
     * validation will be done, calling this method presupposes that {@link #createAndValidateModel} has already
     * been called.
     *
     * @param modelContext An instance of {@link ModelContext}, containing dependencies for creating a {@link Model}.
     * @return a {@link Model} instance.
     */
    Model createModel(ModelContext modelContext);

    /**
     * Creates an instance of a {@link Model}. The resulting instance will be used to serve config. Any validation
     * of a {@link Model} and the {@link ModelContext} can be done in this method.
     *
     * @param modelContext An instance of {@link ModelContext}, containing dependencies for creating a {@link Model}.
     * @param ignoreValidationErrors true if validation errors should not trigger exceptions.
     * @return a {@link ModelCreateResult} instance.
     */
    ModelCreateResult createAndValidateModel(ModelContext modelContext, boolean ignoreValidationErrors);

}
