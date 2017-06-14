// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;

/**
 * @author hmusum
 */
public class XmlPreprocessorTest {

    private static final File appDir = new File("src/test/resources/multienvapp");
    private static final File services = new File(appDir, "services.xml");

    @Test
    public void testPreProcessing() throws IOException, SAXException, XMLStreamException, ParserConfigurationException, TransformerException {
        String expectedDev = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><services xmlns:deploy=\"vespa\" xmlns:preprocess=\"properties\" version=\"1.0\">\n" +
                "    <admin version=\"2.0\">\n" +
                "        <adminserver hostalias=\"node0\"/>\n" +
                "    </admin>\n" +
                "    <content id=\"foo\" version=\"1.0\">\n" +
                "      <redundancy>1</redundancy>\n" +
                "      <documents>\n" +
                "        <document mode=\"index\" type=\"music.sd\"/>\n" +
                "      </documents>\n" +
                "      <nodes>\n" +
                "        <node distribution-key=\"0\" hostalias=\"node0\"/>\n" +
                "      </nodes>\n" +
                "    </content>\n" +
                "    <jdisc id=\"stateless\" version=\"1.0\">\n" +
                "      <search/>\n" +
                "      <component bundle=\"foobundle\" class=\"MyFoo\" id=\"foo\"/>\n" +
                "      <component bundle=\"foobundle\" class=\"TestBar\" id=\"bar\"/>\n" +
                "      <nodes>\n" +
                "        <node hostalias=\"node0\" baseport=\"5000\"/>\n" +
                "      </nodes>\n" +
                "    </jdisc>\n" +
                "</services>";

        Document docDev = (new XmlPreProcessor(appDir, services, Environment.dev, RegionName.from("default")).run());
        TestBase.assertDocument(expectedDev, docDev);


        String expectedUsWest = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><services xmlns:deploy=\"vespa\" xmlns:preprocess=\"properties\" version=\"1.0\">\n" +
                "    <admin version=\"2.0\">\n" +
                "        <adminserver hostalias=\"node1\"/>\n" +
                "    </admin>\n" +
                "    <content id=\"foo\" version=\"1.0\">\n" +
                "      <redundancy>1</redundancy>\n" +
                "      <documents>\n" +
                "        <document mode=\"index\" type=\"music.sd\"/>\n" +
                "      </documents>\n" +
                "      <nodes>\n" +
                "        <node distribution-key=\"0\" hostalias=\"node0\"/>\n" +
                "        <node distribution-key=\"1\" hostalias=\"node1\"/>\n" +
                "        <node distribution-key=\"2\" hostalias=\"node2\"/>\n" +
                "      </nodes>\n" +
                "    </content>\n" +
                "    <jdisc id=\"stateless\" version=\"1.0\">\n" +
                "      <search>\n" +
                "        <chain id=\"common\">\n" +
                "          <searcher id=\"MySearcher1\"/>\n" +
                "          <searcher id=\"MySearcher2\"/>\n" +
                "        </chain>\n" +
                "      </search>\n" +
                "      <component bundle=\"foobundle\" class=\"MyFoo\" id=\"foo\"/>\n" +
                "      <component bundle=\"foobundle\" class=\"ProdBar\" id=\"bar\"/>\n" +
                "      <component bundle=\"foobundle\" class=\"ProdBaz\" id=\"baz\"/>\n" +
                "      <nodes>\n" +
                "        <node hostalias=\"node0\" baseport=\"5001\"/>\n" +
                "      </nodes>\n" +
                "    </jdisc>\n" +
                "</services>";

        Document docUsWest = (new XmlPreProcessor(appDir, services, Environment.prod, RegionName.from("us-west"))).run();
        // System.out.println(Xml.documentAsString(docUsWest));
        TestBase.assertDocument(expectedUsWest, docUsWest);

        String expectedUsEast = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><services xmlns:deploy=\"vespa\" xmlns:preprocess=\"properties\" version=\"1.0\">\n" +
                "    <admin version=\"2.0\">\n" +
                "        <adminserver hostalias=\"node1\"/>\n" +
                "    </admin>\n" +
                "    <content id=\"foo\" version=\"1.0\">\n" +
                "      <redundancy>1</redundancy>\n" +
                "      <documents>\n" +
                "        <document mode=\"index\" type=\"music.sd\"/>\n" +
                "      </documents>\n" +
                "      <nodes>\n" +
                "        <node distribution-key=\"0\" hostalias=\"node0\"/>\n" +
                "        <node distribution-key=\"1\" hostalias=\"node1\"/>\n" +
                "      </nodes>\n" +
                "    </content>\n" +
                "    <jdisc id=\"stateless\" version=\"1.0\">\n" +
                "      <search>\n" +
                "        <chain id=\"common\">\n" +
                "          <searcher id=\"MySearcher1\"/>\n" +
                "          <searcher id=\"MySearcher2\"/>\n" +
                "        </chain>\n" +
                "      </search>\n" +
                "      <component bundle=\"foobundle\" class=\"MyFoo\" id=\"foo\"/>\n" +
                "      <component bundle=\"foobundle\" class=\"ProdBar\" id=\"bar\"/>\n" +
                "      <component bundle=\"foobundle\" class=\"ProdBaz\" id=\"baz\"/>\n" +
                "      <nodes>\n" +
                "        <node hostalias=\"node0\" baseport=\"5002\"/>\n" +
                "      </nodes>\n" +
                "    </jdisc>\n" +
                "</services>";

        Document docUsEast = (new XmlPreProcessor(appDir, services, Environment.prod, RegionName.from("us-east"))).run();
        TestBase.assertDocument(expectedUsEast, docUsEast);
    }

