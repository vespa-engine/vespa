// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.OnnxModelCost;
import com.yahoo.config.model.api.OnnxModelOptions;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.ValidationTester;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author hmusum
 */
public class RestartOnDeployForOnnxModelChangesValidatorTest {

    // Must be so large that changing model set or options requires restart (due to using more memory than available),
    // but not so large that deployment will not work at all with one model
    private static final long defaultCost = 723456789;
    private static final long defaultHash = 0;


    @Test
    void validate_no_changes() {
        VespaModel current = createModel();
        VespaModel next = createModel();
        List<ConfigChangeAction> result = validateModel(current, next);
        assertEquals(0, result.size());
    }

    @Test
    void validate_changed_estimated_cost() {
        VespaModel current = createModel(onnxModelCost(70000000, defaultHash));
        VespaModel next = createModel(onnxModelCost(723456789, defaultHash));
        List<ConfigChangeAction> result = validateModel(current, next);
        assertEquals(1, result.size());
        assertTrue(result.get(0).validationId().isEmpty());
        assertEquals("Onnx model 'https://data.vespa.oath.cloud/onnx_models/e5-base-v2/model.onnx' has changed (estimated cost), need to restart services in container cluster 'cluster1'", result.get(0).getMessage());
    }

    @Test
    void validate_changed_estimated_cost_non_hosted() {
        boolean hosted = false;
        VespaModel current = createModel(onnxModelCost(70000000, defaultHash), hosted);
        VespaModel next = createModel(onnxModelCost(723456789, defaultHash), hosted);
        List<ConfigChangeAction> result = validateModel(current, next, hosted);
        assertEquals(0, result.size());
    }

    @Test
    void validate_changed_hash() {
        VespaModel current = createModel();
        VespaModel next = createModel(onnxModelCost(defaultCost, 123));
        List<ConfigChangeAction> result = validateModel(current, next);
        assertEquals(1, result.size());
        assertStartsWith("Onnx model 'https://data.vespa.oath.cloud/onnx_models/e5-base-v2/model.onnx' has changed (model hash)", result);
    }

    @Test
    void validate_changed_option() {
        VespaModel current = createModel();
        VespaModel next = createModel(onnxModelCost(), true, "sequential");
        List<ConfigChangeAction> result = validateModel(current, next);
        assertEquals(1, result.size());
        assertStartsWith("Onnx model 'https://data.vespa.oath.cloud/onnx_models/e5-base-v2/model.onnx' has changed (model option(s))", result);
    }

    @Test
    void validate_changed_model_set() {
        VespaModel current = createModel();
        VespaModel next = createModel(onnxModelCost(), true, "parallel", "e5-small-v2");
        List<ConfigChangeAction> result = validateModel(current, next);
        assertEquals(1, result.size());
        assertStartsWith("Onnx model set has changed from [https://data.vespa.oath.cloud/onnx_models/e5-base-v2/model.onnx] " +
                                 "to [https://data.vespa.oath.cloud/onnx_models/e5-small-v2/model.onnx",
                         result);
    }

    private static List<ConfigChangeAction> validateModel(VespaModel current, VespaModel next) {
        return validateModel(current, next, true);
    }

    private static List<ConfigChangeAction> validateModel(VespaModel current, VespaModel next, boolean hosted) {
        return ValidationTester.validateChanges(new RestartOnDeployForOnnxModelChangesValidator(),
                                                next,
                                                deployStateBuilder(hosted).previousModel(current).build());
    }

    private static OnnxModelCost onnxModelCost() {
        return onnxModelCost(defaultCost, defaultHash);
    }

    private static OnnxModelCost onnxModelCost(long estimatedCost, long hash) {
        return (appPkg, applicationId, clusterId) -> new OnnxModelCost.Calculator() {

            private final Map<String, OnnxModelCost.ModelInfo> models = new HashMap<>();
            private boolean restartOnDeploy = false;

            @Override
            public long aggregatedModelCostInBytes() { return estimatedCost; }

            @Override
            public void registerModel(ApplicationFile path, OnnxModelOptions onnxModelOptions) {}

            @Override
            public void registerModel(URI uri, OnnxModelOptions onnxModelOptions) {
                models.put(uri.toString(), new OnnxModelCost.ModelInfo(uri.toString(), estimatedCost, hash, onnxModelOptions));
            }

            @Override
            public Map<String, OnnxModelCost.ModelInfo> models() { return models; }

            @Override
            public void setRestartOnDeploy() { restartOnDeploy = true; }

            @Override
            public boolean restartOnDeploy() { return restartOnDeploy; }

            @Override
            public void store() {}
        };
    }

