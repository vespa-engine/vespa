// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author hmusum
 */
public class DeploymentFileValidatorTest {

    @Test
    public void testDeploymentWithNonExistentGlobalId() throws IOException, SAXException {
        final String simpleHosts = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
                "<hosts>  " +
                "<host name=\"localhost\">" +
                "<alias>node0</alias>" +
                "</host>" +
                "</hosts>";

        final String services = "<services version='1.0'>" +
                "  <admin  version='2.0'>" +
                "    <adminserver hostalias='node0' />" +
                "  </admin>" +
                "  <jdisc id='default' version='1.0'>" +
                "    <search/>" +
                "      <nodes>" +
                "        <node hostalias='node0'/>" +
                "     </nodes>" +
                "   </jdisc>" +
                "</services>";

        final String deploymentSpec = "<?xml version='1.0' encoding='UTF-8'?>" +
                "<deployment version='1.0'>" +
                "  <test />" +
                "  <prod global-service-id='non-existing'>" +
                "    <region active='true'>us-east</region>" +
                "  </prod>" +
                "</deployment>";

        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withHosts(simpleHosts)
                .withServices(services)
                .withDeploymentSpec(deploymentSpec)
                .build();
        DeployState.Builder builder = new DeployState.Builder().applicationPackage(app);
        try {
            final DeployState deployState = builder.build(true);
            VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
            new DeploymentFileValidator().validate(model, deployState);
            fail("Did not get expected exception");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("specified in deployment.xml does not match any container cluster id"));
        }
    }

}
