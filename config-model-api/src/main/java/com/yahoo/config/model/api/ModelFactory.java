// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.component.Version;

/**
 * Factory for config models.
 */
public interface ModelFactory {

    /**
     * Returns the Vespa version of the models this builds.
     *
     * @return the version of a {@link Model} instance that this factory can create.
     */
    Version version();

    /**
     * Creates an instance of a {@link Model}. The resulting instance will be used to serve config. No model
     * validation will be done, calling this method assumes that{@link #createAndValidateModel(ModelContext, ValidationParameters)}
     * has already been called at some point for this model.
     *
     * @param modelContext an instance of {@link ModelContext}, containing dependencies for creating a {@link Model}.
     * @return a {@link Model} instance.
     */
    Model createModel(ModelContext modelContext);

    /**
     * Creates an instance of a {@link Model}. The resulting instance will be used to serve config. Any validation
     * of a {@link Model} and the {@link ModelContext} can be done in this method.
     *
     * @param modelContext an instance of {@link ModelContext}, containing dependencies for creating a {@link Model}
     * @param validationParameters validation parameters
     * @return a {@link ModelCreateResult} instance.
     */
    ModelCreateResult createAndValidateModel(ModelContext modelContext, ValidationParameters validationParameters);

}
