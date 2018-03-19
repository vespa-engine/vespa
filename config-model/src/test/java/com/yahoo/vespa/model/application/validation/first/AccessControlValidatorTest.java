// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.first;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.deploy.DeployProperties;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.xml.sax.SAXException;

import java.io.IOException;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static com.yahoo.config.provision.Environment.prod;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class AccessControlValidatorTest  {

    @Rule
    public final ExpectedException exceptionRule = ExpectedException.none();

    private static String servicesXml(boolean addHandler, boolean writeProtection) {
        return joinLines("<services version='1.0'>",
                         "  <container id='default' version='1.0'>",
                         addHandler ? httpHandlerXml : "",
                         "    <http>",
                         "      <filtering>",
                         "        <access-control domain='foo' write='" + writeProtection + "' />",
                         "      </filtering>",
                         "    </http>",
                         "  </container>",
                         "</services>");
    }

    private static final String httpHandlerXml =
            joinLines("    <handler id='foo'>",
                      "      <binding>http://foo/bar</binding>",
                      "    </handler>");

    @Test
    public void cluster_with_write_protection_passes_validation() throws IOException, SAXException{
        DeployState deployState = deployState(servicesXml(true, true));
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);

        new AccessControlValidator().validate(model, deployState);
    }

    @Test
    public void cluster_with_no_handlers_passes_validation_without_write_protection() throws IOException, SAXException{
        DeployState deployState = deployState(servicesXml(false, false));
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);

        new AccessControlValidator().validate(model, deployState);
    }

    @Test
    public void cluster_without_custom_components_passes_validation_without_write_protection() throws IOException, SAXException{
        String servicesXml = joinLines("<services version='1.0'>",
                                       "  <container id='default' version='1.0' />",
                                       "</services>");
        DeployState deployState = deployState(servicesXml);
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);

        new AccessControlValidator().validate(model, deployState);
    }

    @Test
    public void cluster_with_handler_fails_validation_without_write_protection() throws IOException, SAXException{
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(
                "Access-control must be enabled for write operations to container clusters in production zones: [default]");

        DeployState deployState = deployState(servicesXml(true, false));
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);

        new AccessControlValidator().validate(model, deployState);

    }

    @Test
    public void no_http_element_has_same_effect_as_no_write_protection() throws IOException, SAXException{
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(
                "Access-control must be enabled for write operations to container clusters in production zones: [default]");

        String servicesXml = joinLines("<services version='1.0'>",
                                       "  <container id='default' version='1.0'>",
                                       httpHandlerXml,
                                       "  </container>",
                                       "</services>");
        DeployState deployState = deployState(servicesXml);
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);

        new AccessControlValidator().validate(model, deployState);
    }

    @Test
    public void cluster_with_mbus_handler_passes_validation_without_write_protection() throws IOException, SAXException{
        String servicesXml = joinLines("<services version='1.0'>",
                                       "  <container id='default' version='1.0'>",
                                       "    <handler id='foo'>",
                                       "      <binding>mbus://*/foo</binding>",
                                       "    </handler>",
                                       "  </container>",
                                       "</services>");
        DeployState deployState = deployState(servicesXml);
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);

        new AccessControlValidator().validate(model, deployState);
    }

    private static DeployState deployState(String servicesXml) {
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withServices(servicesXml)
                .build();

        DeployState.Builder builder = new DeployState.Builder()
                .applicationPackage(app)
                .properties(new DeployProperties.Builder()
                                    .hostedVespa(true)
                                    .build());
        final DeployState deployState = builder.build(true);

        assertTrue("Test must emulate a hosted deployment.", deployState.isHosted());
        assertEquals("Test must emulate a prod environment.", prod, deployState.zone().environment());

        return deployState;
    }

}
