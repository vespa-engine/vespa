// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.config.model.api;

import com.yahoo.config.ModelReference;
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
        @Deprecated(forRemoval = true) void registerModel(ModelReference ref); // TODO(bjorncs): remove once no longer in use by old config models
        void registerModel(URI uri);
    }

    static OnnxModelCost disabled() {
        return (__, ___) -> new Calculator() {
            @Override public long aggregatedModelCostInBytes() { return 0; }
            @Override public void registerModel(ApplicationFile path) {}
            @SuppressWarnings("removal") @Override public void registerModel(ModelReference ref) {}
            @Override public void registerModel(URI uri) {}
        };
    }
}
