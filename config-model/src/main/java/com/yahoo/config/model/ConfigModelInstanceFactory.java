// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

/**
 * Interface for factories of config models.
 *
 * @author Ulf Lilleengen
 */
public interface ConfigModelInstanceFactory<MODEL extends ConfigModel> {

    /**
     * Create an instance of {@link com.yahoo.config.model.ConfigModel} given the input context.
     *
     * @param context The {@link com.yahoo.config.model.ConfigModelContext} to use.
     * @return an instance of {@link com.yahoo.config.model.ConfigModel}
     */
    MODEL createModel(ConfigModelContext context);

}