    private static VespaModel createModel() {
        return createModel(onnxModelCost(), true);
    }

    private static VespaModel createModel(OnnxModelCost onnxModelCost) {
        return createModel(onnxModelCost, true, "parallel");
    }

    private static VespaModel createModel(OnnxModelCost onnxModelCost, boolean hosted) {
        return createModel(onnxModelCost, hosted, "parallel");
    }

    private static VespaModel createModel(OnnxModelCost onnxModelCost, boolean hosted, String executionMode) {
        return createModel(onnxModelCost, hosted, executionMode, "e5-base-v2");
    }

    private static VespaModel createModel(OnnxModelCost onnxModelCost, boolean hosted, String executionMode, String modelId) {
        DeployState.Builder builder = deployStateBuilder(hosted)
                .onnxModelCost(onnxModelCost);
        return hosted ? hostedModel(builder, executionMode, modelId) : nonHostedModel(builder, executionMode, modelId);
    }

    private static VespaModel hostedModel(DeployState.Builder builder, String executionMode, String modelId) {
        var servicesXml  = """
                          <services version='1.0'>
                            <container id='cluster1' version='1.0'>
                              <component id="hf-embedder" type="hugging-face-embedder">
                                           <transformer-model model-id="%s" url="https://my/url/%s.onnx"/>
                                           <tokenizer-model model-id="e5-base-v2-vocab" path="app/tokenizer.json"/>
                                           <onnx-execution-mode>%s</onnx-execution-mode>
                              </component>
                              <nodes count='1'>
                                <resources vcpu='1' memory='2Gb' disk='25Gb'/>
                              </nodes>
                            </container>
                          </services>
                          """.formatted(modelId, modelId, executionMode);

        var deploymentXml = """
                            <deployment version='1.0' empty-host-ttl='1d'>
                              <instance id='default'>
                                <prod>
                                  <region>us-east-1</region>
                                  <region empty-host-ttl='0m'>us-north-1</region>
                                  <region>us-west-1</region>
                                </prod>
                              </instance>
                            </deployment>
                            """;

        var applicationPackage = new MockApplicationPackage.Builder()
                .withServices(servicesXml)
                .withDeploymentSpec(deploymentXml)
                .build();

        return new VespaModelCreatorWithMockPkg(applicationPackage).create(builder);
    }

    private static VespaModel nonHostedModel(DeployState.Builder builder, String executionMode, String modelId) {
        var xml = """
                                       <services version='1.0'>
                                         <container id='cluster1' version='1.0'>
                                           <http>
                                             <server id='server1' port='8080'/>
                                           </http>
                                         <component id="hf-embedder" type="hugging-face-embedder">
                                           <transformer-model model-id="%s" url="https://my/url/%s.onnx"/>
                                           <tokenizer-model model-id="e5-base-v2-vocab" path="app/tokenizer.json"/>
                                           <onnx-execution-mode>%s</onnx-execution-mode>
                                         </component>
                                         </container>
                                       </services>
                """.formatted(modelId, modelId, executionMode);

        return new VespaModelCreatorWithMockPkg(null, xml).create(builder);
    }


    private static DeployState.Builder deployStateBuilder(boolean hosted) {
        var builder = new DeployState.Builder()
                .properties((new TestProperties()).setHostedVespa(hosted));
        if (hosted)
            builder.endpoints(Set.of(new ContainerEndpoint("cluster1", ApplicationClusterEndpoint.Scope.zone, List.of("tc.example.com"))))
                    .modelHostProvisioner(new InMemoryProvisioner(5, new NodeResources(1, 2, 25, 0.3), true));
        return builder;
    }

    private static void assertStartsWith(String expected, List<ConfigChangeAction> result) {
        assertTrue(result.get(0).getMessage().startsWith(expected));
    }

}
