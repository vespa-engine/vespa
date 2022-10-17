// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Tags;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.transform.TransformerException;
import java.io.StringReader;

/**
 * @author bratseth
 */
public class HostedOverrideProcessorTagsTest {

    static {
        XMLUnit.setIgnoreWhitespace(true);
    }

    private static final String input =
            "<?xml version='1.0' encoding='UTF-8' standalone='no'?>" +
                    "<services xmlns:deploy='vespa' xmlns:preprocess='?' version='1.0'>" +
                    "  <container id='foo' version='1.0'>" +
                    "    <nodes count='5' deploy:tags='a' deploy:environment='perf'/>" +
                    "    <nodes count='10' deploy:tags='a b'/>" +
                    "    <nodes count='20' deploy:tags='c'/>" +
                    "    <search deploy:tags='b'/>" +
                    "    <document-api deploy:tags='d'/>" +
                    "  </container>" +
                    "</services>";

    @Test
    public void testParsingTagAPerf() throws TransformerException {
        String expected = 
                "<?xml version='1.0' encoding='UTF-8' standalone='no'?>" +
                "<services xmlns:deploy='vespa' xmlns:preprocess='?' version='1.0'>" +
                "  <container id='foo' version='1.0'>" +
                "    <nodes count='5' required='true'/>" +
                "  </container>" +
                "</services>";
        assertOverride(InstanceName.defaultName(),
                       Environment.perf,
                       RegionName.defaultName(),
                       Tags.fromString("a"),
                       expected);
    }

    @Test
    public void testParsingTagAProd() throws TransformerException {
        String expected =
                "<?xml version='1.0' encoding='UTF-8' standalone='no'?>" +
                "<services xmlns:deploy='vespa' xmlns:preprocess='?' version='1.0'>" +
                "  <container id='foo' version='1.0'>" +
                "    <nodes count='10' required='true'/>" +
                "  </container>" +
                "</services>";
        assertOverride(InstanceName.defaultName(),
                       Environment.prod,
                       RegionName.defaultName(),
                       Tags.fromString("a"),
                       expected);
    }

    @Test
    public void testParsingTagB() throws TransformerException {
        String expected =
                "<?xml version='1.0' encoding='UTF-8' standalone='no'?>" +
                "<services xmlns:deploy='vespa' xmlns:preprocess='?' version='1.0'>" +
                "  <container id='foo' version='1.0'>" +
                "    <nodes count='10' required='true'/>" +
                "    <search/>" +
                "  </container>" +
                "</services>";
        assertOverride(InstanceName.defaultName(),
                       Environment.prod,
                       RegionName.defaultName(),
                       Tags.fromString("b"),
                       expected);
    }

    @Test
    public void testParsingTagC() throws TransformerException {
        String expected =
                "<?xml version='1.0' encoding='UTF-8' standalone='no'?>" +
                "<services xmlns:deploy='vespa' xmlns:preprocess='?' version='1.0'>" +
                "  <container id='foo' version='1.0'>" +
                "    <nodes count='20' required='true'/>" +
                "  </container>" +
                "</services>";
        assertOverride(InstanceName.defaultName(),
                       Environment.prod,
                       RegionName.defaultName(),
                       Tags.fromString("c"),
                       expected);
    }

    @Test
    public void testParsingTagCAndD() throws TransformerException {
        String expected =
                "<?xml version='1.0' encoding='UTF-8' standalone='no'?>" +
                "<services xmlns:deploy='vespa' xmlns:preprocess='?' version='1.0'>" +
                "  <container id='foo' version='1.0'>" +
                "    <nodes count='20' required='true'/>" +
                "    <document-api/>" +
                "  </container>" +
                "</services>";
        assertOverride(InstanceName.defaultName(),
                       Environment.prod,
                       RegionName.defaultName(),
                       Tags.fromString("c d"),
                       expected);
    }

    private void assertOverride(InstanceName instance, Environment environment, RegionName region, Tags tags, String expected) throws TransformerException {
        Document inputDoc = Xml.getDocument(new StringReader(input));
        Document newDoc = new OverrideProcessor(instance, environment, region, tags).process(inputDoc);
        TestBase.assertDocument(expected, newDoc);
    }

}
