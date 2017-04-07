// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.component.Version;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;

/**
 * @author hmusum
 * @since 5.1.9
 */
public class SchemaValidatorTest {

    private static final String okServices = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<services>" +
            "  <config name=\"standard\">" +
            "    <basicStruct>" +
            "      <stringVal>default</stringVal>" +
            "    </basicStruct>" +
            "  </config> " +
            "  <admin version=\"2.0\">" +
            "    <adminserver hostalias=\"node1\" />" +
            "  </admin>" +
            "</services>";

    private static final String badServices = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<services>" +
            "  <config name=\"standard\">" +
            "    <basicStruct>" +
            "      <stringVal>default</stringVal>" +
            "    </basicStruct>" +
            "  </config> " +
            "  <admin version=\"2.0\">" +
            "    <adminserver hostalias=\"node1\"" +
            "  </admin>" +
            "</services>";


    @Test
    public void testXMLParse() throws SAXException, IOException {
        SchemaValidator validator = createValidator();
        validator.validate(new InputSource(new StringReader(okServices)), "services.xml");
    }

    @Test(expected = RuntimeException.class)
    public void testXMLParseError() throws SAXException, IOException {
        SchemaValidator validator = createValidator();
        validator.validate(new InputSource(new StringReader(badServices)), "services.xml");
    }

    @Test
    public void testXMLParseWithReader() throws SAXException, IOException {
        SchemaValidator validator = createValidator();
        validator.validate(new StringReader(okServices));
    }

    @Test(expected = RuntimeException.class)
    public void testXMLParseErrorWithReader() throws SAXException, IOException {
        SchemaValidator validator = createValidator();
        validator.validate(new StringReader(badServices));
    }

    private SchemaValidator createValidator() throws IOException {
        return SchemaValidator.createTestValidatorServices(new Version(6));
    }
}
