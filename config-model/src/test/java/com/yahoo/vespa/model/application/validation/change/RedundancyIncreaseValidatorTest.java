// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.ValidationTester;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
public class RedundancyIncreaseValidatorTest {

    private final ValidationTester tester = new ValidationTester(7);

    @Test
    void testRedundancyIncreaseValidation() {
        VespaModel previous = tester.deploy(null, getServices(2, 3, 1), Environment.prod, null, "contentClusterId.indexing").getFirst();
        try {
            tester.deploy(previous, getServices(3, 3, 1), Environment.prod, null, "contentClusterId.indexing");
            fail("Expected exception due to redundancy increase");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("redundancy-increase: " +
                    "Increasing redundancy from 2 to 3 in 'content cluster 'contentClusterId'. " +
                    "This is a safe operation but verify that you have room for a 3/2x increase in content size. " +
                    ValidationOverrides.toAllowMessage(ValidationId.redundancyIncrease),
                    Exceptions.toMessageString(expected));
        }
    }

    @Test
    void testRedundancyIncreaseValidationDoesNotTriggerWhenTotalCopiesAreNotIncreased() {
        VespaModel previous = tester.deploy(null, getServices(1, 2, 2), Environment.prod, null, "contentClusterId.indexing").getFirst();
        tester.deploy(previous, getServices(2, 3, 1), Environment.prod, null, "contentClusterId.indexing");
    }

    @Test
    void testOverridingContentRemovalValidation() {
        VespaModel previous = tester.deploy(null, getServices(2, 3, 1), Environment.prod, null, "contentClusterId.indexing").getFirst();
        tester.deploy(previous, getServices(3, 3, 1), Environment.prod, redundancyIncreaseOverride, "contentClusterId.indexing"); // Allowed due to override
    }

    private static String getServices(int redundancy, int nodes, int groups) {
        return "<services version='1.0'>" +
               "  <content id='contentClusterId' version='1.0'>" +
               "    <redundancy>" + redundancy + "</redundancy>" +
               "    <engine>" +
               "    <proton/>" +
               "    </engine>" +
               "    <documents>" +
               "      <document type='music' mode='index'/>" +
               "    </documents>" +
               "    <nodes count='" + nodes + "' groups='" + groups + "'/>" +
               "   </content>" +
               "</services>";
    }

    private static final String redundancyIncreaseOverride =
            "<validation-overrides>\n" +
            "    <allow until='2000-01-03'>redundancy-increase</allow>\n" +
            "</validation-overrides>\n";

}
