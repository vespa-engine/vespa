// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi;

import com.yahoo.application.Networking;
import com.yahoo.application.container.JDisc;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.api.NToken;
import com.yahoo.vespa.hosted.controller.athenz.mock.AthenzClientFactoryMock;
import org.junit.After;
import org.junit.Before;

import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;

import static com.yahoo.vespa.hosted.controller.AthenzFilterMock.ATHENZ_NTOKEN_HEADER_NAME;
import static com.yahoo.vespa.hosted.controller.AthenzFilterMock.IDENTITY_HEADER_NAME;
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

    public static final AthenzUser USER = AthenzUser.fromUserId("bob");
    protected JDisc container;

    @Before
    public void startContainer() { container = JDisc.fromServicesXml(controllerServicesXml, Networking.disable); }

    @After
    public void stopContainer() { container.close(); }

    private final String controllerServicesXml =
            "<jdisc version='1.0'>\n" +
            "  <config name='vespa.hosted.zone.config.zone'>\n" +
            "    <system>main</system>\n" +
            "  </config>\n" +
            "  <config name=\"vespa.hosted.rotation.config.rotations\">\n" +
            "    <rotations>\n" +
            "      <item key=\"rotation-id-1\">rotation-fqdn-1</item>\n" +
            "      <item key=\"rotation-id-2\">rotation-fqdn-2</item>\n" +
            "      <item key=\"rotation-id-3\">rotation-fqdn-3</item>\n" +
            "      <item key=\"rotation-id-4\">rotation-fqdn-4</item>\n" +
            "      <item key=\"rotation-id-5\">rotation-fqdn-5</item>\n" +
            "    </rotations>\n" +
            "  </config>\n" +
            "  <component id='com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb'/>\n" +
            "  <component id='com.yahoo.vespa.hosted.controller.athenz.mock.AthenzClientFactoryMock'/>\n" +
            "  <component id='com.yahoo.vespa.hosted.controller.api.integration.chef.ChefMock'/>\n" +
            "  <component id='com.yahoo.vespa.hosted.controller.api.integration.dns.MemoryNameService'/>\n" +
            "  <component id='com.yahoo.vespa.hosted.controller.api.integration.entity.MemoryEntityService'/>\n" +
            "  <component id='com.yahoo.vespa.hosted.controller.api.integration.github.GitHubMock'/>\n" +
            "  <component id='com.yahoo.vespa.hosted.controller.api.integration.routing.MemoryGlobalRoutingService'/>\n" +
            "  <component id='com.yahoo.vespa.hosted.controller.api.integration.stubs.LoggingDeploymentIssues'/>\n" +
            "  <component id='com.yahoo.vespa.hosted.controller.api.integration.stubs.DummyOwnershipIssues'/>\n" +
            "  <component id='com.yahoo.vespa.hosted.controller.api.integration.organization.MockOrganization'/>\n" +
            "  <component id='com.yahoo.vespa.hosted.controller.ConfigServerClientMock'/>\n" +
            "  <component id='com.yahoo.vespa.hosted.controller.NodeRepositoryClientMock'/>\n" +
            "  <component id='com.yahoo.vespa.hosted.controller.ZoneRegistryMock'/>\n" +
            "  <component id='com.yahoo.vespa.hosted.controller.Controller'/>\n" +
            "  <component id='com.yahoo.vespa.hosted.controller.ConfigServerProxyMock'/>\n" +
            "  <component id='com.yahoo.vespa.hosted.controller.integration.MockMetricsService'/>\n" +
            "  <component id='com.yahoo.vespa.hosted.controller.maintenance.ControllerMaintenance'/>\n" +
            "  <component id='com.yahoo.vespa.hosted.controller.maintenance.JobControl'/>\n" +
            "  <component id='com.yahoo.vespa.hosted.controller.persistence.MemoryControllerDb'/>\n" +
            "  <component id='com.yahoo.vespa.hosted.controller.routing.MockRoutingGenerator'/>\n" +
            "  <component id='com.yahoo.vespa.hosted.controller.ArtifactRepositoryMock'/>\n" +
            "  <handler id='com.yahoo.vespa.hosted.controller.restapi.application.ApplicationApiHandler'>\n" +
            "    <binding>http://*/application/v4/*</binding>\n" +
            "  </handler>\n" +
            "  <handler id='com.yahoo.vespa.hosted.controller.restapi.deployment.DeploymentApiHandler'>\n" +
            "    <binding>http://*/deployment/v1/*</binding>\n" +
            "  </handler>\n" +
            "  <handler id='com.yahoo.vespa.hosted.controller.restapi.controller.ControllerApiHandler'>\n" +
            "    <binding>http://*/controller/v1/*</binding>\n" +
            "  </handler>\n" +
            "  <handler id='com.yahoo.vespa.hosted.controller.restapi.screwdriver.ScrewdriverApiHandler'>\n" +
            "    <binding>http://*/screwdriver/v1/*</binding>\n" +
            "  </handler>\n" +
            "  <handler id='com.yahoo.vespa.hosted.controller.restapi.zone.v1.ZoneApiHandler'>\n" +
            "    <binding>http://*/zone/v1</binding>\n" +
            "    <binding>http://*/zone/v1/*</binding>\n" +
            "  </handler>\n" +
            "  <handler id='com.yahoo.vespa.hosted.controller.restapi.zone.v2.ZoneApiHandler'>\n" +
            "    <binding>http://*/zone/v2</binding>\n" +
            "    <binding>http://*/zone/v2/*</binding>\n" +
            "  </handler>\n" +
            "  <http>\n" +
            "    <server id='default' port='8080' />\n" +
            "    <filtering>\n" +
            "      <request-chain id='default'>\n" +
            "        <filter id='com.yahoo.vespa.hosted.controller.AthenzFilterMock'/>\n" +
            "        <filter id='com.yahoo.vespa.hosted.controller.restapi.filter.ControllerAuthorizationFilter'/>\n" +
            "        <binding>http://*/*</binding>\n" +
            "      </request-chain>\n" +
            "    </filtering>\n" +
            "  </http>\n" +
            "</jdisc>";

    protected void assertResponse(Request request, int responseStatus, String responseMessage) {
        Response response = container.handleRequest(request);
        // Compare both status and message at once for easier diagnosis
        try {
            assertEquals("status: " + responseStatus + "\nmessage: " + responseMessage,
                         "status: " + response.getStatus() + "\nmessage: " + response.getBodyAsString());
        } catch (CharacterCodingException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected static Request authenticatedRequest(String uri) {
        return addIdentityToRequest(new Request(uri), USER);
    }

    protected static Request authenticatedRequest(String uri, String body, Request.Method method) {
        return addIdentityToRequest(new Request(uri, body, method), USER);
    }

    protected static Request authenticatedRequest(String uri, byte[] body, Request.Method method) {
        return addIdentityToRequest(new Request(uri, body, method), USER);
    }

    protected static Request addIdentityToRequest(Request request, AthenzIdentity identity) {
        request.getHeaders().put(IDENTITY_HEADER_NAME, identity.getFullName());
        return request;
    }

    protected static Request addNTokenToRequest(Request request, NToken nToken) {
        request.getHeaders().put(ATHENZ_NTOKEN_HEADER_NAME, nToken.getRawToken());
        return request;
    }

    protected void addUserToHostedOperatorRole(AthenzIdentity athenzIdentity) {
        AthenzClientFactoryMock mock = (AthenzClientFactoryMock) container.components()
                .getComponent(AthenzClientFactoryMock.class.getName());
        mock.getSetup().addHostedOperator(athenzIdentity);
    }

}
