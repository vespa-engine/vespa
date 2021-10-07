// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.StringReader;

/**
 * @author bratseth
 */
public class HostedOverrideProcessorTest {

    static {
        XMLUnit.setIgnoreWhitespace(true);
    }

    private static final String input =
            "<?xml version='1.0' encoding='UTF-8' standalone='no'?>" +
                    "<services xmlns:deploy='vespa' xmlns:preprocess='?' version='1.0'>" +
                    "  <container id='foo' version='1.0'>" +
                    "    <nodes count='1'/>" +
                    "    <nodes count='3' deploy:environment='perf'/>" +
                    "    <nodes deploy:environment='staging' count='2' required='true'/>" +
                    "    <nodes deploy:environment='prod' count='3' flavor='v-4-8-100'/>" +
                    "    <nodes deploy:environment='prod' deploy:region='us-west' count='4'/>" +
                    "    <nodes deploy:environment='prod' deploy:region='us-east-3' flavor='v-8-8-100' count='5'/>" +
                    "    <nodes deploy:instance='myinstance' deploy:environment='prod' deploy:region='us-west' count='1'/>" +
                    "  </container>" +
                    "</services>";


    @Test
    public void testParsingDefault() throws TransformerException {
        String expected = 
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                "  <container id=\"foo\" version=\"1.0\">" +
                "    <nodes count='1'/>" +
                "  </container>" +
                "</services>";
        assertOverride(Environment.test, RegionName.defaultName(), expected);
    }

    @Test
    public void testParsingEnvironmentAndRegion() throws TransformerException {
        String expected = 
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                "  <container id=\"foo\" version=\"1.0\">" +
                "    <nodes count='4' required='true'/>" +
                "  </container>" +
                "</services>";
        assertOverride(Environment.from("prod"), RegionName.from("us-west"), expected);
    }

    @Test
    public void testParsingInstance() throws TransformerException {
        String expected =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                "  <container id=\"foo\" version=\"1.0\">" +
                "    <nodes count='1' required='true'/>" +
                "  </container>" +
                "</services>";
        assertOverride(InstanceName.from("myinstance"), Environment.from("prod"), RegionName.from("us-west"), expected);
    }

    @Test
    public void testParsingEnvironmentAndRegion2() throws TransformerException {
        String expected =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                        "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                        "  <container id=\"foo\" version=\"1.0\">" +
                        "    <nodes count='5' flavor='v-8-8-100' required='true'/>" +
                        "  </container>" +
                        "</services>";
        assertOverride(Environment.from("prod"), RegionName.from("us-east-3"), expected);
    }

    @Test
    public void testParsingEnvironmentAndRegion3() throws TransformerException {
        String expected =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                        "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                        "  <container id=\"foo\" version=\"1.0\">" +
                        "    <nodes count='3' required='true'/>" +
                        "  </container>" +
                        "</services>";
        assertOverride(Environment.from("perf"), RegionName.from("us-east-3"), expected);
    }

    @Test
    public void testParsingEnvironmentUnknownRegion() throws TransformerException {
        String expected =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                "  <container id=\"foo\" version=\"1.0\">" +
                "    <nodes count='3' flavor='v-4-8-100' required='true'/>" +
                "  </container>" +
                "</services>";
        assertOverride(Environment.valueOf("prod"), RegionName.from("unknown"), expected);
    }

    @Test
    public void testParsingEnvironmentNoRegion() throws TransformerException {
        String expected =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                "  <container id=\"foo\" version=\"1.0\">" +
                "    <nodes count='3' flavor='v-4-8-100' required='true'/>" +
                "  </container>" +
                "</services>";
        assertOverride(Environment.from("prod"), RegionName.defaultName(), expected);
    }

    @Test
    public void testParsingUnknownEnvironment() throws TransformerException {
        String expected =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                "  <container id=\"foo\" version=\"1.0\">" +
                "    <nodes count='1'/>" +
                "  </container>" +
                "</services>";
        assertOverride(Environment.from("dev"), RegionName.defaultName(), expected);
    }

    @Test
    public void testParsingUnknownEnvironmentUnknownRegion() throws TransformerException {
        String expected =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                "  <container id=\"foo\" version=\"1.0\">" +
                "    <nodes count='1'/>" +
                "  </container>" +
                "</services>";
        assertOverride(Environment.from("test"), RegionName.from("us-west"), expected);
    }

    @Test
    public void testParsingInheritEnvironment() throws TransformerException {
        String expected =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                "  <container id=\"foo\" version=\"1.0\">" +
                "    <nodes count='2' required='true'/>" +
                "  </container>" +
                "</services>";
        assertOverride(Environment.from("staging"), RegionName.from("us-west"), expected);
    }

    private void assertOverride(Environment environment, RegionName region, String expected) throws TransformerException {
        assertOverride(InstanceName.from("default"), environment, region, expected);
    }

    private void assertOverride(InstanceName instance, Environment environment, RegionName region, String expected) throws TransformerException {
        Document inputDoc = Xml.getDocument(new StringReader(input));
        Document newDoc = new OverrideProcessor(instance, environment, region).process(inputDoc);
        TestBase.assertDocument(expected, newDoc);
    }

}
