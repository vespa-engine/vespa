// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.transform.TransformerException;
import java.io.StringReader;

/**
 * @author Ulf Lilleengen
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
                "    <documents deploy:environment='staging prod'>" +
                "      <document mode='index' type='music'/>\n" +
                "      <document type='music2' mode='index' />\n" +
                "      <document deploy:environment='prod' deploy:region='us-east-3' mode='index' type='music'/>\n" +
                "      <document deploy:environment='staging prod' deploy:region='us-east-3' mode='index' type='music2'/>\n" +
                "      <document deploy:environment='prod' mode='index' type='music3'/>\n" +
                "      <document deploy:environment='prod' deploy:region='us-west' mode='index' type='music4'/>\n" +
                "    </documents>" +
                "    <documents deploy:environment='dev'>" +
                "      <document mode='store-only' type='music'/>\n" +
                "      <document type='music5' mode='streaming' />\n" +
                "      <document deploy:region='us-east-1' type='music6' mode='streaming' />\n" +
                "    </documents>" +
                "    <documents>" +
                "      <document mode='store-only' type='music'/>\n" +
                "      <document type='music2' mode='streaming' />\n" +
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
                "  <container id=\"stateless\" version=\"1.0\">" +
                "    <search/>" +
                "    <component id=\"foo\" class=\"MyFoo\" bundle=\"foobundle\" />" +
                "    <component id=\"bar\" class=\"TestBar\" bundle=\"foobundle\" deploy:environment=\"staging\" />" +
                "    <component id=\"bar\" class=\"ProdBar\" bundle=\"foobundle\" deploy:environment=\"prod\" />" +
                "    <component id=\"baz\" class=\"ProdBaz\" bundle=\"foobundle\" deploy:environment=\"prod\" />" +
                "    <nodes>" +
                "      <node hostalias=\"node0\"/>" +
                "    </nodes>" +
                "  </container>" +
                "</services>";


    @Test
    public void testParsingDefault() throws TransformerException {
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                "  <admin version=\"2.0\">" +
                "    <adminserver hostalias=\"node0\"/>" +
                "  </admin>" +
                "  <content id=\"foo\" version=\"1.0\">" +
                "    <redundancy>1</redundancy>" +
                "    <documents>" +
                "      <document mode=\"store-only\" type=\"music\"/>" +
                "      <document mode=\"streaming\" type=\"music2\"/>" +
                "    </documents>" +
                "    <nodes>" +
                "      <node distribution-key=\"0\" hostalias=\"node0\"/>" +
                "    </nodes>" +
                "  </content>" +
                "  <container id=\"stateless\" version=\"1.0\">" +
                "    <search/>" +
                "    <component id=\"foo\" class=\"MyFoo\" bundle=\"foobundle\" />" +
                "    <nodes>" +
                "      <node hostalias=\"node0\"/>" +
                "    </nodes>" +
                "  </container>" +
                "</services>";
        assertOverride(Environment.test, RegionName.defaultName(), expected);
    }

    @Test
    public void testParsingEnvironmentAndRegion() throws TransformerException {
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
                "  <container id=\"stateless\" version=\"1.0\">" +
                "    <search/>" +
                "    <component id=\"foo\" class=\"MyFoo\" bundle=\"foobundle\" />" +
                "    <component id=\"bar\" class=\"ProdBar\" bundle=\"foobundle\" />" +
                "    <component id=\"baz\" class=\"ProdBaz\" bundle=\"foobundle\" />" +
                "    <nodes>" +
                "      <node hostalias=\"node0\"/>" +
                "    </nodes>" +
                "  </container>" +
                "</services>";
        assertOverride(Environment.from("prod"), RegionName.from("us-west"), expected);
    }

    @Test
    public void testParsingEnvironmentUnknownRegion() throws TransformerException {
        String expected =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                        "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                        "  <admin version=\"2.0\">" +
                        "    <adminserver hostalias=\"node1\"/>" +
                        "  </admin>" +
                        "  <content id=\"foo\" version=\"1.0\">" +
                        "    <redundancy>1</redundancy>" +
                        "    <documents>" +
                        "      <document mode='index' type='music'/>\n" +
                        "      <document type='music2' mode='index' />\n" +
                        "      <document mode='index' type='music3'/>" +
                        "    </documents>" +
                        "    <nodes>" +
                        "      <node distribution-key=\"0\" hostalias=\"node0\"/>" +
                        "      <node distribution-key=\"1\" hostalias=\"node1\"/>" +
                        "    </nodes>" +
                        "  </content>" +
                        "  <container id=\"stateless\" version=\"1.0\">" +
                        "    <search/>" +
                        "    <component id=\"foo\" class=\"MyFoo\" bundle=\"foobundle\" />" +
                        "    <component id=\"bar\" class=\"ProdBar\" bundle=\"foobundle\" />" +
                        "    <component id=\"baz\" class=\"ProdBaz\" bundle=\"foobundle\" />" +
                        "    <nodes>" +
                        "      <node hostalias=\"node0\"/>" +
                        "    </nodes>" +
                        "  </container>" +
                        "</services>";
        assertOverride(Environment.valueOf("prod"), RegionName.from("unknown"), expected);
    }

    @Test
    public void testParsingEnvironmentNoRegion() throws TransformerException {
        String expected =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                        "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                        "  <admin version=\"2.0\">" +
                        "    <adminserver hostalias=\"node1\"/>" +
                        "  </admin>" +
                        "  <content id=\"foo\" version=\"1.0\">" +
                        "    <redundancy>1</redundancy>" +
                        "    <documents>" +
                        "      <document mode='index' type='music'/>\n" +
                        "      <document type='music2' mode='index' />\n" +
                        "      <document mode='index' type='music3'/>" +
                        "    </documents>" +
                        "    <nodes>" +
                        "      <node distribution-key=\"0\" hostalias=\"node0\"/>" +
                        "      <node distribution-key=\"1\" hostalias=\"node1\"/>" +
                        "    </nodes>" +
                        "  </content>" +
                        "  <container id=\"stateless\" version=\"1.0\">" +
                        "    <search/>" +
                        "    <component id=\"foo\" class=\"MyFoo\" bundle=\"foobundle\" />" +
                        "    <component id=\"bar\" class=\"ProdBar\" bundle=\"foobundle\" />" +
                        "    <component id=\"baz\" class=\"ProdBaz\" bundle=\"foobundle\" />" +
                        "    <nodes>" +
                        "      <node hostalias=\"node0\"/>" +
                        "    </nodes>" +
                        "  </container>" +
                        "</services>";
        assertOverride(Environment.from("prod"), RegionName.defaultName(), expected);
    }

    @Test
    public void testParsingDevEnvironment() throws TransformerException {
        String expected =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                        "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                        "  <admin version=\"2.0\">" +
                        "    <adminserver hostalias=\"node0\"/>" +
                        "  </admin>" +
                        "  <content id=\"foo\" version=\"1.0\">" +
                        "    <redundancy>1</redundancy>" +
                        "    <documents>" +
                        "      <document mode=\"store-only\" type=\"music\"/>" +
                        "      <document mode=\"streaming\" type=\"music5\"/>" +
                        "    </documents>" +
                        "    <nodes>" +
                        "      <node distribution-key=\"0\" hostalias=\"node0\"/>" +
                        "    </nodes>" +
                        "  </content>" +
                        "  <container id=\"stateless\" version=\"1.0\">" +
                        "    <search/>" +
                        "    <component id=\"foo\" class=\"MyFoo\" bundle=\"foobundle\" />" +
                        "    <nodes>" +
                        "      <node hostalias=\"node0\"/>" +
                        "    </nodes>" +
                        "  </container>" +
                        "</services>";
        assertOverride(Environment.from("dev"), RegionName.defaultName(), expected);
    }

    @Test
    public void testParsingDevEnvironmentAndRegion() throws Exception {
        String expected =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                "  <admin version=\"2.0\">" +
                "    <adminserver hostalias=\"node0\"/>" +
                "  </admin>" +
                "  <content id=\"foo\" version=\"1.0\">" +
                "    <redundancy>1</redundancy>" +
                "    <documents>" +
                "      <document mode=\"streaming\" type=\"music6\"/>" +
                "    </documents>" +
                "    <nodes>" +
                "      <node distribution-key=\"0\" hostalias=\"node0\"/>" +
                "    </nodes>" +
                "  </content>" +
                "  <container id=\"stateless\" version=\"1.0\">" +
                "    <search/>" +
                "    <component id=\"foo\" class=\"MyFoo\" bundle=\"foobundle\" />" +
                "    <nodes>" +
                "      <node hostalias=\"node0\"/>" +
                "    </nodes>" +
                "  </container>" +
                "</services>";

        assertOverride(Environment.from("dev"), RegionName.from("us-east-1"), expected);
    }

    @Test
    public void testParsingTestEnvironmentUnknownRegion() throws TransformerException {
        String expected =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                        "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                        "  <admin version=\"2.0\">" +
                        "    <adminserver hostalias=\"node0\"/>" +
                        "  </admin>" +
                        "  <content id=\"foo\" version=\"1.0\">" +
                        "    <redundancy>1</redundancy>" +
                        "    <documents>" +
                        "      <document mode=\"store-only\" type=\"music\"/>" +
                        "      <document mode=\"streaming\" type=\"music2\"/>" +
                        "    </documents>" +
                        "    <nodes>" +
                        "      <node distribution-key=\"0\" hostalias=\"node0\"/>" +
                        "    </nodes>" +
                        "  </content>" +
                        "  <container id=\"stateless\" version=\"1.0\">" +
                        "    <search/>" +
                        "    <component id=\"foo\" class=\"MyFoo\" bundle=\"foobundle\" />" +
                        "    <nodes>" +
                        "      <node hostalias=\"node0\"/>" +
                        "    </nodes>" +
                        "  </container>" +
                        "</services>";
        assertOverride(Environment.from("test"), RegionName.from("us-west"), expected);
    }

    @Test
    public void testParsingInheritEnvironment() throws TransformerException {
        String expected =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                        "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                        "  <admin version=\"2.0\">" +
                        "    <adminserver hostalias=\"node0\"/>" +
                        "  </admin>" +
                        "  <content id=\"foo\" version=\"1.0\">" +
                        "    <redundancy>1</redundancy>" +
                        "    <documents>" +
                        "      <document mode='index' type='music'/>\n" +
                        "      <document mode='index' type='music2'/>\n" +
                        "      <document type='music2' mode='index' />\n" +
                        "    </documents>" +
                        "    <nodes>" +
                // node1 is specified for us-west but does not match because region overrides implies environment=prod
                        "      <node distribution-key=\"0\" hostalias=\"node0\"/>" +
                        "    </nodes>" +
                        "  </content>" +
                        "  <container id=\"stateless\" version=\"1.0\">" +
                        "    <search/>" +
                        "    <component id=\"foo\" class=\"MyFoo\" bundle=\"foobundle\" />" +
                        "    <component id=\"bar\" class=\"TestBar\" bundle=\"foobundle\" />" +
                        "    <nodes>" +
                        "      <node hostalias=\"node0\"/>" +
                        "    </nodes>" +
                        "  </container>" +
                        "</services>";
        assertOverride(Environment.from("staging"), RegionName.from("us-west"), expected);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingDifferentEnvInParentAndChild() throws TransformerException {
        String in = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                    "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                    "  <admin deploy:environment=\"prod\" version=\"2.0\">" +
                    "    <adminserver deploy:environment=\"test\" hostalias=\"node1\"/>" +
                    "  </admin>" +
                    "</services>";
        Document inputDoc = Xml.getDocument(new StringReader(in));
        new OverrideProcessor(InstanceName.from("default"), Environment.from("prod"), RegionName.from("us-west")).process(inputDoc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingDifferentRegionInParentAndChild() throws TransformerException {
        String in = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                "  <admin deploy:region=\"us-west\" version=\"2.0\">" +
                "    <adminserver deploy:region=\"us-east\" hostalias=\"node1\"/>" +
                "  </admin>" +
                "</services>";
        Document inputDoc = Xml.getDocument(new StringReader(in));
        new OverrideProcessor(InstanceName.from("default"), Environment.defaultEnvironment(), RegionName.from("us-west")).process(inputDoc);
    }

    @Test
    public void testImpliedRequired() throws TransformerException {
        String input = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                       "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                       "  <content id=\"foo\" version=\"1.0\">" +
                       "    <nodes deploy:environment=\"dev\">" +
                       "      <!-- comment -->" +
                       "      <resources vcpu=\"2\" memory=\"8Gb\" disk=\"50Gb\" disk-speed=\"any\"/>" +
                       "    </nodes>" +
                       "  </content>" +
                       "</services>";
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                "  <content id=\"foo\" version=\"1.0\">" +
                "    <nodes required=\"true\">" +
                "      <!-- comment -->" +
                "      <resources vcpu=\"2\" memory=\"8Gb\" disk=\"50Gb\" disk-speed=\"any\"/>" +
                "    </nodes>" +
                "  </content>" +
                "</services>";

        assertOverride(input, Environment.dev, RegionName.defaultName(), expected);
    }

    @Test
    public void testNodeElementCancelsImpliedRequired() throws TransformerException {
        String input = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                "  <content id=\"foo\" version=\"1.0\">" +
                "    <nodes deploy:environment=\"dev\">" +
                "      <node distribution-key=\"0\" hostalias=\"node0\"/>" +
                "    </nodes>" +
                "  </content>" +
                "</services>";
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"?\" version=\"1.0\">" +
                "  <content id=\"foo\" version=\"1.0\">" +
                "    <nodes>" +
                "      <node distribution-key=\"0\" hostalias=\"node0\"/>" +
                "    </nodes>" +
                "  </content>" +
                "</services>";

        assertOverride(input, Environment.dev, RegionName.defaultName(), expected);
    }

    private void assertOverride(Environment environment, RegionName region, String expected) throws TransformerException {
        assertOverride(input, environment, region, expected);
    }

    private void assertOverride(String input, Environment environment, RegionName region, String expected) throws TransformerException {
        Document inputDoc = Xml.getDocument(new StringReader(input));
        Document newDoc = new OverrideProcessor(InstanceName.from("default"), environment, region).process(inputDoc);
        TestBase.assertDocument(expected, newDoc);
    }

}
