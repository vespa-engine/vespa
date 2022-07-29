// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ValidationParameters;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.model.VespaModel;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author hmusum
 */
public class ValidationOverridesValidatorTest {

    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                                                                                .withZone(ZoneId.systemDefault());

    @Test
    void testValidationOverride() throws IOException, SAXException {
        String tenDays = dateTimeFormatter.format(Instant.now().plus(Duration.ofDays(10)));

        var validationOverridesXml = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "  <validation-overrides>\n" +
                "    <allow until='" + tenDays + "'>deployment-removal</allow>\n" +
                "  </validation-overrides>";

        var deployState = createDeployState(validationOverridesXml);
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
        new Validation().validate(model, new ValidationParameters(), deployState);
    }

    @Test
    void testFailsWhenValidationOverrideIsTooFarInFuture() {
        Instant now = Instant.now();
        String sixtyDays = dateTimeFormatter.format(now.plus(Duration.ofDays(60)));
        String sixtyOneDays = dateTimeFormatter.format(now.plus(Duration.ofDays(61)));

        var validationOverrides = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<validation-overrides>\n" +
                "    <allow until='" + sixtyDays + "'>deployment-removal</allow>\n" +
                "</validation-overrides>";
        assertValidationError("validation-overrides is invalid: allow 'deployment-removal' until " +
                sixtyOneDays + "T00:00:00Z is too far in the future: Max 30 days is allowed", validationOverrides);
    }

    private static void assertValidationError(String message, String validationOverridesXml) {
        try {
            var deployState = createDeployState(validationOverridesXml);
            VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
            new Validation().validate(model, new ValidationParameters(), deployState);
            fail("Did not get expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals(message, e.getMessage());
        } catch (SAXException|IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static DeployState createDeployState(String validationOverridesXml) {
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
                .withValidationOverrides(validationOverridesXml)
                .withServices(services)
                .build();
        var builder = new DeployState.Builder().applicationPackage(app);
        return builder.build();
    }

}

