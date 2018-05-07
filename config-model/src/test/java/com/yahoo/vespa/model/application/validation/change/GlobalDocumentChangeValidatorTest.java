// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ConfigChangeRefeedAction;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.ValidationTester;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * Test that global attribute changes are detected by change validator.
 */
public class GlobalDocumentChangeValidatorTest {

    @Test
    public void testChangGlobalAttribute() throws IOException, SAXException {
        testChangeGlobalAttribute(true, false, false, null);
        testChangeGlobalAttribute(true, true, true, null);
        testChangeGlobalAttribute(false, false, true, null);
        testChangeGlobalAttribute(false, true, false, null);
        testChangeGlobalAttribute(true, false, true, globalDocumentValidationOverrides);
        testChangeGlobalAttribute(true, true, false, globalDocumentValidationOverrides);
    }

    private void testChangeGlobalAttribute(boolean allowed, boolean oldGlobal, boolean newGlobal, String validationOverrides) {
        ValidationTester tester = new ValidationTester();
        VespaModel oldModel = tester.deploy(null, getServices(oldGlobal), validationOverrides).getFirst();
        try {
            List<ConfigChangeAction> changeActions =
                    tester.deploy(oldModel, getServices(newGlobal), validationOverrides).getSecond();
            assertTrue(allowed);
        } catch (IllegalStateException e) {
            assertFalse(allowed);
            assertEquals("Document type music in cluster default changed global from " + oldGlobal + " to " + newGlobal,
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
