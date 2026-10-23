// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.component.Version;
import com.yahoo.config.model.api.AdditionalContent.DocumentTypeDeclaration;
import com.yahoo.config.model.api.AdditionalContent.Mode;
import com.yahoo.config.model.application.provider.SchemaValidators;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.derived.SchemaInfo;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AdditionalContentTest {

    @Test
    void everyDeclaredTypeMustHaveASchemaFileOfTheSameName() {
        Throwable thrown = assertThrows(IllegalArgumentException.class,
                                        () -> new AdditionalContent(List.of(schema("product")),
                                                                    List.of(declaration("product"), declaration("facet"), declaration("rule"))));
        assertEquals("Additional document declarations [facet, rule] have no schema among the additional schemas [product]: " +
                     "a declared type must have a schema file named '<type>.sd' supplied with it",
                     thrown.getMessage());
    }

    @Test
    void noneIsEmpty() {
        assertTrue(AdditionalContent.none().isEmpty());
        assertFalse(new AdditionalContent(List.of(schema("product")), List.of()).isEmpty());
    }

    @Test
    void modeMatchesSchemaInfoIndexMode() {
        assertEquals(SchemaInfo.IndexMode.values().length, Mode.values().length);
        for (Mode mode : Mode.values())
            assertEquals(mode.name(), indexModeOf(mode.xmlValue()).name(), "AdditionalContent.Mode." + mode);
    }

    @Test
    void modeMatchesTheServicesXmlSchema() {
        for (Mode mode : Mode.values())
            validate(servicesWithMode(mode.xmlValue()));
        assertThrows(IllegalArgumentException.class, () -> validate(servicesWithMode("bogus")), "the schema rejects unknown modes");
    }

    private static NamedReader schema(String name) {
        return new NamedReader(name + ".sd", new StringReader("schema " + name + " { document " + name + " {} }"));
    }

    private static DocumentTypeDeclaration declaration(String type) {
        return new DocumentTypeDeclaration(type, Mode.INDEX, true);
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
