// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.config.provision.zone.ZoneInfo;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class PreprocessingTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testCloudPreprocessing() {
        var tester = new PreprocessingTester("src/test/resources/multienvapp", temporaryFolder);
        tester.preprocess(new ZoneInfo(CloudName.AWS, SystemName.Public, Environment.dev, RegionName.defaultName()));
        String expectedServices = """
                                  <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                                  <!-- Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
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
                                        <component bundle="foobundle" class="ProdXyzzyInAws" id="xyzzy"/>
                                        <nodes>
                                          <node hostalias="node0" baseport="5000"/>
                                        </nodes>
                                      </container>
                                  </services>""";
        tester.assertServices(expectedServices);
        String expectedHosts = """
                               <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                               <!-- Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
                               <hosts xmlns:deploy="vespa" xmlns:preprocess="properties">
                                   <host name="bar.yahoo.com">
                                       <alias>node1</alias>
                                   </host>
                               </hosts>""";
        tester.assertHosts(expectedHosts);
    }

    /** Replicates the config/multienvironment system test */
    @Test
    public void testStandalonePreprocessing() {
        var tester = new PreprocessingTester("src/test/resources/multienv", temporaryFolder);

        tester.preprocess(new ZoneInfo(CloudName.DEFAULT, SystemName.defaultSystem(), Environment.dev, RegionName.defaultName()));
        String expectedDevServices = """
                                     <services version='1.0' xmlns:deploy="vespa" xmlns:preprocess="properties">
                                       <admin version='2.0'>
                                         <adminserver hostalias="node0" />
                                         <config name="cloud.config.log.logd">
                                           <logserver>
                                             <rpcport>4099</rpcport>
                                           </logserver>
                                         </config>
                                       </admin>
                                     </services>""";
        tester.assertServices(expectedDevServices);

        tester.preprocess(new ZoneInfo(CloudName.DEFAULT, SystemName.defaultSystem(), Environment.prod, RegionName.defaultName()));
        String expectedProdServices = """
                                      <services version='1.0' xmlns:deploy="vespa" xmlns:preprocess="properties">
                                        <admin version='2.0'>
                                          <adminserver hostalias="node0" />
                                          <config name="cloud.config.log.logd">
                                            <logserver>
                                              <rpcport>5000</rpcport>
                                            </logserver>
                                          </config>
                                        </admin>
                                      </services>""";
        tester.assertServices(expectedProdServices);
    }

    @Test
    public void testDeploymentXmlPreprocessing() {
        var tester = new PreprocessingTester("src/test/resources/multienv-deployment", temporaryFolder);

        var usEast = tester.preprocess(new ZoneInfo(CloudName.AWS, SystemName.Public, Environment.prod, RegionName.from("us-east")));
        String expectedUsEastDeployment = """
                                          <!-- Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
                                          <deployment version='1.0' xmlns:deploy="vespa" xmlns:preprocess="properties">
                                            <instance id='default' cloud-account='aws:222222222222'>
                                              <prod>
                                                <region>us-east</region>
                                                <region>us-west</region>
                                              </prod>
                                            </instance>
                                          </deployment>""";
        tester.assertDeployment(expectedUsEastDeployment);
        assertEquals("aws:222222222222",
                     cloudAccount(usEast, "us-east"));

        var usWest = tester.preprocess(new ZoneInfo(CloudName.AWS, SystemName.Public, Environment.prod, RegionName.from("us-west")));
        assertEquals("aws:111111111111",
                     cloudAccount(usWest, "us-west"));
    }

    private static String cloudAccount(ApplicationPackage application, String region) {
        return application.getDeploymentSpec()
                          .cloudAccount(CloudName.AWS, InstanceName.defaultName(), ZoneId.from(Environment.prod, RegionName.from(region)))
                          .value();
    }

}
