// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import com.yahoo.config.provision.Environment;
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
 * @author lulf
 * @since 5.22
 */
public class OverrideProcessorTest {

    static {
        XMLUnit.setIgnoreWhitespace(true);
    }

    private static final String input =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                "  <admin version=\"2.0\">" +
                "    <adminserver hostalias=\"node0\"/>" +
                "  </admin>" +
                "  <admin deploy:environment=\"prod\" version=\"2.0\">" +
                "    <adminserver hostalias=\"node1\"/>" +
                "  </admin>" +
                "  <content id=\"foo\" version=\"1.0\">" +
                "    <redundancy>1</redundancy>" +
                "    <documents>" +
                "      <document mode='index' type='music'/>\n" +
                "      <document mode='index' type='music2'/>\n" +
                "      <document deploy:environment='prod' deploy:region='us-east-3' mode='index' type='music'/>\n" +
                "      <document deploy:environment='prod' deploy:region='us-east-3' mode='index' type='music2'/>\n" +
                "      <document deploy:environment='prod' mode='index' type='music3'/>\n" +
                "      <document deploy:environment='prod' deploy:region='us-west' mode='index' type='music4'/>\n" +
                "    </documents>" +
                "    <nodes>" +
                "      <node distribution-key=\"0\" hostalias=\"node0\"/>" +
                "    </nodes>" +
                "    <nodes deploy:environment=\"prod\">" +
                "      <node distribution-key=\"0\" hostalias=\"node0\"/>" +
                "      <node distribution-key=\"1\" hostalias=\"node1\"/>" +
                "    </nodes>" +
                "    <nodes deploy:environment=\"staging\">" +
                "      <node distribution-key=\"0\" hostalias=\"node0\"/>" +
                "      <node deploy:region=\"us-west\" distribution-key=\"0\" hostalias=\"node1\"/>" +
                "    </nodes>" +
                "    <nodes deploy:environment=\"prod\" deploy:region=\"us-west\">" +
                "      <node distribution-key=\"0\" hostalias=\"node0\"/>" +
                "      <node distribution-key=\"1\" hostalias=\"node1\"/>" +
                "      <node distribution-key=\"2\" hostalias=\"node2\"/>" +
                "    </nodes>" +
                "  </content>" +
                "  <jdisc id=\"stateless\" version=\"1.0\">" +
                "    <search/>" +
                "    <component id=\"foo\" class=\"MyFoo\" bundle=\"foobundle\" />" +
                "    <component id=\"bar\" class=\"TestBar\" bundle=\"foobundle\" deploy:environment=\"staging\" />" +
                "    <component id=\"bar\" class=\"ProdBar\" bundle=\"foobundle\" deploy:environment=\"prod\" />" +
                "    <component id=\"baz\" class=\"ProdBaz\" bundle=\"foobundle\" deploy:environment=\"prod\" />" +
                "    <nodes>" +
                "      <node hostalias=\"node0\"/>" +
                "    </nodes>" +
                "  </jdisc>" +
                "</services>";


    @Test
    public void testParsingDefault() throws IOException, SAXException, XMLStreamException, ParserConfigurationException, TransformerException {
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                "  <admin version=\"2.0\">" +
                "    <adminserver hostalias=\"node0\"/>" +
                "  </admin>" +
                "  <content id=\"foo\" version=\"1.0\">" +
                "    <redundancy>1</redundancy>" +
                "    <documents>" +
                "      <document mode=\"index\" type=\"music\"/>" +
                "      <document mode=\"index\" type=\"music2\"/>" +
                "    </documents>" +
                "    <nodes>" +
                "      <node distribution-key=\"0\" hostalias=\"node0\"/>" +
                "    </nodes>" +
                "  </content>" +
                "  <jdisc id=\"stateless\" version=\"1.0\">" +
                "    <search/>" +
                "    <component id=\"foo\" class=\"MyFoo\" bundle=\"foobundle\" />" +
                "    <nodes>" +
                "      <node hostalias=\"node0\"/>" +
                "    </nodes>" +
                "  </jdisc>" +
                "</services>";
        assertOverride(Environment.test, RegionName.defaultName(), expected);
    }

