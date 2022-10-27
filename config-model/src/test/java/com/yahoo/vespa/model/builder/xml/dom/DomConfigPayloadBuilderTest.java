// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.codegen.DefParser;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.slime.JsonFormat;
import com.yahoo.vespa.config.ConfigDefinition;
import com.yahoo.vespa.config.ConfigDefinitionBuilder;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.ConfigPayloadBuilder;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link com.yahoo.vespa.model.builder.xml.dom.DomConfigPayloadBuilder} class.
 *
 * @author gjoranv
 * @author Ulf Lilleengen
 */
public class DomConfigPayloadBuilderTest {

    @Test
    void testFunctionTest_DefaultValues() throws FileNotFoundException {
        Element configRoot = getDocument(new FileReader("src/test/cfg/admin/userconfigs/functiontest-defaultvalues.xml"));
        String expected = ""
                + "{"
                + "\"bool_val\":\"false\","
                + "\"int_val\":\"5\","
                + "\"long_val\":\"1234567890123\","
                + "\"double_val\":\"41.23\","
                + "\"string_val\":\"foo\","
                + "\"enum_val\":\"FOOBAR\","
                + "\"refval\":\":parent:\","
                + "\"fileVal\":\"vespa.log\","
                + "\"basicStruct\":{\"bar\":\"3\",\"intArr\":[\"10\"]},"
                + "\"rootStruct\":{\"inner0\":{\"index\":\"11\"},\"inner1\":{\"index\":\"12\"},"
                + "\"innerArr\":[{\"stringVal\":\"deep\"}]},"
                + "\"boolarr\":[\"false\"],"
                + "\"doublearr\":[\"2344\",\"123\"],"
                + "\"stringarr\":[\"bar\"],"
                + "\"enumarr\":[\"VALUES\"],"
                + "\"myarray\":[{\"refval\":\":parent:\",\"fileVal\":\"command.com\",\"myStruct\":{\"a\":\"1\"},\"stringval\":[\"baah\",\"yikes\"],\"anotherarray\":[{\"foo\":\"7\"}]},{\"refval\":\":parent:\",\"fileVal\":\"display.sys\",\"myStruct\":{\"a\":\"-1\"},\"anotherarray\":[{\"foo\":\"1\"},{\"foo\":\"2\"}]}]"
                + "}";
        assertPayload(expected, configRoot);
    }

    // Multi line strings are not tested in 'DefaultValues', so here it is.
    @Test
    void verifyThatWhitespaceIsPreservedForStrings() throws Exception {
        Element configRoot = getDocument(new FileReader("src/test/cfg/admin/userconfigs/whitespace-test.xml"));
        assertPayload("{\"stringVal\":\" This is a string\\n  that contains different kinds of whitespace \"}", configRoot);
    }

    @Test
    void put_to_leaf_map() {
        Reader xmlConfig = new StringReader("<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
                "<config name=\"test.foobar\">" +
                "  <intmap>" +
                "    <item key=\"bar\">1338</item>" +
                "    <item key=\"foo\">1337</item>" +
                "  </intmap>" +
                "</config>");
        assertPayload("{\"intmap\":{\"bar\":\"1338\",\"foo\":\"1337\"}}", getDocument(xmlConfig));
    }

    @Test
    void put_to_inner_map() {
        Reader xmlConfig = new StringReader("<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
                "<config name=\"test.foobar\">" +
                "  <innermap>" +
                "    <item key=\"bar\">" +
                "      <foo>baz</foo>" +
                "    </item>" +
                "    <item key=\"foo\">" +
                "      <foo>bar</foo>" +
                "    </item>" +
                "  </innermap>" +
                "</config>");
        assertPayload("{\"innermap\":{\"bar\":{\"foo\":\"baz\"},\"foo\":{\"foo\":\"bar\"}}}", getDocument(xmlConfig));
    }

    @Test
    void put_to_nested_map() {
        Reader xmlConfig = new StringReader("<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
                "<config name=\"test.foobar\">" +
                "  <nestedmap>" +
                "    <item key=\"bar\">" +
                "      <inner>" +
                "        <item key=\"bar1\">30</item>" +
                "        <item key=\"bar2\">40</item>" +
                "      </inner>" +
                "    </item>" +
                "    <item key=\"foo\">" +
                "      <inner>" +
                "        <item key=\"foo1\">10</item>" +
                "        <item key=\"foo2\">20</item>" +
                "      </inner>" +
                "    </item>" +
                "  </nestedmap>" +
                "</config>");
        assertPayload("{\"nestedmap\":{" +
                "\"bar\":{\"inner\":{\"bar1\":\"30\",\"bar2\":\"40\"}}," +
                "\"foo\":{\"inner\":{\"foo1\":\"10\",\"foo2\":\"20\"}}}}", getDocument(xmlConfig));
    }

    @Test
    void camel_case_via_dashes() {
        Reader xmlConfig = new StringReader("<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
                "<config name=\"test.function-test\">" +
                "  <some-struct> <any-value>17</any-value> </some-struct>" +
                "</config> ");
        assertPayload("{\"someStruct\":{\"anyValue\":\"17\"}}", getDocument(xmlConfig));
    }

    // Verifies that an exception is thrown when the root element is not 'config'.
    @Test
    void testFailWrongTagName() {
        Element configRoot = getDocument(new StringReader("<configs name=\"foo\"/>"));
        try {
            new DomConfigPayloadBuilder(null).build(configRoot);
            fail("Expected exception for wrong tag name.");
        } catch (IllegalArgumentException e) {
            assertEquals("The root element must be 'config', but was 'configs'", e.getMessage());
        }
    }

