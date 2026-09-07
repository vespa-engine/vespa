// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import ai.vespa.llm.clients.TritonConfig;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ConfigChangeRestartAction.ConfigChange;
import com.yahoo.config.model.api.OnnxModelCost;
import com.yahoo.config.model.api.OnnxModelOptions;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.ValidationTester;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author glebashnik
 */
public class RestartOnDeployForTritonOnnxRuntimeValidatorTest {

    private static final String SERVICES_XML_WITH_ONE_CLUSTER = """
        <services version='1.0'>
          <container id='cluster1' version='1.0'>
          </container>
        </services>
        """;

    // Clusters must have different ports
    private static final String SERVICES_XML_TWO_CLUSTERS = """
        <services version='1.0'>
          <container id='cluster1' version='1.0'>
            <http>
              <server id='server1' port='8080'/>
            </http>
          </container>
          <container id='other-cluster' version='1.0'>
            <http>
              <server id='server2' port='8081'/>
            </http>
          </container>
        </services>
        """;

    private static final String SERVICES_XML_OTHER_CLUSTER = """
        <services version='1.0'>
          <container id='other-cluster' version='1.0'>
          </container>
        </services>
        """;

    private static final String SERVICES_XML_WITH_TRITON_CONFIG = """
        <services version='1.0'>
          <container id='cluster1' version='1.0'>
            <config name='ai.vespa.llm.clients.triton'>
              <timeout>30000</timeout>
            </config>
          </container>
        </services>
        """;

    @Test
    void restart_when_triton_runtime_enabled() {
        var previous = createModel(SERVICES_XML_WITH_ONE_CLUSTER, false);
        var next = createModel(SERVICES_XML_WITH_ONE_CLUSTER, true);
        var result = validateModel(previous, next);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getMessage().contains("Triton ONNX runtime was enabled"));
        assertEquals(ConfigChangeAction.Type.RESTART, result.get(0).getType());

