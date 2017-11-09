// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.deploy.DeployProperties;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author mortent
 */
public class IdentityValidatorTest {

    private static final ApplicationId APPLICATION_ID = ApplicationId.from("tenant", "application", "instance");

    @Test
    public void it_fails_on_invalid_servicename() throws IOException, SAXException {
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withServices(getServicesWithIdentity("invalid.service"))
                .build();
        DeployState deployState = getDeployState(app);
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
        try {
            new IdentityValidator().validate(model, deployState);
            fail("Did not get expected exception");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("Invalid service name"));
        }
    }

    @Test
    public void it_accepts_correct_servicename() throws IOException, SAXException {
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withServices(getServicesWithIdentity("tenant.valid_service"))
                .build();

        DeployState deployState = getDeployState(app);
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
        new IdentityValidator().validate(model, deployState);
    }

    @Test
    public void it_validates_all_container_clusters() throws IOException, SAXException {
        String services = "<services version='1.0'>" +
                          "  <jdisc id='valid' version='1.0'>" +
                          "    <identity>" +
                          "      <domain>domain</domain>" +
                          "      <service>tenant.valid.service</service>" +
                          "     </identity>" +
                          "   </jdisc>" +
                          "  <jdisc id='invalid' version='1.0'>" +
                          "    <http><server id='other' port='1234'/></http>" +
                          "    <identity>" +
                          "      <domain>domain</domain>" +
                          "      <service>invalid.service</service>" +
                          "     </identity>" +
                          "   </jdisc>" +
                          "</services>";
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withServices(services)
                .build();
        DeployState deployState = getDeployState(app);
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
        try {
            new IdentityValidator().validate(model, deployState);
            fail("Did not get expected exception");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("Invalid service name"));
        }
    }

    @Test
    public void it_does_not_fail_when_identity_not_defined() throws IOException, SAXException {
        String services = "<services version='1.0'>\n" +
                          "    <jdisc id='default' version='1.0' />\n" +
                          "</services>";
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withServices(services)
                .build();
        DeployState deployState = getDeployState(app);
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
        new IdentityValidator().validate(model, deployState);
    }

    private DeployState getDeployState(ApplicationPackage applicationPackage) {
        return new DeployState.Builder()
                .properties(new DeployProperties.Builder().applicationId(APPLICATION_ID).build())
                .applicationPackage(applicationPackage)
                .build();
    }

    private String getServicesWithIdentity(String serviceName) {
        return "<services version='1.0'>" +
               "  <jdisc id='default' version='1.0'>" +
               "    <identity>" +
               "      <domain>domain</domain>" +
               "      <service>" + serviceName + "</service>" +
               "     </identity>" +
               "   </jdisc>" +
               "</services>";
    }
}
