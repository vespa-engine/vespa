// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.component.Version;
import com.yahoo.config.model.api.AdditionalDocuments.Mode;
import com.yahoo.config.model.application.provider.SchemaValidators;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.derived.SchemaInfo;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AdditionalDocumentsTest {

    @Test
    void modeMatchesSchemaInfoIndexMode() {
        assertEquals(SchemaInfo.IndexMode.values().length, Mode.values().length);
        for (Mode mode : Mode.values())
            assertEquals(mode.name(), indexModeOf(mode.xmlValue()).name(), "AdditionalDocuments.Mode." + mode);
    }

    @Test
    void modeMatchesTheServicesXmlSchema() {
        for (Mode mode : Mode.values())
            validate(servicesWithMode(mode.xmlValue()));
        assertThrows(IllegalArgumentException.class, () -> validate(servicesWithMode("bogus")), "the schema rejects unknown modes");
    }

    private static SchemaInfo.IndexMode indexModeOf(String xmlValue) {
        Schema schema = new Schema("test", MockApplicationPackage.createEmpty());
        return new SchemaInfo(schema, xmlValue, new RankProfileRegistry(), null).getIndexMode();
    }

    private static void validate(String services) {
        try {
            new SchemaValidators(new Version(8)).servicesXmlValidator().validate(new StringReader(services));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static String servicesWithMode(String mode) {
        return "<services version='1.0'>" +
               "  <content id='content' version='1.0'>" +
               "    <redundancy>1</redundancy>" +
               "    <documents><document type='test' mode='" + mode + "'/></documents>" +
               "    <nodes><node hostalias='node1' distribution-key='0'/></nodes>" +
               "  </content>" +
               "</services>";
    }

}