    // Verifies that an exception is thrown when the root element is not 'config'.
    @Test
    void testFailNoNameAttribute() {
        Element configRoot = getDocument(new StringReader("<config/>"));
        try {
            new DomConfigPayloadBuilder(null).build(configRoot);
            fail("Expected exception for mismatch between def-name and xml name attribute.");
        } catch (IllegalArgumentException e) {
            assertEquals("The 'config' element must have a 'name' attribute that matches the name of the config definition", e.getMessage());
        }
    }

    @Test
    void testNameParsing() {
        Element configRoot = getDocument(new StringReader("<config name=\"test.function-test\" version=\"1\">" +
                "<int_val>1</int_val> +" +
                "</config>"));
        ConfigDefinitionKey key = DomConfigPayloadBuilder.parseConfigName(configRoot);
        assertEquals("function-test", key.getName());
        assertEquals("test", key.getNamespace());
    }

    @Test
    void testNameParsingInvalidName() {
        assertThrows(IllegalArgumentException.class, () -> {
            Element configRoot = getDocument(new StringReader("<config name=\" function-test\" version=\"1\">" +
                    "<int_val>1</int_val> +" +
                    "</config>"));
            DomConfigPayloadBuilder.parseConfigName(configRoot);
        });
    }

    @Test
    void testNameParsingInvalidNamespace() {
        assertThrows(IllegalArgumentException.class, () -> {
            Element configRoot = getDocument(new StringReader("<config name=\"_foo.function-test\" version=\"1\">" +
                    "<int_val>1</int_val> +" +
                    "</config>"));
            DomConfigPayloadBuilder.parseConfigName(configRoot);
        });
    }

    @Test
    void require_that_item_syntax_works_with_leaf() {
        Element configRoot = getDocument(
                "<config name=\"test.arraytypes\" version=\"1\">" +
                        "    <intarr>" +
                        "        <item>13</item>" +
                        "        <item>10</item>" +
                        "        <item>1337</item>" +
                        "    </intarr>" +
                        "</config>");

        assertPayload("{\"intarr\":[\"13\",\"10\",\"1337\"]}", configRoot);
    }

    @Test
    void require_that_item_syntax_works_with_struct() {
        Element configRoot = getDocument(
                "<config name=\"test.arraytypes\" version=\"1\">" +
                        "    <lolarray>" +
                        "        <item><foo>hei</foo><bar>hei2</bar></item>" +
                        "        <item><foo>hoo</foo><bar>hoo2</bar></item>" +
                        "        <item><foo>happ</foo><bar>happ2</bar></item>" +
                        "    </lolarray>" +
                        "</config>");

        assertPayload("{\"lolarray\":[{\"foo\":\"hei\",\"bar\":\"hei2\"},{\"foo\":\"hoo\",\"bar\":\"hoo2\"},{\"foo\":\"happ\",\"bar\":\"happ2\"}]}",
                configRoot);
    }

    @Test
    void require_that_item_syntax_works_with_struct_array() {
        Element configRoot = getDocument(
                "<config name=\"test.arraytypes\" version=\"1\">" +
                        "    <lolarray>" +
                        "        <item><fooarray><item>13</item></fooarray></item>" +
                        "        <item><fooarray><item>10</item></fooarray></item>" +
                        "        <item><fooarray><item>1337</item></fooarray></item>" +
                        "    </lolarray>" +
                        "</config>");

        assertPayload("{\"lolarray\":[{\"fooarray\":[\"13\"]},{\"fooarray\":[\"10\"]},{\"fooarray\":[\"1337\"]}]}", configRoot);
    }

    @Test
    void require_that_item_is_reserved_in_root() {
        assertThrows(IllegalArgumentException.class, () -> {
            Element configRoot = getDocument(
                    "<config name=\"test.arraytypes\" version=\"1\">" +
                            "    <item>13</item>" +
                            "</config>");
            new DomConfigPayloadBuilder(null).build(configRoot);
        });
    }

    @Test
    void require_that_exceptions_are_issued() throws FileNotFoundException {
        assertThrows(IllegalArgumentException.class, () -> {
            Element configRoot = getDocument(
                    "<config name=\"test.simpletypes\">" +
                            "<longval>invalid</longval>" +
                            "</config>");
            DefParser defParser = new DefParser("simpletypes",
                    new FileReader("src/test/resources/configdefinitions/test.simpletypes.def"));
            ConfigDefinition def = ConfigDefinitionBuilder.createConfigDefinition(defParser.getTree());
            ConfigPayloadBuilder unused =  new DomConfigPayloadBuilder(def).build(configRoot);
        });
    }

    private void assertPayload(String expected, Element configRoot) {
        ConfigPayload payload = ConfigPayload.fromBuilder(
                new DomConfigPayloadBuilder(null).build(configRoot));
        try {
            ByteArrayOutputStream a = new ByteArrayOutputStream();
            new JsonFormat(true).encode(a, payload.getSlime());
            assertEquals(expected, a.toString());
        } catch (Exception e) {
            fail("Exception thrown when encoding slime: " + e.getMessage());
        }
    }

    private Element getDocument(Reader xmlReader) {
        Document doc;
        try {
            doc = XmlHelper.getDocument(xmlReader);
        } catch (Exception e) {
            throw new RuntimeException();
        }
        return doc.getDocumentElement();
    }

    private Element getDocument(String xml) {
        Reader xmlReader = new StringReader(xml);
        return getDocument(xmlReader);
    }
}