    @Test
    public void testPropertiesWithOverlappingNames() throws IOException, SAXException, XMLStreamException, ParserConfigurationException, TransformerException {
        String input = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"properties\" version=\"1.0\">" +
                "  <preprocess:properties>" +
                "    <sherpa.host>gamma-usnc1.dht.yahoo.com</sherpa.host>" +
                "    <sherpa.port>4080</sherpa.port>" +
                "    <lidspacecompaction_interval>3600</lidspacecompaction_interval>" +
                "    <lidspacecompaction_interval deploy:environment='prod'>36000</lidspacecompaction_interval>" +
                "    <lidspacecompaction_allowedlidbloat>50000</lidspacecompaction_allowedlidbloat>" +
                "    <lidspacecompaction_allowedlidbloat deploy:environment='prod'>50000000</lidspacecompaction_allowedlidbloat>" +
                "    <lidspacecompaction_allowedlidbloatfactor>0.01</lidspacecompaction_allowedlidbloatfactor>" +
                "    <lidspacecompaction_allowedlidbloatfactor deploy:environment='prod'>0.91</lidspacecompaction_allowedlidbloatfactor>" +
                "  </preprocess:properties>" +
                "  <config name='a'>" +
                "     <a>${lidspacecompaction_interval}</a>" +
                "     <b>${lidspacecompaction_allowedlidbloat}</b>" +
                "     <c>${lidspacecompaction_allowedlidbloatfactor}</c>" +
                "     <host>${sherpa.host}</host>" +
                "     <port>${sherpa.port}</port>" +
                "  </config>" +
                "  <admin version=\"2.0\">" +
                "    <adminserver hostalias=\"node0\"/>" +
                "  </admin>" +
                "</services>";

        String expectedProd = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"properties\" version=\"1.0\">" +
                "  <config name='a'>" +
                "     <a>36000</a>" +
                "     <b>50000000</b>" +
                "     <c>0.91</c>" +
                "     <host>gamma-usnc1.dht.yahoo.com</host>" +
                "     <port>4080</port>" +
                "  </config>" +
                "  <admin version=\"2.0\">" +
                "    <adminserver hostalias=\"node0\"/>" +
                "  </admin>" +
                "</services>";
        Document docDev = (new XmlPreProcessor(appDir, new StringReader(input), Environment.prod, RegionName.from("default")).run());
        TestBase.assertDocument(expectedProd, docDev);
    }

}
