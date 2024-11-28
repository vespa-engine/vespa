// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.deploy.DeployState;
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

    private static void assertValidationError(String message, String deploymentXml) {
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
        var builder = new DeployState.Builder().applicationPackage(app);
        try {
            var deployState = builder.build();
            VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
            ValidationTester.validate(new DeploymentSpecValidator(), model, deployState);
            fail("Did not get expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals(message, e.getMessage());
        } catch (SAXException|IOException e) {
            throw new RuntimeException(e);
        }
    }

}

