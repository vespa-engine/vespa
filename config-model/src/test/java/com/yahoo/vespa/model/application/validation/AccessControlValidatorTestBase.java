// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static com.yahoo.config.provision.Environment.prod;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public abstract class AccessControlValidatorTestBase {

    protected Validator validator;
    protected Zone zone;

    @Rule
    public final ExpectedException exceptionRule = ExpectedException.none();

    private static String servicesXml(boolean addHandler, boolean protection) {
        return joinLines("<services version='1.0'>",
                         "  <container id='default' version='1.0'>",
                         addHandler ? httpHandlerXml : "",
                         "    <http>",
                         "      <filtering>",
                         "        <access-control domain='foo' read='" + protection + "' write='" + protection + "' />",
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
    public void cluster_with_protection_passes_validation() throws IOException, SAXException {
        DeployState deployState = deployState(servicesXml(true, true));
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);

        validator.validate(model, deployState);
    }

    @Test
    public void cluster_with_no_handlers_passes_validation_without_protection() throws IOException, SAXException{
        DeployState deployState = deployState(servicesXml(false, false));
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);

        validator.validate(model, deployState);
    }

    @Test
    public void cluster_without_custom_components_passes_validation_without_protection() throws IOException, SAXException{
        String servicesXml = joinLines("<services version='1.0'>",
                                       "  <container id='default' version='1.0' />",
                                       "</services>");
        DeployState deployState = deployState(servicesXml);
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);

        validator.validate(model, deployState);
    }

    @Test
    public void cluster_with_handler_fails_validation_without_protection() throws IOException, SAXException{
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(containsString("Access-control must be enabled"));
        exceptionRule.expectMessage(containsString("production zones: [default]"));

        DeployState deployState = deployState(servicesXml(true, false));
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);

        validator.validate(model, deployState);
    }

    @Test
    public void no_http_element_has_same_effect_as_no_write_protection() throws IOException, SAXException{
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(containsString("Access-control must be enabled"));
        exceptionRule.expectMessage(containsString("production zones: [default]"));

        String servicesXml = joinLines("<services version='1.0'>",
                                       "  <container id='default' version='1.0'>",
                                       httpHandlerXml,
                                       "  </container>",
                                       "</services>");
        DeployState deployState = deployState(servicesXml);
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);

        validator.validate(model, deployState);
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

        validator.validate(model, deployState);
    }

    @Test
    public void write_protection_is_not_required_for_non_default_application_type() throws IOException, SAXException{
        String servicesXml = joinLines("<services version='1.0' application-type='hosted-infrastructure'>",
                                       "  <container id='default' version='1.0'>",
                                       httpHandlerXml,
                                       "  </container>",
                                       "</services>");
        DeployState deployState = deployState(servicesXml);
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);

        validator.validate(model, deployState);
    }

    @Test
    public void write_protection_is_not_required_with_validation_override() throws IOException, SAXException{
        DeployState deployState = deployState(servicesXml(true, false),
                                              "<validation-overrides><allow until='2000-01-30'>access-control</allow></validation-overrides>",
                                              LocalDate.parse("2000-01-01", DateTimeFormatter.ISO_DATE).atStartOfDay().atZone(ZoneOffset.UTC).toInstant());
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);

        validator.validate(model, deployState);
    }

    private DeployState deployState(String servicesXml) {
        return deployState(servicesXml, "<validation-overrides></validation-overrides>", Instant.now());
    }

    private DeployState deployState(String servicesXml, String validationOverrides, Instant now) {
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withServices(servicesXml)
                .withValidationOverrides(validationOverrides)
                .build();

        DeployState.Builder builder = new DeployState.Builder()
                .applicationPackage(app)
                .zone(zone)
                .properties(new TestProperties().setHostedVespa(true))
                .now(now);
        final DeployState deployState = builder.build();

        assertTrue("Test must emulate a hosted deployment.", deployState.isHosted());
        assertEquals("Test must emulate a prod environment.", prod, deployState.zone().environment());

        return deployState;
    }

}