        var restartAction = (VespaRestartAction) result.get(0);
        assertEquals(ConfigChange.DEFER_UNTIL_RESTART, restartAction.configChange());
    }

    @Test
    void restart_when_triton_runtime_disabled() {
        var previous = createModel(SERVICES_XML_WITH_ONE_CLUSTER, true);
        var next = createModel(SERVICES_XML_WITH_ONE_CLUSTER, false);
        var result = validateModel(previous, next);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getMessage().contains("Triton ONNX runtime was disabled"));
        assertEquals(ConfigChangeAction.Type.RESTART, result.get(0).getType());

        var restartAction = (VespaRestartAction) result.get(0);
        assertEquals(ConfigChange.DEFER_UNTIL_RESTART, restartAction.configChange());
    }

    @Test
    void no_restart_when_triton_runtime_remains_enabled() {
        var previous = createModel(SERVICES_XML_WITH_ONE_CLUSTER, true);
        var next = createModel(SERVICES_XML_WITH_ONE_CLUSTER, true);
        var result = validateModel(previous, next);

        assertTrue(result.isEmpty());
    }

    // TritonOnnxRuntime holds state shared across reconfigurations,
    // so any change to its config requires a restart.
    @Test
    void restart_when_triton_config_changes() {
        var previous = createModel(SERVICES_XML_WITH_ONE_CLUSTER, true);
        var next = createModel(SERVICES_XML_WITH_TRITON_CONFIG, true);
        var result = validateModel(previous, next);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getMessage().contains("Triton config changed"));
        assertEquals(ConfigChangeAction.Type.RESTART, result.get(0).getType());

        var restartAction = (VespaRestartAction) result.get(0);
        assertEquals(ConfigChange.DEFER_UNTIL_RESTART, restartAction.configChange());
    }

    // Session sharing is decided by a feature flag and written to the Triton config of the runtime component.
    @Test
    void session_sharing_feature_flag_is_written_to_triton_config() {
        assertFalse(tritonConfig(createModel(SERVICES_XML_WITH_ONE_CLUSTER, true, false)).shareOnnxSessionBetweenInstances());
        assertTrue(tritonConfig(createModel(SERVICES_XML_WITH_ONE_CLUSTER, true, true)).shareOnnxSessionBetweenInstances());
    }

    // Pins the restart marker on the session sharing field, which the feature flag flips.
    @Test
    void restart_when_triton_session_sharing_changes() {
        var previous = createModel(SERVICES_XML_WITH_ONE_CLUSTER, true, false);
        var next = createModel(SERVICES_XML_WITH_ONE_CLUSTER, true, true);
        var result = validateModel(previous, next);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getMessage().contains("Triton config changed"));
        assertTrue(result.get(0).getMessage().contains("shareOnnxSessionBetweenInstances"));
        assertEquals(ConfigChangeAction.Type.RESTART, result.get(0).getType());

        var restartAction = (VespaRestartAction) result.get(0);
        assertEquals(ConfigChange.DEFER_UNTIL_RESTART, restartAction.configChange());
    }

    @Test
    void no_restart_when_triton_runtime_remains_disabled() {
        var previous = createModel(SERVICES_XML_WITH_ONE_CLUSTER, false);
        var next = createModel(SERVICES_XML_WITH_ONE_CLUSTER, false);
        var result = validateModel(previous, next);

        assertTrue(result.isEmpty());
    }

    @Test
    void no_restart_when_cluster_with_triton_runtime_is_added() {
        var previous = createModel(SERVICES_XML_OTHER_CLUSTER, true);
        var next = createModel(SERVICES_XML_TWO_CLUSTERS, true);
        var result = validateModel(previous, next);

        assertTrue(result.isEmpty());
    }

    @Test
    void no_restart_when_cluster_with_triton_runtime_is_removed() {
        var previous = createModel(SERVICES_XML_TWO_CLUSTERS, true);
        var next = createModel(SERVICES_XML_OTHER_CLUSTER, true);
        var result = validateModel(previous, next);

        assertTrue(result.isEmpty());
    }

    private static List<ConfigChangeAction> validateModel(VespaModel current, VespaModel next) {
        return ValidationTester.validateChanges(
                new RestartOnDeployForTritonOnnxRuntimeValidator(),
                next,
                deployStateBuilder(false, false)
                        .properties(new TestProperties().setHostedVespa(true))
                        .previousModel(current)
                        .build());
    }

    private static VespaModel createModel(String servicesXml, boolean useTriton) {
        return createModel(servicesXml, useTriton, false);
    }

    private static VespaModel createModel(String servicesXml, boolean useTriton, boolean shareSession) {
        var builder = deployStateBuilder(useTriton, shareSession);
        return new VespaModelCreatorWithMockPkg(null, servicesXml).create(builder);
    }

    // The cluster produces the config for all its Triton components.
    private static TritonConfig tritonConfig(VespaModel model) {
        return model.getConfig(TritonConfig.class, "cluster1");
    }

    private static DeployState.Builder deployStateBuilder(boolean useTriton, boolean shareSession) {
        var deployStateBuilder = new DeployState.Builder().properties(
                new TestProperties().setUseTriton(useTriton).setTritonShareOnnxSession(shareSession));

        if (useTriton) {
            var mockModelCost = new OnnxModelCost.DisabledOnnxModelCost() {
                @Override
                public Map<String, ModelInfo> models() {
                    return Map.of("modernbert", new ModelInfo("modernbert", 1, 1, OnnxModelOptions.empty()));
                }
            };
            deployStateBuilder.onnxModelCost(mockModelCost);
            deployStateBuilder.zone(new Zone(SystemName.PublicCd, Environment.dev, RegionName.defaultName()));
            deployStateBuilder.sidecarProvider((clusterId, minNodeResources, needTriton) -> List.of());
        }

        return deployStateBuilder;
    }
}
