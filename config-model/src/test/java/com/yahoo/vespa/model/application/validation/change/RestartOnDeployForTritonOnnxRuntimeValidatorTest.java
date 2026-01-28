// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ConfigChangeRestartAction.ConfigChange;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.ValidationTester;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void restart_when_triton_runtime_enabled() {
        var previous = createModel(SERVICES_XML_WITH_ONE_CLUSTER,false);
        var next = createModel(SERVICES_XML_WITH_ONE_CLUSTER,true);
        var result = validateModel(previous, next);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getMessage().contains("Triton ONNX runtime was enabled"));
        assertEquals(ConfigChangeAction.Type.RESTART, result.get(0).getType());

        var restartAction = (VespaRestartAction) result.get(0);
        assertEquals(ConfigChange.DEFER_UNTIL_RESTART, restartAction.configChange());
    }

    @Test
    void restart_when_triton_runtime_disabled() {
        var previous = createModel(SERVICES_XML_WITH_ONE_CLUSTER,true);
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

    @Test
    void no_restart_when_triton_runtime_remains_disabled() {
        var previous = createModel(SERVICES_XML_WITH_ONE_CLUSTER,false);
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
        return ValidationTester.validateChanges(new RestartOnDeployForTritonOnnxRuntimeValidator(),
                                                next,
                                                deployStateBuilder(false).previousModel(current).build());
    }
    
    private static VespaModel createModel(String servicesXml, boolean useTriton) {
        var builder = deployStateBuilder(useTriton);
        return new VespaModelCreatorWithMockPkg(null, servicesXml).create(builder);
    }

    private static DeployState.Builder deployStateBuilder(boolean useTriton) {
        return new DeployState.Builder()
                .properties(new TestProperties().setUseTriton(useTriton));
    }

}
