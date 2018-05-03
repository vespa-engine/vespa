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
                return
                        "<jdisc version='1.0'>" +
                        "  <component id='com.yahoo.test.ManualClock'/>" +
                        "  <component id='com.yahoo.vespa.curator.mock.MockCurator'/>" +
                        "  <component id='com.yahoo.vespa.hosted.provision.testutils.OrchestratorMock'/>" +
                        "  <component id='com.yahoo.vespa.hosted.provision.testutils.MockDeployer'/>" +
                        "  <component id='com.yahoo.vespa.hosted.provision.testutils.MockProvisioner'/>" +
                        "  <component id='com.yahoo.vespa.hosted.provision.testutils.TestHostLivenessTracker'/>" +
                        "  <component id='com.yahoo.vespa.hosted.provision.testutils.ServiceMonitorStub'/>" +
                        "  <component id='com.yahoo.vespa.hosted.provision.testutils.MockNodeFlavors'/>" +
                        "  <component id='com.yahoo.vespa.hosted.provision.testutils.MockNodeRepository'/>" +
                        "  <component id='com.yahoo.vespa.hosted.provision.maintenance.NodeRepositoryMaintenance'/>" +
                        "  <component id='com.yahoo.config.provision.Zone'/>" +
                        "  <handler id='com.yahoo.vespa.hosted.provision.restapi.v2.NodesApiHandler'>" +
                        "    <binding>http://*/nodes/v2/*</binding>" +
                        "  </handler>" +
                        "  <http>" +
                        "    <server id='myServer' port='" + port + "' />" +
                        "  </http>" +
                        "</jdisc>";
        }

}
