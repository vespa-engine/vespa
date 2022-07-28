// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
public class ClusterSizeReductionValidatorTest {

    @Test
    void testSizeReductionValidation() {
        ValidationTester tester = new ValidationTester(33);

        VespaModel previous = tester.deploy(null, getServices(30), Environment.prod, null).getFirst();
        try {
            tester.deploy(previous, getServices(14), Environment.prod, null);
            fail("Expected exception due to cluster size reduction");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("cluster-size-reduction: Size reduction in 'default' is too large: " +
                    "New min size must be at least 50% of the current min size. " +
                    "Current size: 30, new size: 14. " +
                    ValidationOverrides.toAllowMessage(ValidationId.clusterSizeReduction),
                    Exceptions.toMessageString(expected));
        }
    }

    @Test
    void testSizeReductionValidationMinimalDecreaseIsAllowed() {
        ValidationTester tester = new ValidationTester(30);

        VespaModel previous = tester.deploy(null, getServices(3), Environment.prod, null).getFirst();
        tester.deploy(previous, getServices(2), Environment.prod, null);
    }

    @Test
    void testOverridingSizereductionValidation() {
        ValidationTester tester = new ValidationTester(33);

        VespaModel previous = tester.deploy(null, getServices(30), Environment.prod, null).getFirst();
        tester.deploy(previous, getServices(14), Environment.prod, sizeReductionOverride); // Allowed due to override
    }

    private static String getServices(int size) {
        return "<services version='1.0'>" +
               "  <content id='default' version='1.0'>" +
               "    <redundancy>1</redundancy>" +
               "    <engine>" +
               "    <proton/>" +
               "    </engine>" +
               "    <documents>" +
               "      <document type='music' mode='index'/>" +
               "    </documents>" +
               "    <nodes count='" + size + "'/>" +
               "   </content>" +
               "</services>";
    }

    private static final String sizeReductionOverride =
            "<validation-overrides>\n" +
            "    <allow until='2000-01-03'>cluster-size-reduction</allow>\n" +
            "</validation-overrides>\n";

}
