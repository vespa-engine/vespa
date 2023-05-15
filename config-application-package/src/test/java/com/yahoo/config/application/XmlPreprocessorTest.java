// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Tags;
import org.junit.Test;
import org.w3c.dom.Document;

import java.io.File;
import java.io.StringReader;
import java.util.Set;

/**
 * @author hmusum
 */
public class XmlPreprocessorTest {

    private static final File appDir = new File("src/test/resources/multienvapp");
    private static final File services = new File(appDir, "services.xml");

    @Test
    public void testPreProcessing() throws Exception {
        String expectedDev =
                """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <!-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
                <services xmlns:deploy="vespa" xmlns:preprocess="properties" version="1.0">
                    <admin version="2.0">
                        <adminserver hostalias="node0"/>
                    </admin>
                    <content id="foo" version="1.0">
                      <redundancy>1</redundancy>
                      <documents>
                        <document mode="index" type="music.sd"/>
                      </documents>
                      <nodes>
                        <node distribution-key="0" hostalias="node0"/>
                      </nodes>
                    </content>
                    <container id="stateless" version="1.0">
                      <search/>
                      <component bundle="foobundle" class="MyFoo" id="foo"/>
                      <component bundle="foobundle" class="TestBar" id="bar"/>
                      <nodes>
                        <node hostalias="node0" baseport="5000"/>
                      </nodes>
                    </container>
                </services>""";
        TestBase.assertDocument(expectedDev,
                                new XmlPreProcessor(appDir,
                                                    services,
                                                    InstanceName.defaultName(),
                                                    Environment.dev,
                                                    RegionName.defaultName(),
                                                    Tags.empty()).run());

        // Difference from dev: node1
        // Difference from dev: no TestBar
        String expectedStaging =
                """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <!-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
                <services xmlns:deploy="vespa" xmlns:preprocess="properties" version="1.0">
                    <admin version="2.0">
                        <adminserver hostalias="node0"/>
                    </admin>
                    <content id="foo" version="1.0">
                      <redundancy>1</redundancy>
                      <documents>
                        <document mode="index" type="music.sd"/>
                      </documents>
                      <nodes>
                        <node distribution-key="0" hostalias="node0"/>
                      </nodes>
                    </content>
                    <container id="stateless" version="1.0">
                      <search/>
                      <component bundle="foobundle" class="MyFoo" id="foo"/>
                      <nodes>
                        <node hostalias="node0" baseport="5000"/>
                      </nodes>
                    </container>
                </services>""";
        TestBase.assertDocument(expectedStaging,
                                new XmlPreProcessor(appDir,
                                                    services,
                                                    InstanceName.defaultName(),
                                                    Environment.staging,
                                                    RegionName.defaultName(),
                                                    Tags.empty()).run());

        String expectedPerfUsWest =
                """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <!-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
                <services xmlns:deploy="vespa" xmlns:preprocess="properties" version="1.0">
                    <admin version="2.0">
                        <adminserver hostalias="node0"/>
                    </admin>
                    <content id="foo" version="1.0">
                      <redundancy>1</redundancy>
                      <documents>
                        <document mode="index" type="music.sd"/>
                      </documents>
                      <nodes>
                        <node distribution-key="0" hostalias="node0"/>
                      </nodes>
                    </content>
                    <container id="stateless" version="1.0">
                      <search/>
                      <component bundle="foobundle" class="MyFoo" id="foo"/>
                      <nodes>
                        <node hostalias="node0" baseport="5000"/>
                      </nodes>
                    </container>
                </services>""";
        TestBase.assertDocument(expectedPerfUsWest,
                                new XmlPreProcessor(appDir,
                                                    services,
                                                    InstanceName.defaultName(),
                                                    Environment.perf,
                                                    RegionName.from("us-west"),
                                                    Tags.empty()).run());

        String expectedPerfUsEastAndCentral =
                """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <!-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
                <services xmlns:deploy="vespa" xmlns:preprocess="properties" version="1.0">
                    <admin version="2.0">
                        <adminserver hostalias="node0"/>
                    </admin>
                    <content id="foo" version="1.0">
                      <thread count="128"/>
                      <redundancy>1</redundancy>
                      <documents>
                        <document mode="index" type="music.sd"/>
                      </documents>
                      <nodes>
                        <node distribution-key="0" hostalias="node0"/>
                      </nodes>
                    </content>
                    <container id="stateless" version="1.0">
                      <search/>
                      <component bundle="foobundle" class="MyFoo" id="foo"/>
                      <nodes>
                        <node hostalias="node0" baseport="5000"/>
                      </nodes>
                    </container>
                </services>""";
        TestBase.assertDocument(expectedPerfUsEastAndCentral,
                                new XmlPreProcessor(appDir,
                                                    services,
                                                    InstanceName.defaultName(),
                                                    Environment.perf,
                                                    RegionName.from("us-east"),
                                                    Tags.empty()).run());
        TestBase.assertDocument(expectedPerfUsEastAndCentral,
                                new XmlPreProcessor(appDir,
                                                    services,
                                                    InstanceName.defaultName(),
                                                    Environment.perf,
                                                    RegionName.from("us-central"),
                                                    Tags.empty()).run());

        String expectedProdUsWest =
                """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <!-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
                <services xmlns:deploy="vespa" xmlns:preprocess="properties" version="1.0">
                    <admin version="2.0">
                        <adminserver hostalias="node0"/>
                    </admin>
                    <content id="foo" version="1.0">
                      <redundancy>1</redundancy>
                      <documents>
                        <document mode="index" type="music.sd"/>
                      </documents>
                      <nodes>
                        <node distribution-key="0" hostalias="node0"/>
                        <node distribution-key="1" hostalias="node1"/>
                        <node distribution-key="2" hostalias="node2"/>
                      </nodes>
                    </content>
                    <container id="stateless" version="1.0">
                      <search>
                        <chain id="common">
                          <searcher id="MySearcher1"/>
                          <searcher id="MySearcher2"/>
                        </chain>
                      </search>
                      <component bundle="foobundle" class="MyFoo" id="foo"/>
                      <component bundle="foobundle" class="ProdBar" id="bar"/>
                      <component bundle="foobundle" class="ProdBaz" id="baz"/>
                      <nodes>
                        <node hostalias="node0" baseport="5001"/>
                      </nodes>
                    </container>
                </services>""";
        TestBase.assertDocument(expectedProdUsWest,
                                new XmlPreProcessor(appDir,
                                                    services,
                                                    InstanceName.defaultName(),
                                                    Environment.prod,
                                                    RegionName.from("us-west"),
                                                    Tags.empty()).run());

        String expectedProdUsEastAndCentral =
                """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <!-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
                <services xmlns:deploy="vespa" xmlns:preprocess="properties" version="1.0">
                    <admin version="2.0">
                        <adminserver hostalias="node1"/>
                    </admin>
                    <content id="foo" version="1.0">
                      <thread count="128"/>
                      <redundancy>1</redundancy>
                      <documents>
                        <document mode="index" type="music.sd"/>
                      </documents>
                      <nodes>
                        <node distribution-key="0" hostalias="node0"/>
                        <node distribution-key="1" hostalias="node1"/>
                      </nodes>
                    </content>
                    <container id="stateless" version="1.0">
                      <search>
                        <chain id="common">
                          <searcher id="MySearcher1"/>
                          <searcher id="MySearcher2"/>
                        </chain>
                      </search>
                      <component bundle="foobundle" class="MyFoo" id="foo"/>
                      <component bundle="foobundle" class="ProdBar" id="bar"/>
                      <component bundle="foobundle" class="ProdBaz" id="baz"/>
                      <nodes>
                        <node hostalias="node0" baseport="5002"/>
                      </nodes>
                    </container>
                </services>""";
        TestBase.assertDocument(expectedProdUsEastAndCentral,
                                new XmlPreProcessor(appDir,
                                                    services,
                                                    InstanceName.defaultName(),
                                                    Environment.prod,
                                                    RegionName.from("us-east"),
                                                    Tags.empty()).run());
        TestBase.assertDocument(expectedProdUsEastAndCentral,
                                new XmlPreProcessor(appDir,
                                                    services,
                                                    InstanceName.defaultName(),
                                                    Environment.prod,
                                                    RegionName.from("us-central"),
                                                    Tags.empty()).run());
    }

