// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestDeployState;
import com.yahoo.text.Text;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.ValidationTester;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Lester Solbakken
 * @author glebashnik
 */
public class RestartOnDeployForLocalLLMValidatorTest {

    private static final String LOCAL_LLM_COMPONENT = RestartOnDeployForLocalLLMValidator.LOCAL_LLM_COMPONENT;

    @Test
    void validate_no_restart_on_deploy() {
        VespaModel current = createModel();
        VespaModel next = createModel(withLocalLLMComponent("models/model.gguf", 1));
        List<ConfigChangeAction> result = validateModel(current, next);
        assertEquals(0, result.size());
    }

    @Test
    void validate_restart_on_deploy_llm_config_unchanged() {
        VespaModel current = createModel(withLocalLLMComponent("models/model.gguf", 1));
        VespaModel next = createModel(withLocalLLMComponent("models/model.gguf", 1));
        List<ConfigChangeAction> result = validateModel(current, next);
        assertEquals(1, result.size());
        assertTrue(result.get(0).validationId().isEmpty());
        assertEquals(
                "Need to restart services in cluster 'cluster1' due to use of local LLM", result.get(0).getMessage());
        assertTrue(
                result.get(0).ignoreForInternalRedeploy(),
                "Restart must be ignored for internal redeployment when LLM config is unchanged"
        );
    }

    @Test
    void validate_restart_on_deploy_llm_config_changed() {
        VespaModel current = createModel(withLocalLLMComponent("models/model.gguf", 1));
        VespaModel next = createModel(withLocalLLMComponent("models/model.gguf", 2));
        List<ConfigChangeAction> result = validateModel(current, next);
        assertEquals(1, result.size());
        assertFalse(
                result.get(0).ignoreForInternalRedeploy(),
                "Restart must not be ignored for internal redeployment when LLM config changed"
        );
    }

    @Test
    void validate_restart_on_deploy_model_path_changed() {
        VespaModel current = createModel(withLocalLLMComponent("models/model-a.gguf", 1));
        VespaModel next = createModel(withLocalLLMComponent("models/model-b.gguf", 1));
        List<ConfigChangeAction> result = validateModel(current, next);
        assertEquals(1, result.size());
        assertFalse(result.get(0).ignoreForInternalRedeploy(), "Restart must not be ignored when model path changed");
    }

    private static List<ConfigChangeAction> validateModel(VespaModel current, VespaModel next) {
        return ValidationTester.validateChanges(
                new RestartOnDeployForLocalLLMValidator(),
                next,
                deployStateBuilder().previousModel(current).build()
        );
    }

    private static VespaModel createModel(String component) {
        var xml = Text.format(
                """
                        <services version='1.0'>
                          <container id='cluster1' version='1.0'>
                            <http>
                              <server id='server1' port='8080'/>
                            </http>
                            %s
                          </container>
                        </services>
                        """, component
        );
        DeployState.Builder builder = deployStateBuilder();
        return new VespaModelCreatorWithMockPkg(null, xml).create(builder);
    }

    private static VespaModel createModel() {
        return createModel("");
    }

    private static String withLocalLLMComponent(String modelPath, int parallelRequests) {
        return Text.format(
                """
                        <component id='llm' class='%s'>
                          <config name="ai.vespa.llm.clients.llm-local-client">
                            <model path="%s"/>
                            <parallelRequests>%d</parallelRequests>
                          </config>
                        </component>""", RestartOnDeployForLocalLLMValidatorTest.LOCAL_LLM_COMPONENT, modelPath,
                parallelRequests
        );
    }

    private static DeployState.Builder deployStateBuilder() {
        return TestDeployState.createBuilder();
    }

}