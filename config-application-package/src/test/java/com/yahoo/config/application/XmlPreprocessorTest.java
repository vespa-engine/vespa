// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import org.junit.Test;
import org.w3c.dom.Document;

import java.io.File;
import java.io.StringReader;

/**
 * @author hmusum
 */
public class XmlPreprocessorTest {

    private static final File appDir = new File("src/test/resources/multienvapp");
    private static final File services = new File(appDir, "services.xml");

    @Test
    public void testPreProcessing() throws Exception {
        String expectedDev =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"properties\" version=\"1.0\">\n" +
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
                "    <container id=\"stateless\" version=\"1.0\">\n" +
                "      <search/>\n" +
                "      <component bundle=\"foobundle\" class=\"MyFoo\" id=\"foo\"/>\n" +
                "      <component bundle=\"foobundle\" class=\"TestBar\" id=\"bar\"/>\n" +
                "      <nodes>\n" +
                "        <node hostalias=\"node0\" baseport=\"5000\"/>\n" +
                "      </nodes>\n" +
                "    </container>\n" +
                "</services>";
        TestBase.assertDocument(expectedDev, new XmlPreProcessor(appDir, services, InstanceName.defaultName(), Environment.dev, RegionName.defaultName()).run());

        String expectedStaging =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"properties\" version=\"1.0\">\n" +
                "    <admin version=\"2.0\">\n" +
                "        <adminserver hostalias=\"node1\"/>\n" + // Difference from dev: node1
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
                "    <container id=\"stateless\" version=\"1.0\">\n" +
                "      <search/>\n" +
                "      <component bundle=\"foobundle\" class=\"MyFoo\" id=\"foo\"/>\n" +
                "" +   //  Difference from dev: no TestBar
                "      <nodes>\n" +
                "        <node hostalias=\"node0\" baseport=\"5000\"/>\n" +
                "      </nodes>\n" +
                "    </container>\n" +
                "</services>";
        TestBase.assertDocument(expectedStaging, new XmlPreProcessor(appDir, services, InstanceName.defaultName(), Environment.staging, RegionName.defaultName()).run());

        String expectedUsWest =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"properties\" version=\"1.0\">\n" +
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
                "        <node distribution-key=\"1\" hostalias=\"node1\"/>\n" +
                "        <node distribution-key=\"2\" hostalias=\"node2\"/>\n" +
                "      </nodes>\n" +
                "    </content>\n" +
                "    <container id=\"stateless\" version=\"1.0\">\n" +
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
                "    </container>\n" +
                "</services>";
        TestBase.assertDocument(expectedUsWest, new XmlPreProcessor(appDir, services, InstanceName.defaultName(), Environment.prod, RegionName.from("us-west")).run());

        String expectedUsEastAndCentral =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"properties\" version=\"1.0\">\n" +
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
                "    <container id=\"stateless\" version=\"1.0\">\n" +
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
                "    </container>\n" +
                "</services>";
        TestBase.assertDocument(expectedUsEastAndCentral,
                                new XmlPreProcessor(appDir, services, InstanceName.defaultName(), Environment.prod, RegionName.from("us-east")).run());
        TestBase.assertDocument(expectedUsEastAndCentral,
                                new XmlPreProcessor(appDir, services, InstanceName.defaultName(), Environment.prod, RegionName.from("us-central")).run());
    }

    @Test
    public void testPropertiesWithOverlappingNames() throws Exception {
        String input =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
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

        String expectedProd =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
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
        Document docDev = (new XmlPreProcessor(appDir, new StringReader(input), InstanceName.defaultName(), Environment.prod, RegionName.defaultName()).run());
        TestBase.assertDocument(expectedProd, docDev);
    }

}
