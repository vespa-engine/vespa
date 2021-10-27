// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.test;

import com.yahoo.config.model.ConfigModelRegistry;
import com.yahoo.config.model.MapConfigModelRegistry;

/**
 * Creates a {@link ConfigModelRegistry} instance that simulates the hosted environment.
 *
 * @author bjorncs
 */
public class HostedConfigModelRegistry {

    private HostedConfigModelRegistry() {}

    public static ConfigModelRegistry create() {
        return MapConfigModelRegistry.createFromList(new ModelBuilderAddingAccessControlFilter());
    }
}
