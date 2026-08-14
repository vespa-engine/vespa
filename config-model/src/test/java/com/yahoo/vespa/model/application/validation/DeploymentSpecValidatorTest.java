// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.deploy.TestDeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.model.VespaModel;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author hmusum
 */
public class DeploymentSpecValidatorTest {

    @Test
    void testEndpointNonExistentContainerId() {
        var deploymentXml = "<?xml version='1.0' encoding='UTF-8'?>" +
                "<deployment version='1.0'>" +
                "  <test />" +
                "  <prod>" +
                "    <region>us-east</region>" +
                "  </prod>" +
                "  <endpoints>" +
                "    <endpoint container-id='non-existing'/>" +
                "  </endpoints>" +
                "</deployment>";
        assertValidationError("Endpoint 'default' in instance default: 'non-existing' specified in " +
                "deployment.xml does not match any container cluster ID", deploymentXml);
    }

    @Test
    void requireUniqueInstanceId() {
        String deploymentXml = """
                    <deployment version="1.0" cloud-account="aws:010438471985">
                    <instance id="default" tags="search">
                        <prod>
                            <region>aws-us-west-2a</region>
                        </prod>
                    </instance>
                    <instance id="default" tags="canary">
                        <prod>
                            <region>aws-us-west-2a</region>
                        </prod>
                    </instance>
                </deployment>
                """;
        assertValidationError("Duplicate instance name 'default' specified in deployment.xml.", deploymentXml);
    }

    @Test
    void testPrivateEndpointNonExistentContainerId() {
        var deploymentXml = "<?xml version='1.0' encoding='UTF-8'?>" +
                "<deployment version='1.0'>" +
                "  <prod>" +
                "    <region>us-east</region>" +
                "  </prod>" +
                "  <endpoints>" +
                "    <endpoint type='private' container-id='non-existing'/>" +
                "  </endpoints>" +
                "</deployment>";
        assertValidationError("Zone endpoint in instance default: 'non-existing' specified in " +
                "deployment.xml does not match any container cluster ID", deploymentXml);
    }

    @Test
    void testZoneEndpointNonExistentContainerId() {
        var deploymentXml = "<?xml version='1.0' encoding='UTF-8'?>" +
                "<deployment version='1.0'>" +
                "  <prod>" +
                "    <region>us-east</region>" +
                "  </prod>" +
                "  <endpoints>" +
                "    <endpoint type='zone' container-id='non-existing'/>" +
                "  </endpoints>" +
                "</deployment>";
        assertValidationError("Zone endpoint in instance default: 'non-existing' specified in " +
                "deployment.xml does not match any container cluster ID", deploymentXml);
    }

    @Test
    void testPrivateEndpointExistingContainerIdValidates() {
        // 'default' matches the container in the test services, so this must not fail
        var deploymentXml = "<?xml version='1.0' encoding='UTF-8'?>" +
                "<deployment version='1.0'>" +
                "  <prod>" +
                "    <region>us-east</region>" +
                "  </prod>" +
                "  <endpoints>" +
                "    <endpoint type='private' container-id='default'/>" +
                "  </endpoints>" +
                "</deployment>";
        validate(deploymentXml);
    }

    private static void assertValidationError(String message, String deploymentXml) {
        try {
            validate(deploymentXml);
            fail("Did not get expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals(message, e.getMessage());
        }
    }

    private static void validate(String deploymentXml) {
        var simpleHosts = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
                          "<hosts>  " +
                          "<host name=\"localhost\">" +
                          "<alias>node0</alias>" +
                          "</host>" +
                          "</hosts>";

        var services = "<services version='1.0'>" +
                       "  <admin  version='2.0'>" +
                       "    <adminserver hostalias='node0' />" +
                       "  </admin>" +
                       "  <container id='default' version='1.0'>" +
                       "    <search/>" +
                       "      <nodes>" +
                       "        <node hostalias='node0'/>" +
                       "     </nodes>" +
                       "   </container>" +
                       "</services>";

        var app = new MockApplicationPackage.Builder()
                .withHosts(simpleHosts)
                .withServices(services)
                .withDeploymentSpec(deploymentXml)
                .build();
        var deployState = TestDeployState.create(app);
        try {
            VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
            ValidationTester.validate(new DeploymentSpecValidator(), model, deployState);
        } catch (SAXException|IOException e) {
            throw new RuntimeException(e);
        }
    }

}

