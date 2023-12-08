// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.OnnxModelCost;
import com.yahoo.config.model.api.OnnxModelOptions;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author hmusum
 */
public class RestartOnDeployForOnnxModelChangesValidatorTest {

    @Test
    void validate_no_changes() {
        VespaModel current = createModel();
        VespaModel next = createModel();
        List<ConfigChangeAction> result = validateModel(current, next);
        assertEquals(0, result.size());
    }

    @Test
    void validate_changed_estimated_cost() {
        VespaModel current = createModel();
        VespaModel next = createModel(onnxModelCost(123, 0));
        List<ConfigChangeAction> result = validateModel(current, next);
        assertEquals(1, result.size());
        assertTrue(result.get(0).validationId().isEmpty());
        assertEquals("Onnx model 'https://my/url/model.onnx' has changed (estimated cost), need to restart services in container cluster 'cluster1'", result.get(0).getMessage());
    }

    @Test
    void validate_changed_hash() {
        VespaModel current = createModel();
        VespaModel next = createModel(onnxModelCost(0, 123));
        List<ConfigChangeAction> result = validateModel(current, next);
        assertEquals(1, result.size());
        assertEquals("Onnx model 'https://my/url/model.onnx' has changed (model hash), need to restart services in container cluster 'cluster1'", result.get(0).getMessage());
    }

    @Test
    void validate_changed_option() {
        VespaModel current = createModel();
        VespaModel next = createModel(onnxModelCost(0, 0), "sequential");
        List<ConfigChangeAction> result = validateModel(current, next);
        assertEquals(1, result.size());
        assertEquals("Onnx model 'https://my/url/model.onnx' has changed (model option(s)), need to restart services in container cluster 'cluster1'", result.get(0).getMessage());
    }

    private static List<ConfigChangeAction> validateModel(VespaModel current, VespaModel next) {
        return new RestartOnDeployForOnnxModelChangesValidator().validate(current, next, deployStateBuilder().build());
    }

    private static OnnxModelCost onnxModelCost() {
        return onnxModelCost(0, 0);
    }

    private static OnnxModelCost onnxModelCost(long estimatedCost, long hash) {
        return (appPkg, applicationId) -> new OnnxModelCost.Calculator() {

            private final Map<String, OnnxModelCost.ModelInfo> models = new HashMap<>();
            private boolean restartOnDeploy = false;

            @Override
            public long aggregatedModelCostInBytes() { return estimatedCost; }

            @Override
            public void registerModel(ApplicationFile path) {}

            @Override
            public void registerModel(ApplicationFile path, OnnxModelOptions onnxModelOptions) {}

            @Override
            public void registerModel(URI uri) {}

            @Override
            public void registerModel(URI uri, OnnxModelOptions onnxModelOptions) {
                models.put(uri.toString(), new OnnxModelCost.ModelInfo(uri.toString(), estimatedCost, hash, Optional.ofNullable(onnxModelOptions)));
            }

            @Override
            public Map<String, OnnxModelCost.ModelInfo> models() { return models; }

            @Override
            public void setRestartOnDeploy() { restartOnDeploy = true; }

            @Override
            public boolean restartOnDeploy() { return restartOnDeploy; }
        };
    }

    private static VespaModel createModel() {
        return createModel(onnxModelCost());
    }

    private static VespaModel createModel(OnnxModelCost onnxModelCost) {
        return createModel(onnxModelCost, "parallel");
    }

    private static VespaModel createModel(OnnxModelCost onnxModelCost, String executionMode) {
        DeployState.Builder builder = deployStateBuilder();
        builder.onnxModelCost(onnxModelCost);
        return createModel(builder, executionMode);
    }

    private static VespaModel createModel(DeployState.Builder builder, String executionMode) {
        String xml = """
                                       <services version='1.0'>
                                         <container id='cluster1' version='1.0'>
                                           <http>
                                             <server id='server1' port='8080'/>
                                           </http>
                                         <component id="hf-embedder" type="hugging-face-embedder">
                                           <transformer-model model-id="e5-base-v2" url="https://my/url/model.onnx"/>
                                           <tokenizer-model model-id="e5-base-v2-vocab" path="app/tokenizer.json"/>
                                           <max-tokens>1024</max-tokens>
                                           <transformer-input-ids>my_input_ids</transformer-input-ids>
                                           <transformer-attention-mask>my_attention_mask</transformer-attention-mask>
                                           <transformer-token-type-ids>my_token_type_ids</transformer-token-type-ids>
                                           <transformer-output>my_output</transformer-output>
                                           <normalize>true</normalize>
                                           <onnx-execution-mode>%s</onnx-execution-mode>
                                           <onnx-intraop-threads>10</onnx-intraop-threads>
                                           <onnx-interop-threads>8</onnx-interop-threads>
                                           <pooling-strategy>mean</pooling-strategy>
                                         </component>
                                         </container>
                                         <container id='cluster2' version='1.0'>
                                           <http>
                                             <server id='server1' port='8081'/>
                                           </http>
                                         </container>
                                       </services>
                """.formatted(executionMode);

        return new VespaModelCreatorWithMockPkg(null, xml).create(builder);
    }

    private static DeployState.Builder deployStateBuilder() {
        return new DeployState.Builder()
                .properties((new TestProperties()).setRestartOnDeployForOnnxModelChanges(true));
    }

}
