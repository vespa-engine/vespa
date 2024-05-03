// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
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
 * @author lesters
 */
public class RestartOnDeployForLocalLLMValidatorTest {

    private static final String LOCAL_LLM_COMPONENT = RestartOnDeployForLocalLLMValidator.LOCAL_LLM_COMPONENT;

    @Test
    void validate_no_restart_on_deploy() {
        VespaModel current = createModel();
        VespaModel next = createModel(withComponent(LOCAL_LLM_COMPONENT));
        List<ConfigChangeAction> result = validateModel(current, next);
        assertEquals(0, result.size());
    }

    @Test
    void validate_restart_on_deploy() {
        VespaModel current = createModel(withComponent(LOCAL_LLM_COMPONENT));
        VespaModel next = createModel(withComponent(LOCAL_LLM_COMPONENT));
        List<ConfigChangeAction> result = validateModel(current, next);
        assertEquals(1, result.size());
        assertTrue(result.get(0).validationId().isEmpty());
        assertEquals("Need to restart services in cluster 'cluster1' due to use of local LLM", result.get(0).getMessage());
    }

    private static List<ConfigChangeAction> validateModel(VespaModel current, VespaModel next) {
        return ValidationTester.validateChanges(new RestartOnDeployForLocalLLMValidator(),
                                                next,
                                                deployStateBuilder().previousModel(current).build());
    }

    private static VespaModel createModel(String component) {
        var xml = """
                <services version='1.0'>
                  <container id='cluster1' version='1.0'>
                    <http>
                      <server id='server1' port='8080'/>
                    </http>
                    %s
                  </container>
                </services>
                """.formatted(component);
        DeployState.Builder builder = deployStateBuilder();
        return new VespaModelCreatorWithMockPkg(null, xml).create(builder);
    }

    private static VespaModel createModel() {
        return createModel("");
    }

    private static String withComponent(String componentClass) {
        return "<component id='llm' class='%s' />".formatted(componentClass);
    }

    private static DeployState.Builder deployStateBuilder() {
        return new DeployState.Builder().properties(new TestProperties());
    }

    private static void assertStartsWith(String expected, List<ConfigChangeAction> result) {
        assertTrue(result.get(0).getMessage().startsWith(expected));
    }

}
