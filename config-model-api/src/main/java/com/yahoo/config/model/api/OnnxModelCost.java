// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;

import java.net.URI;
import java.util.Map;

/**
 * @author bjorncs
 * @author hmusum
 */
public interface OnnxModelCost {

    Calculator newCalculator(ApplicationPackage appPkg, ApplicationId applicationId, ClusterSpec.Id clusterId);

    interface Calculator {
        long aggregatedModelCostInBytes();
        void registerModel(ApplicationFile path, OnnxModelOptions onnxModelOptions);
        void registerModel(URI uri, OnnxModelOptions onnxModelOptions);
        Map<String, ModelInfo> models();
        void setRestartOnDeploy();
        boolean restartOnDeploy();
        void store();
    }

    record ModelInfo(String modelId, long estimatedCost, long hash, OnnxModelOptions onnxModelOptions) {}

    static OnnxModelCost disabled() { return new DisabledOnnxModelCost(); }

    class DisabledOnnxModelCost implements OnnxModelCost, Calculator {
        @Override public Calculator newCalculator(ApplicationPackage appPkg, ApplicationId applicationId, ClusterSpec.Id clusterId) { return this; }
        @Override public long aggregatedModelCostInBytes() {return 0;}
        @Override public void registerModel(ApplicationFile path, OnnxModelOptions onnxModelOptions) {}
        @Override public void registerModel(URI uri, OnnxModelOptions onnxModelOptions) {}
        @Override public Map<String, ModelInfo> models() { return Map.of(); }
        @Override public void setRestartOnDeploy() {}
        @Override public boolean restartOnDeploy() { return false; }
        @Override public void store() {}
    }

}
