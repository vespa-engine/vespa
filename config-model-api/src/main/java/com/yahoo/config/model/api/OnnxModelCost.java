// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.config.model.api;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;

import java.net.URI;

/**
 * @author bjorncs
 */
public interface OnnxModelCost {

    Calculator newCalculator(ApplicationPackage appPkg, DeployLogger logger);

    interface Calculator {
        long aggregatedModelCostInBytes();
        void registerModel(ApplicationFile path);
        void registerModel(URI uri);
    }

    static OnnxModelCost disabled() {
        return (__, ___) -> new Calculator() {
            @Override public long aggregatedModelCostInBytes() { return 0; }
            @Override public void registerModel(ApplicationFile path) {}
            @Override public void registerModel(URI uri) {}
        };
    }

}
