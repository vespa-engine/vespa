// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.ValidationTester;
import com.yahoo.yolean.Exceptions;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests validation of removal of a document type.
 * Uses the types "music" and "book" as those are supported by the ValidationTester.
 *
 * @author bratseth
 */
public class ContentTypeRemovalValidatorTest {

    @Test
    public void testContentTypeRemovalValidation() {
        ValidationTester tester = new ValidationTester();

        VespaModel previous = tester.deploy(null, getServices("music"), null).getFirst();
        try {
            tester.deploy(previous, getServices("book"), null);
            fail("Expected exception due to removal of context type 'music");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("content-type-removal: Type 'music' is removed  in content cluster 'test'. " +
                         "This will cause loss of all data of this type. " +
                         ValidationOverrides.toAllowMessage(ValidationId.contentTypeRemoval),
                         Exceptions.toMessageString(expected));
        }
    }

    @Test
    public void testOverridingContentTypeRemovalValidation() {
        ValidationTester tester = new ValidationTester();

        VespaModel previous = tester.deploy(null, getServices("music"), null).getFirst();
        tester.deploy(previous, getServices("book"), removalOverride); // Allowed due to override
    }

    private static String getServices(String documentType) {
        return "<services version='1.0'>" +
               "  <content id='test' version='1.0'>" +
               "    <redundancy>1</redundancy>" +
               "    <engine>" +
               "    <proton/>" +
               "    </engine>" +
               "    <documents>" +
               "      <document type='" + documentType + "' mode='index'/>" +
               "    </documents>" +
               "    <nodes count='1'/>" +
               "   </content>" +
               "</services>";
    }

    private static final String removalOverride =
            "<validation-overrides>\n" +
            "    <allow until='2000-01-03'>content-type-removal</allow>\n" +
            "</validation-overrides>\n";

}
