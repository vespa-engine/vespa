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

    private static final String emptyConfigElement = "<?xml version='1.0' encoding='utf-8' ?>\n" +
                                                     "<services>\n" +
                                                     "  <config name='standard'>\n" +
                                                     "  </config>\n" +
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

    @Test
    public void testXMLParseErrorEmptyElement() throws IOException {
        SchemaValidator validator = createValidator();
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(expectedErrorMessageForEmptyConfigElement("services.xml"));
        validator.validate(new InputSource(new StringReader(emptyConfigElement)), "services.xml");
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

    private String expectedErrorMessageForEmptyConfigElement(String input) {
        return "Invalid XML according to XML schema, error in " + input + ": element \"config\" incomplete [4:12], input:\n" +
               "1:<?xml version='1.0' encoding='utf-8' ?>\n" +
               "2:<services>\n" +
               "3:  <config name='standard'>\n" +
               "4:  </config>\n" +
               "5:  <admin version='2.0'>\n" +
               "6:    <adminserver hostalias='node1'>\n" +
               "7:  </admin>\n";
    }

}
