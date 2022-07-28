// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.first;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.model.application.validation.ValidationTester;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
public class RedundancyOnFirstDeploymentValidatorTest {

    private final ValidationTester tester = new ValidationTester(7, false,
                                                                 new TestProperties().setFirstTimeDeployment(true)
                                                                                     .setHostedVespa(true));

    @Test
    void testRedundancyOnFirstDeploymentValidation() {
        try {
            tester.deploy(null, getServices(1), Environment.prod, null);
            fail("Expected exception due to redundancy 1");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("redundancy-one: " +
                    "content cluster 'contentClusterId' has redundancy 1, which will cause it to lose data if a node fails. " +
                    "This requires an override on first deployment in a production zone. " +
                    ValidationOverrides.toAllowMessage(ValidationId.redundancyOne),
                    Exceptions.toMessageString(expected));
        }
    }

    @Test
    void testOverridingRedundancyOnFirstDeploymentValidation() {
        tester.deploy(null, getServices(1), Environment.prod, redundancyOneOverride); // Allowed due to override
    }

    private static String getServices(int redundancy) {
        return "<services version='1.0'>" +
               "  <content id='contentClusterId' version='1.0'>" +
               "    <redundancy>" + redundancy + "</redundancy>" +
               "    <engine>" +
               "    <proton/>" +
               "    </engine>" +
               "    <documents>" +
               "      <document type='music' mode='index'/>" +
               "    </documents>" +
               "    <nodes count='3'/>" +
               "   </content>" +
               "</services>";
    }

    private static final String redundancyOneOverride =
            "<validation-overrides>\n" +
            "    <allow until='2000-01-03'>redundancy-one</allow>\n" +
            "</validation-overrides>\n";

}
