// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.component.Version;
import com.yahoo.vespa.config.VespaVersion;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.StringReader;

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

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testXMLParse() throws IOException {
        SchemaValidator validator = createValidator();
        validator.validate(new InputSource(new StringReader(okServices)), "services.xml");
    }

    @Test
    public void testXMLParseError() throws IOException {
        SchemaValidator validator = createValidator();
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage(expectedErrorMessage("services.xml"));
        validator.validate(new InputSource(new StringReader(invalidServices)), "services.xml");
    }

    @Test
    public void testXMLParseWithReader() throws IOException {
        SchemaValidator validator = createValidator();
        validator.validate(new StringReader(okServices));
    }

    @Test
    public void testXMLParseErrorWithReader() throws IOException {
        SchemaValidator validator = createValidator();
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage(expectedErrorMessage("input"));
        validator.validate(new StringReader(invalidServices));
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
