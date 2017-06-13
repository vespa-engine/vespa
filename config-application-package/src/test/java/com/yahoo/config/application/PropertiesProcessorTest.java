// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author hmusum
 */
public class PropertiesProcessorTest {

    static {
        XMLUnit.setIgnoreWhitespace(true);
    }

    @Test
    public void testPropertyValues() throws ParserConfigurationException, TransformerException, SAXException, IOException {
        String input = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"properties\" version=\"1.0\">" +
                "  <preprocess:properties>" +
                "    <slobrok.port>4099</slobrok.port>" +
                "    <redundancy>2</redundancy>" +
                "  </preprocess:properties>" +
                "  <admin version=\"2.0\">" +
                "    <adminserver hostalias=\"node0\"/>" +
                "    <slobroks>" +
                "      <slobrok hostalias=\"node1\" baseport=\"${slobrok.port}\"/>" +
                "    </slobroks>" +
                "  </admin>" +
                "</services>";

        PropertiesProcessor p = new PropertiesProcessor();
        p.process(Xml.getDocument(new StringReader(input)));
        Map<String, String> properties = p.getProperties();
        assertThat(properties.size(), is(2));
        assertThat(properties.get("slobrok.port"), is("4099"));
        assertThat(properties.get("redundancy"), is("2"));
    }

    @Test
    public void testPropertyApplying() throws IOException, SAXException, XMLStreamException, ParserConfigurationException, TransformerException {
        String input = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"properties\" version=\"1.0\">" +
                "  <preprocess:properties>" +
                "    <slobrok.port>4099</slobrok.port>" +
                "    <redundancy>2</redundancy>" +
                "    <doctype>music</doctype>" +
                "    <zero>0</zero>" +
                "  </preprocess:properties>" +
                "  <admin version=\"2.0\">" +
                "    <adminserver hostalias=\"node0\"/>" +
                "    <slobroks>" +
                "      <slobrok hostalias=\"node1\" baseport=\"${slobrok.port}\"/>" +
                "    </slobroks>" +
                "  </admin>" +
                "  <content id=\"foo\" version=\"1.0\">" +
                "    <redundancy>${redundancy}</redundancy>" +
                "    <documents>" +
                "      <document mode=\"index\" type=\"${doctype}.sd\"/>" +
                "    </documents>" +
                "    <nodes>" +
                "      <node distribution-key=\"${zero}\" hostalias=\"node${zero}\"/>" +
                "    </nodes>" +
                "  </content>" +
                "</services>";

        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"properties\" version=\"1.0\">" +
                "  <admin version=\"2.0\">" +
                "    <adminserver hostalias=\"node0\"/>" +
                "    <slobroks>" +
                "      <slobrok hostalias=\"node1\" baseport=\"4099\"/>" +
                "    </slobroks>" +
                "  </admin>" +
                "  <content id=\"foo\" version=\"1.0\">" +
                "    <redundancy>2</redundancy>" +
                "    <documents>" +
                "      <document mode=\"index\" type=\"music.sd\"/>" +
                "    </documents>" +
                "    <nodes>" +
                "      <node distribution-key=\"0\" hostalias=\"node0\"/>" +
                "    </nodes>" +
                "  </content>" +
                "</services>";


        Document inputDoc = Xml.getDocument(new StringReader(input));
        Document newDoc = new PropertiesProcessor().process(inputDoc);
        TestBase.assertDocument(expected, newDoc);
    }


    // TODO: Check that warning is actually logged
    @Test
    public void testWarnIfDuplicatePropertyForSameEnvironment() throws IOException, SAXException, XMLStreamException, ParserConfigurationException, TransformerException {
        String input = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"properties\" version=\"1.0\">" +
                "  <preprocess:properties>" +
                "    <slobrok.port>4099</slobrok.port>" +
                "    <slobrok.port>5000</slobrok.port>" +
                "    <redundancy>2</redundancy>" +
                "  </preprocess:properties>" +
                "  <admin version=\"2.0\">" +
                "    <adminserver hostalias=\"node0\"/>" +
                "    <slobroks>" +
                "      <slobrok hostalias=\"node1\" baseport=\"${slobrok.port}\"/>" +
                "    </slobroks>" +
                "  </admin>" +
                "</services>";


        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"properties\" version=\"1.0\">" +
                "  <admin version=\"2.0\">" +
                "    <adminserver hostalias=\"node0\"/>" +
                "    <slobroks>" +
                "      <slobrok hostalias=\"node1\" baseport=\"5000\"/>" + // Should get the last defined value
                "    </slobroks>" +
                "  </admin>" +
                "</services>";

        Document inputDoc = Xml.getDocument(new StringReader(input));
        Document newDoc = new PropertiesProcessor().process(inputDoc);
        TestBase.assertDocument(expected, newDoc);
    }
}

