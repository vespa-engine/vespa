// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.provision.ApplicationId;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

/**
 * @author bjorncs
 */
public interface OnnxModelCost {

    Calculator newCalculator(ApplicationPackage appPkg, ApplicationId applicationId);

    interface Calculator {
        long aggregatedModelCostInBytes();
        void registerModel(ApplicationFile path);
        void registerModel(ApplicationFile path, OnnxModelOptions onnxModelOptions);
        void registerModel(URI uri);
        void registerModel(URI uri, OnnxModelOptions onnxModelOptions);
        Map<String, ModelInfo> models();
        void setRestartOnDeploy();
        boolean restartOnDeploy();
    }

    record ModelInfo(String modelId, long estimatedCost, long hash, Optional<OnnxModelOptions> onnxModelOptions) {}

    static OnnxModelCost disabled() { return new DisabledOnnxModelCost(); }

    class DisabledOnnxModelCost implements OnnxModelCost, Calculator {
        @Override public Calculator newCalculator(ApplicationPackage appPkg, ApplicationId applicationId) { return this; }
        @Override public long aggregatedModelCostInBytes() {return 0;}
        @Override public void registerModel(ApplicationFile path) {}
        @Override public void registerModel(ApplicationFile path, OnnxModelOptions onnxModelOptions) {}
        @Override public void registerModel(URI uri) {}
        @Override public void registerModel(URI uri, OnnxModelOptions onnxModelOptions) {}
        @Override public Map<String, ModelInfo> models() { return Map.of(); }
        @Override public void setRestartOnDeploy() {}
        @Override public boolean restartOnDeploy() { return false; }
    }

}
