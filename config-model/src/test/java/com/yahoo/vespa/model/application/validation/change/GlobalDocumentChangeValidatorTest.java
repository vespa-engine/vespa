// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.ValidationTester;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that global attribute changes are detected by change validator.
 */
public class GlobalDocumentChangeValidatorTest {

    @Test
    void testChangGlobalAttribute() {
        testChangeGlobalAttribute(true, false, false, null);
        testChangeGlobalAttribute(true, true, true, null);
        testChangeGlobalAttribute(false, false, true, null);
        testChangeGlobalAttribute(false, true, false, null);
        testChangeGlobalAttribute(true, false, true, globalDocumentValidationOverrides);
        testChangeGlobalAttribute(true, true, false, globalDocumentValidationOverrides);
    }

    private void testChangeGlobalAttribute(boolean allowed, boolean oldGlobal, boolean newGlobal, String validationOverrides) {
        ValidationTester tester = new ValidationTester();
        VespaModel oldModel = tester.deploy(null, getServices(oldGlobal), Environment.prod, validationOverrides).getFirst();
        try {
            tester.deploy(oldModel, getServices(newGlobal), Environment.prod, validationOverrides).getSecond();
            assertTrue(allowed);
        } catch (IllegalStateException e) {
            assertFalse(allowed);
            assertEquals("Document type music in cluster default changed global from " + oldGlobal + " to " + newGlobal + ". " +
                    "Add validation override 'global-document-change' to force this change through. " +
                    "First, stop services on all content nodes. Then, deploy with validation override. Finally, start services on all content nodes.",
                    e.getMessage());
        }
    }
    private static final String getServices(boolean isGlobal) {
        return "<services version='1.0'>" +
                "  <content id='default' version='1.0'>" +
                "    <redundancy>1</redundancy>" +
                "    <documents>" +
                "      <document type='music' mode='index' global='" +
                isGlobal + "'/>" +
                "    </documents>" +
                "    <nodes count='1'/>" +
                "   </content>" +
                "</services>";
    }

    private static final String globalDocumentValidationOverrides =
            "<validation-overrides>\n" +
                    "    <allow until='2000-01-14' comment='test override'>global-document-change</allow>\n" +
                    "</validation-overrides>\n";

}
