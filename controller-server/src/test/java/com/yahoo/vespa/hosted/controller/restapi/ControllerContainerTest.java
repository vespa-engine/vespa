// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi;

import com.yahoo.application.Networking;
import com.yahoo.application.container.JDisc;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Superclass of REST API tests which needs to set up a functional container instance.
 * 
 * This is a test superclass, not a tester because we need the start and stop methods.
 *
 * DO NOT ADD ANYTHING HERE: If you need additional fields and methods, create a tester
 * which gets the container instance at construction time (in the test method) instead.
 * 
 * @author bratseth
 */
public class ControllerContainerTest {

    protected JDisc container;
    @Before
    public void startContainer() { container = JDisc.fromServicesXml(controllerServicesXml, Networking.disable); }
    @After
    public void stopContainer() { container.close(); }

    private final String controllerServicesXml =
            "<jdisc version='1.0'>" +
            "  <config name='vespa.hosted.zone.config.zone'>" +
            "    <system>main</system>" +
            "  </config>" +
            "  <component id='com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb'/>" +
            "  <component id='com.yahoo.vespa.hosted.controller.athenz.mock.AthenzClientFactoryMock'/>" +
            "  <component id='com.yahoo.vespa.hosted.controller.api.integration.chef.ChefMock'/>" +
            "  <component id='com.yahoo.vespa.hosted.controller.api.integration.dns.MemoryNameService'/>" +
            "  <component id='com.yahoo.vespa.hosted.controller.api.integration.entity.MemoryEntityService'/>" +
            "  <component id='com.yahoo.vespa.hosted.controller.api.integration.github.GitHubMock'/>" +
            "  <component id='com.yahoo.vespa.hosted.controller.api.integration.jira.JiraMock'/>" +
            "  <component id='com.yahoo.vespa.hosted.controller.api.integration.routing.MemoryGlobalRoutingService'/>" +
            "  <component id='com.yahoo.vespa.hosted.controller.api.integration.stubs.ContactsMock'/>" +
            "  <component id='com.yahoo.vespa.hosted.controller.api.integration.stubs.LoggingIssues'/>" +
            "  <component id='com.yahoo.vespa.hosted.controller.api.integration.stubs.PropertiesMock'/>" +
            "  <component id='com.yahoo.vespa.hosted.controller.ConfigServerClientMock'/>" +
            "  <component id='com.yahoo.vespa.hosted.controller.ZoneRegistryMock'/>" +
            "  <component id='com.yahoo.vespa.hosted.controller.Controller'/>" +
            "  <component id='com.yahoo.vespa.hosted.controller.integration.MockMetricsService'/>" +
            "  <component id='com.yahoo.vespa.hosted.controller.maintenance.ControllerMaintenance'/>" +
            "  <component id='com.yahoo.vespa.hosted.controller.maintenance.JobControl'/>" +
            "  <component id='com.yahoo.vespa.hosted.controller.persistence.MemoryControllerDb'/>" +
            "  <component id='com.yahoo.vespa.hosted.controller.restapi.application.MockAuthorizer'/>" +
            "  <component id='com.yahoo.vespa.hosted.controller.routing.MockRoutingGenerator'/>" +
            "  <component id='com.yahoo.vespa.hosted.rotation.MemoryRotationRepository'/>" +
            "  <handler id='com.yahoo.vespa.hosted.controller.restapi.RootHandler'>" +
            "    <binding>http://*/</binding>" +
            "  </handler>" +
            "  <handler id='com.yahoo.vespa.hosted.controller.restapi.application.ApplicationApiHandler'>" +
            "    <binding>http://*/application/v4/*</binding>" +
            "  </handler>" +
            "  <handler id='com.yahoo.vespa.hosted.controller.restapi.deployment.DeploymentApiHandler'>" +
            "    <binding>http://*/deployment/v1/*</binding>" +
            "  </handler>" +
            "  <handler id='com.yahoo.vespa.hosted.controller.restapi.controller.ControllerApiHandler'>" +
            "    <binding>http://*/controller/v1/*</binding>" +
            "  </handler>" +
            "  <handler id='com.yahoo.vespa.hosted.controller.restapi.screwdriver.ScrewdriverApiHandler'>" +
            "    <binding>http://*/screwdriver/v1/*</binding>" +
            "  </handler>" +
            "</jdisc>";

    protected void assertResponse(Request request, int responseStatus, String responseMessage) throws IOException {
        Response response = container.handleRequest(request);
        // Compare both status and message at once for easier diagnosis
        assertEquals("status: " + responseStatus + "\nmessage: " + responseMessage,
                     "status: " + response.getStatus() + "\nmessage: " + response.getBodyAsString());
    }

}
