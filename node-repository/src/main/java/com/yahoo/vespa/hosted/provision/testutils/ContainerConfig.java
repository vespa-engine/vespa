// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

/**
 * For running NodeRepository API with some mocked data.
 * This is used by both NodeAdmin and NodeRepository tests.
 *
 * @author dybis
 */
public class ContainerConfig {

    public static String servicesXmlV2(int port) {
        return "<container version='1.0'>\n" +
               "  <config name=\"container.handler.threadpool\">\n" +
               "    <maxthreads>20</maxthreads>\n" +
               "  </config>\n" +
               "  <accesslog type='disabled'/>\n" +
               "  <component id='com.yahoo.test.ManualClock'/>\n" +
               "  <component id='com.yahoo.vespa.curator.mock.MockCurator'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.provision.testutils.OrchestratorMock'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.provision.testutils.MockDeployer'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.provision.testutils.MockInfraDeployer'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.provision.testutils.MockProvisioner'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.provision.testutils.TestHostLivenessTracker'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.provision.testutils.ServiceMonitorStub'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.provision.testutils.MockDuperModel'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.provision.testutils.MockNodeFlavors'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.provision.autoscale.QuestMetricsDb'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.provision.testutils.MockMetricsFetcher'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.provision.testutils.MockNodeRepository'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.provision.testutils.MockProvisionServiceProvider'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.provision.maintenance.NodeRepositoryMaintenance'/>\n" +
               "  <component id='com.yahoo.vespa.flags.InMemoryFlagSource'/>\n" +
               "  <component id='com.yahoo.config.provision.Zone'/>\n" +
               "  <handler id='com.yahoo.vespa.hosted.provision.restapi.NodesV2ApiHandler'>\n" +
               "    <binding>http://*/nodes/v2/*</binding>\n" +
               "  </handler>\n" +
               "  <handler id='com.yahoo.vespa.hosted.provision.restapi.LoadBalancersV1ApiHandler'>\n" +
               "    <binding>http://*/loadbalancers/v1/*</binding>\n" +
               "  </handler>\n" +
               "  <http>\n" +
               "    <server id='myServer' port='" + port + "'/>\n" +
               "  </http>\n" +
               "</container>";
    }

}