    @Test
    public void testParsingEnvironmentAndRegion() throws ParserConfigurationException, IOException, SAXException, TransformerException {
        String expected = 
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                "  <admin version=\"2.0\">" +
                "    <adminserver hostalias=\"node1\"/>" +
                "  </admin>" +
                "  <content id=\"foo\" version=\"1.0\">" +
                "    <redundancy>1</redundancy>" +
                "    <documents>" +
                "      <document mode=\"index\" type=\"music4\"/>" +
                "    </documents>" +
                "    <nodes>" +
                "      <node distribution-key=\"0\" hostalias=\"node0\"/>" +
                "      <node distribution-key=\"1\" hostalias=\"node1\"/>" +
                "      <node distribution-key=\"2\" hostalias=\"node2\"/>" +
                "    </nodes>" +
                "  </content>" +
                "  <jdisc id=\"stateless\" version=\"1.0\">" +
                "    <search/>" +
                "    <component id=\"foo\" class=\"MyFoo\" bundle=\"foobundle\" />" +
                "    <component id=\"bar\" class=\"ProdBar\" bundle=\"foobundle\" />" +
                "    <component id=\"baz\" class=\"ProdBaz\" bundle=\"foobundle\" />" +
                "    <nodes>" +
                "      <node hostalias=\"node0\"/>" +
                "    </nodes>" +
                "  </jdisc>" +
                "</services>";
        assertOverride(Environment.from("prod"), RegionName.from("us-west"), expected);
    }

    @Test
    public void testParsingEnvironmentUnknownRegion() throws ParserConfigurationException, IOException, SAXException, TransformerException {
        String expected =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                        "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                        "  <admin version=\"2.0\">" +
                        "    <adminserver hostalias=\"node1\"/>" +
                        "  </admin>" +
                        "  <content id=\"foo\" version=\"1.0\">" +
                        "    <redundancy>1</redundancy>" +
                        "    <documents>" +
                        "      <document mode=\"index\" type=\"music3\"/>" +
                        "    </documents>" +
                        "    <nodes>" +
                        "      <node distribution-key=\"0\" hostalias=\"node0\"/>" +
                        "      <node distribution-key=\"1\" hostalias=\"node1\"/>" +
                        "    </nodes>" +
                        "  </content>" +
                        "  <jdisc id=\"stateless\" version=\"1.0\">" +
                        "    <search/>" +
                        "    <component id=\"foo\" class=\"MyFoo\" bundle=\"foobundle\" />" +
                        "    <component id=\"bar\" class=\"ProdBar\" bundle=\"foobundle\" />" +
                        "    <component id=\"baz\" class=\"ProdBaz\" bundle=\"foobundle\" />" +
                        "    <nodes>" +
                        "      <node hostalias=\"node0\"/>" +
                        "    </nodes>" +
                        "  </jdisc>" +
                        "</services>";
        assertOverride(Environment.valueOf("prod"), RegionName.from("unknown"), expected);
    }

    @Test
    public void testParsingEnvironmentNoRegion() throws ParserConfigurationException, IOException, SAXException, TransformerException {
        String expected =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                        "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                        "  <admin version=\"2.0\">" +
                        "    <adminserver hostalias=\"node1\"/>" +
                        "  </admin>" +
                        "  <content id=\"foo\" version=\"1.0\">" +
                        "    <redundancy>1</redundancy>" +
                        "    <documents>" +
                        "      <document mode=\"index\" type=\"music3\"/>" +
                        "    </documents>" +
                        "    <nodes>" +
                        "      <node distribution-key=\"0\" hostalias=\"node0\"/>" +
                        "      <node distribution-key=\"1\" hostalias=\"node1\"/>" +
                        "    </nodes>" +
                        "  </content>" +
                        "  <jdisc id=\"stateless\" version=\"1.0\">" +
                        "    <search/>" +
                        "    <component id=\"foo\" class=\"MyFoo\" bundle=\"foobundle\" />" +
                        "    <component id=\"bar\" class=\"ProdBar\" bundle=\"foobundle\" />" +
                        "    <component id=\"baz\" class=\"ProdBaz\" bundle=\"foobundle\" />" +
                        "    <nodes>" +
                        "      <node hostalias=\"node0\"/>" +
                        "    </nodes>" +
                        "  </jdisc>" +
                        "</services>";
        assertOverride(Environment.from("prod"), RegionName.defaultName(), expected);
    }