    @Test
    public void testPropertiesWithOverlappingNames() throws Exception {
        String input =
                """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <services xmlns:deploy="vespa" xmlns:preprocess="properties" version="1.0">
                  <preprocess:properties>
                    <sherpa.host>gamma-usnc1.dht.yahoo.com</sherpa.host>
                    <sherpa.port>4080</sherpa.port>
                    <lidspacecompaction_interval>3600</lidspacecompaction_interval>
                    <lidspacecompaction_interval deploy:environment='prod'>36000</lidspacecompaction_interval>
                    <lidspacecompaction_allowedlidbloat>50000</lidspacecompaction_allowedlidbloat>
                    <lidspacecompaction_allowedlidbloat deploy:environment='prod'>50000000</lidspacecompaction_allowedlidbloat>
                    <lidspacecompaction_allowedlidbloatfactor>0.01</lidspacecompaction_allowedlidbloatfactor>
                    <lidspacecompaction_allowedlidbloatfactor deploy:environment='prod'>0.91</lidspacecompaction_allowedlidbloatfactor>
                  </preprocess:properties>
                  <config name='a'>
                     <a>${lidspacecompaction_interval}</a>
                     <b>${lidspacecompaction_allowedlidbloat}</b>
                     <c>${lidspacecompaction_allowedlidbloatfactor}</c>
                     <host>${sherpa.host}</host>
                     <port>${sherpa.port}</port>
                  </config>
                  <admin version="2.0">
                    <adminserver hostalias="node0"/>
                  </admin>
                </services>""";

        String expectedProd =
                """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <services xmlns:deploy="vespa" xmlns:preprocess="properties" version="1.0">
                  <config name='a'>
                     <a>36000</a>
                     <b>50000000</b>
                     <c>0.91</c>
                     <host>gamma-usnc1.dht.yahoo.com</host>
                     <port>4080</port>
                  </config>
                  <admin version="2.0">
                    <adminserver hostalias="node0"/>
                  </admin>
                </services>""";
        Document docDev = (new XmlPreProcessor(appDir,
                                               new StringReader(input),
                                               InstanceName.defaultName(),
                                               Environment.prod,
                                               RegionName.defaultName(),
                                               Tags.empty()).run());
        TestBase.assertDocument(expectedProd, docDev);
    }

}
