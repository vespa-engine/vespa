// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.SystemName;

/**
 * For running NodeRepository API with some mocked data.
 * This is used by both NodeAdmin and NodeRepository tests.
 *
 * @author dybis
 */
public class ContainerConfig {

    public static String servicesXmlV2(int port, SystemName systemName, CloudAccount cloudAccount) {
        return """
               <container version='1.0'>
                 <config name="container.handler.threadpool">
                   <maxthreads>20</maxthreads>
                 </config>
                 <config name="cloud.config.configserver">
                   <system>%s</system>
                 </config>
                 <config name="config.provisioning.cloud">
                   <account>%s</account>
                 </config>
                 <accesslog type='disabled'/>
                 <component id='com.yahoo.test.ManualClock'/>
                 <component id='com.yahoo.vespa.curator.mock.MockCurator'/>
                 <component id='com.yahoo.vespa.hosted.provision.testutils.OrchestratorMock'/>
                 <component id='com.yahoo.vespa.hosted.provision.testutils.MockDeployer'/>
                 <component id='com.yahoo.vespa.hosted.provision.testutils.MockInfraDeployer'/>
                 <component id='com.yahoo.vespa.hosted.provision.testutils.MockProvisioner'/>
                 <component id='com.yahoo.vespa.hosted.provision.testutils.ServiceMonitorStub'/>
                 <component id='com.yahoo.vespa.hosted.provision.testutils.MockDuperModel'/>
                 <component id='com.yahoo.vespa.hosted.provision.testutils.MockNodeFlavors'/>
                 <component id='com.yahoo.vespa.hosted.provision.autoscale.QuestMetricsDb'/>
                 <component id='com.yahoo.vespa.hosted.provision.testutils.MockMetricsFetcher'/>
                 <component id='com.yahoo.vespa.hosted.provision.testutils.MockNodeRepository'/>
                 <component id='com.yahoo.vespa.hosted.provision.testutils.MockProvisionServiceProvider'/>
                 <component id='com.yahoo.vespa.hosted.provision.maintenance.NodeRepositoryMaintenance'/>
                 <component id='com.yahoo.vespa.flags.InMemoryFlagSource'/>
                 <component id='com.yahoo.config.provision.Zone'/>
                 <handler id='com.yahoo.vespa.hosted.provision.restapi.NodesV2ApiHandler'>
                   <binding>http://*/nodes/v2*</binding>
                 </handler>
                 <handler id='com.yahoo.vespa.hosted.provision.restapi.LoadBalancersV1ApiHandler'>
                   <binding>http://*/loadbalancers/v1*</binding>
                 </handler>
                 <http>
                   <server id='myServer' port='%s'/>
                 </http>
               </container>
               """.formatted(systemName.value(), cloudAccount.value(), port);
    }

}