    @Test
    public void testParsingUnknownEnvironment() throws ParserConfigurationException, IOException, SAXException, TransformerException {
        String expected =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                        "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                        "  <admin version=\"2.0\">" +
                        "    <adminserver hostalias=\"node0\"/>" +
                        "  </admin>" +
                        "  <content id=\"foo\" version=\"1.0\">" +
                        "    <redundancy>1</redundancy>" +
                        "    <documents>" +
                        "      <document mode=\"index\" type=\"music\"/>" +
                        "      <document mode=\"index\" type=\"music2\"/>" +
                        "    </documents>" +
                        "    <nodes>" +
                        "      <node distribution-key=\"0\" hostalias=\"node0\"/>" +
                        "    </nodes>" +
                        "  </content>" +
                        "  <jdisc id=\"stateless\" version=\"1.0\">" +
                        "    <search/>" +
                        "    <component id=\"foo\" class=\"MyFoo\" bundle=\"foobundle\" />" +
                        "    <nodes>" +
                        "      <node hostalias=\"node0\"/>" +
                        "    </nodes>" +
                        "  </jdisc>" +
                        "</services>";
        assertOverride(Environment.from("dev"), RegionName.defaultName(), expected);
    }

    @Test
    public void testParsingUnknownEnvironmentUnknownRegion() throws ParserConfigurationException, IOException, SAXException, TransformerException {
        String expected =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                        "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                        "  <admin version=\"2.0\">" +
                        "    <adminserver hostalias=\"node0\"/>" +
                        "  </admin>" +
                        "  <content id=\"foo\" version=\"1.0\">" +
                        "    <redundancy>1</redundancy>" +
                        "    <documents>" +
                        "      <document mode=\"index\" type=\"music\"/>" +
                        "      <document mode=\"index\" type=\"music2\"/>" +
                        "    </documents>" +
                        "    <nodes>" +
                        "      <node distribution-key=\"0\" hostalias=\"node0\"/>" +
                        "    </nodes>" +
                        "  </content>" +
                        "  <jdisc id=\"stateless\" version=\"1.0\">" +
                        "    <search/>" +
                        "    <component id=\"foo\" class=\"MyFoo\" bundle=\"foobundle\" />" +
                        "    <nodes>" +
                        "      <node hostalias=\"node0\"/>" +
                        "    </nodes>" +
                        "  </jdisc>" +
                        "</services>";
        assertOverride(Environment.from("test"), RegionName.from("us-west"), expected);
    }

    @Test
    public void testParsingInheritEnvironment() throws ParserConfigurationException, IOException, SAXException, TransformerException {
        String expected =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                        "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                        "  <admin version=\"2.0\">" +
                        "    <adminserver hostalias=\"node0\"/>" +
                        "  </admin>" +
                        "  <content id=\"foo\" version=\"1.0\">" +
                        "    <redundancy>1</redundancy>" +
                        "    <documents>" +
                        "      <document mode=\"index\" type=\"music\"/>" +
                        "      <document mode=\"index\" type=\"music2\"/>" +
                        "    </documents>" +
                        "    <nodes>" +
                        "      <node distribution-key=\"0\" hostalias=\"node1\"/>" +
                        "    </nodes>" +
                        "  </content>" +
                        "  <jdisc id=\"stateless\" version=\"1.0\">" +
                        "    <search/>" +
                        "    <component id=\"foo\" class=\"MyFoo\" bundle=\"foobundle\" />" +
                        "    <component id=\"bar\" class=\"TestBar\" bundle=\"foobundle\" />" +
                        "    <nodes>" +
                        "      <node hostalias=\"node0\"/>" +
                        "    </nodes>" +
                        "  </jdisc>" +
                        "</services>";
        assertOverride(Environment.from("staging"), RegionName.from("us-west"), expected);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingDifferentEnvInParentAndChild() throws ParserConfigurationException, IOException, SAXException, TransformerException {
        String in = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                    "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                    "  <admin deploy:environment=\"prod\" version=\"2.0\">" +
                    "    <adminserver deploy:environment=\"test\" hostalias=\"node1\"/>" +
                    "  </admin>" +
                    "</services>";
        Document inputDoc = Xml.getDocument(new StringReader(in));
        new OverrideProcessor(Environment.from("prod"), RegionName.from("us-west")).process(inputDoc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingDifferentRegionInParentAndChild() throws ParserConfigurationException, IOException, SAXException, TransformerException {
        String in = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                "  <admin deploy:region=\"us-west\" version=\"2.0\">" +
                "    <adminserver deploy:region=\"us-east\" hostalias=\"node1\"/>" +
                "  </admin>" +
                "</services>";
        Document inputDoc = Xml.getDocument(new StringReader(in));
        new OverrideProcessor(Environment.defaultEnvironment(), RegionName.from("us-west")).process(inputDoc);
    }

    private void assertOverride(Environment environment, RegionName region, String expected) throws TransformerException {
        Document inputDoc = Xml.getDocument(new StringReader(input));
        Document newDoc = new OverrideProcessor(environment, region).process(inputDoc);
        TestBase.assertDocument(expected, newDoc);
    }

}
