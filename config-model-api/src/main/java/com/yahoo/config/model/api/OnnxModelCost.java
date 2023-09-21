// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.config.model.api;

import com.yahoo.config.ModelReference;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.provision.ApplicationId;

/**
 * @author bjorncs
 */
public interface OnnxModelCost {

    Calculator newCalculator(DeployLogger logger);

    interface Calculator {
        long aggregatedModelCostInBytes();
        void registerModel(ApplicationFile path);
        void registerModel(ModelReference ref);
    }

    static OnnxModelCost testInstance() {
        return (__) -> new Calculator() {
            @Override public long aggregatedModelCostInBytes() { return 0; }
            @Override public void registerModel(ApplicationFile path) {}
            @Override public void registerModel(ModelReference ref) {}
        };
    }
}
