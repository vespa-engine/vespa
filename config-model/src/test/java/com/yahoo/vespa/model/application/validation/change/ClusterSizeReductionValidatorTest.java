// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ConfigChangeRefeedAction;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.ValidationTester;
import com.yahoo.vespa.model.search.AbstractSearchCluster;
import com.yahoo.yolean.Exceptions;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class ClusterSizeReductionValidatorTest {

    @Test
    public void testSizeReductionValidation() throws IOException, SAXException {
        ValidationTester tester = new ValidationTester(30);

        VespaModel previous = tester.deploy(null, getServices(30), null).getFirst();
        try {
            tester.deploy(previous, getServices(14), null);
            fail("Expected exception due to cluster size reduction");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("cluster-size-reduction: Size reduction in 'default' is too large. Current size: 30, new size: 14. New size must be at least 50% of the current size. " +
                         ValidationOverrides.toAllowMessage(ValidationId.clusterSizeReduction),
                         Exceptions.toMessageString(expected));
        }
    }

    @Test
    public void testSizeReductionValidationMinimalDecreaseIsAllowed() throws IOException, SAXException {
        ValidationTester tester = new ValidationTester(30);

        VespaModel previous = tester.deploy(null, getServices(3), null).getFirst();
        tester.deploy(previous, getServices(2), null);
    }

    /*
    @Test
    public void testSizeReductionTo50PercentIsAllowed() throws IOException, SAXException {
        ValidationTester tester = new ValidationTester(30);

        VespaModel previous = tester.deploy(null, getServices(30), null).getFirst();
        tester.deploy(previous, getServices(15), null);
    }
    */

    @Test
    public void testOverridingSizereductionValidation() throws IOException, SAXException {
        ValidationTester tester = new ValidationTester(30);

        VespaModel previous = tester.deploy(null, getServices(30), null).getFirst();
        tester.deploy(previous, getServices(14), sizeReductionOverride); // Allowed due to override
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
