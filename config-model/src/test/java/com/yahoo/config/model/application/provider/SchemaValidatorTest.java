// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.component.Version;
import com.yahoo.vespa.config.VespaVersion;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author hmusum
 */
public class SchemaValidatorTest {

    private static final String okServices = "<?xml version='1.0' encoding='utf-8' ?>\n" +
            "<services>\n" +
            "  <config name='standard'>\n" +
            "    <basicStruct>\n" +
            "      <stringVal>default</stringVal>\n" +
            "    </basicStruct>\n" +
            "  </config>\n" +
            "  <admin version='2.0'>\n" +
            "    <adminserver hostalias='node1' />\n" +
            "  </admin>\n" +
            "</services>\n";

    // Typo in closing end tag for <config> (<confih>)
    private static final String invalidServices = "<?xml version='1.0' encoding='utf-8' ?>\n" +
            "<services>\n" +
            "  <config name='standard'>\n" +
            "    <basicStruct>\n" +
            "      <stringVal>default</stringVal>\n" +
            "    </basicStruct>\n" +
            "  </confih>\n" +
            "  <admin version='2.0'>\n" +
            "    <adminserver hostalias='node1'>\n" +
            "  </admin>\n" +
            "</services>\n";

    @Test
    void testXMLParse() throws IOException {
        SchemaValidator validator = createValidator();
        validator.validate(new StringReader(okServices));
    }

    @Test
    void testXMLParseError() throws IOException {
        Throwable exception = assertThrows(RuntimeException.class, () -> {
            SchemaValidator validator = createValidator();
            validator.validate(new StringReader(invalidServices));
        });
        assertTrue(exception.getMessage().contains(expectedErrorMessage("input")));
    }

    @Test
    void testXMLParseWithReader() throws IOException {
        SchemaValidator validator = createValidator();
        validator.validate(new StringReader(okServices));
    }

    @Test
    void testXMLParseErrorWithReader() throws IOException {
        Throwable exception = assertThrows(RuntimeException.class, () -> {
            SchemaValidator validator = createValidator();
            validator.validate(new StringReader(invalidServices));
        });
        assertTrue(exception.getMessage().contains(expectedErrorMessage("input")));
    }

    @Test
    void testXMLParseErrorFromFile() throws IOException {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            SchemaValidator validator = createValidator();
            validator.validate(new File("src/test/cfg/application/invalid-services-syntax/services.xml"));
        });
        assertTrue(exception.getMessage().contains(expectedErrorMessage("services.xml")));
    }

    private SchemaValidator createValidator() {
        return new SchemaValidators(new Version(VespaVersion.major)).servicesXmlValidator();
    }

    private String expectedErrorMessage(String input) {
        return "Invalid XML according to XML schema, error in " + input + ": The element type \"config\" must be terminated by the matching end-tag \"</config>\". [7:5], input:\n" +
                "4:    <basicStruct>\n" +
                "5:      <stringVal>default</stringVal>\n" +
                "6:    </basicStruct>\n" +
                "7:  </confih>\n" +
                "8:  <admin version='2.0'>\n" +
                "9:    <adminserver hostalias='node1'>\n" +
                "10:  </admin>\n";
    }
}
