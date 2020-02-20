// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi;

import com.yahoo.application.Networking;
import com.yahoo.application.container.JDisc;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.api.OktaAccessToken;
import com.yahoo.vespa.athenz.api.OktaIdentityToken;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactoryMock;
import org.junit.After;
import org.junit.Before;

import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;

import static com.yahoo.vespa.hosted.controller.integration.AthenzFilterMock.IDENTITY_HEADER_NAME;
import static com.yahoo.vespa.hosted.controller.integration.AthenzFilterMock.OKTA_ACCESS_TOKEN_HEADER_NAME;
import static com.yahoo.vespa.hosted.controller.integration.AthenzFilterMock.OKTA_IDENTITY_TOKEN_HEADER_NAME;
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

    protected static final AthenzUser hostedOperator = AthenzUser.fromUserId("alice");
    protected static final AthenzUser defaultUser = AthenzUser.fromUserId("bob");

    protected JDisc container;

    @Before
    public void startContainer() {
        container = JDisc.fromServicesXml(controllerServicesXml(), Networking.disable);
        addUserToHostedOperatorRole(hostedOperator);
    }

    @After
    public void stopContainer() { container.close(); }

    private String controllerServicesXml() {
        return "<container version='1.0'>\n" +
               "  <config name=\"container.handler.threadpool\">\n" +
               "    <maxthreads>10</maxthreads>\n" +
               "  </config> \n" +
               "  <config name=\"cloud.config.configserver\">\n" +
               "    <system>" + system().value() + "</system>\n" +
               "  </config> \n" +
               "  <config name=\"vespa.hosted.rotation.config.rotations\">\n" +
               "    <rotations>\n" +
               "      <item key=\"rotation-id-1\">rotation-fqdn-1</item>\n" +
               "      <item key=\"rotation-id-2\">rotation-fqdn-2</item>\n" +
               "      <item key=\"rotation-id-3\">rotation-fqdn-3</item>\n" +
               "      <item key=\"rotation-id-4\">rotation-fqdn-4</item>\n" +
               "      <item key=\"rotation-id-5\">rotation-fqdn-5</item>\n" +
               "    </rotations>\n" +
               "  </config>\n" +
               "  " +
               "<accesslog type='disabled'/>\n" +
               "  <component id='com.yahoo.vespa.flags.InMemoryFlagSource'/>\n" +
               "  <component id='com.yahoo.vespa.configserver.flags.db.FlagsDbImpl'/>\n" +
               "  <component id='com.yahoo.vespa.curator.mock.MockCurator'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactoryMock'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.controller.integration.ServiceRegistryMock'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.controller.Controller'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.controller.integration.ConfigServerProxyMock'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.controller.maintenance.ControllerMaintenance'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.controller.maintenance.JobControl'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.controller.api.integration.stubs.MockMavenRepository'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.controller.api.integration.stubs.MockUserManagement'/>\n" +
               "  <handler id='com.yahoo.vespa.hosted.controller.restapi.deployment.DeploymentApiHandler'>\n" +
               "    <binding>http://*/deployment/v1/*</binding>\n" +
               "    <binding>http://*/api/deployment/v1/*</binding>\n" +
               "  </handler>\n" +
               "  <handler id='com.yahoo.vespa.hosted.controller.restapi.deployment.BadgeApiHandler'>\n" +
               "    <binding>http://*/badge/v1/*</binding>\n" +
               "  </handler>\n" +
               "  <handler id='com.yahoo.vespa.hosted.controller.restapi.controller.ControllerApiHandler'>\n" +
               "    <binding>http://*/controller/v1/*</binding>\n" +
               "  </handler>\n" +
               "  <handler id='com.yahoo.vespa.hosted.controller.restapi.os.OsApiHandler'>\n" +
               "    <binding>http://*/os/v1/*</binding>\n" +
               "  </handler>\n" +
               "  <handler id='com.yahoo.vespa.hosted.controller.restapi.zone.v2.ZoneApiHandler'>\n" +
               "    <binding>http://*/zone/v2</binding>\n" +
               "    <binding>http://*/zone/v2/*</binding>\n" +
               "  </handler>\n" +
               "  <handler id='com.yahoo.vespa.hosted.controller.restapi.configserver.ConfigServerApiHandler'>\n" +
               "    <binding>http://*/configserver/v1</binding>\n" +
               "    <binding>http://*/configserver/v1/*</binding>\n" +
               "    <binding>http://*/api/configserver/v1</binding>\n" +
               "    <binding>http://*/api/configserver/v1/*</binding>\n" +
               "  </handler>\n" +
               "  <handler id='com.yahoo.vespa.hosted.controller.restapi.flags.AuditedFlagsHandler'>\n" +
               "    <binding>http://*/flags/v1</binding>\n" +
               "    <binding>http://*/flags/v1/*</binding>\n" +
               "  </handler>\n" +
               "  <handler id='com.yahoo.vespa.hosted.controller.restapi.user.UserApiHandler'>\n" +
               "    <binding>http://*/user/v1/*</binding>\n" +
               "    <binding>http://*/api/user/v1/*</binding>\n" +
               "  </handler>\n" +
               "  <handler id='com.yahoo.vespa.hosted.controller.restapi.routing.RoutingApiHandler'>\n" +
               "    <binding>http://*/routing/v1/*</binding>\n" +
               "  </handler>\n" +
               variablePartXml() +
               "</container>";
    }

    protected SystemName system() {
        return SystemName.main;
    }

    protected String variablePartXml() {
        return "  <component id='com.yahoo.vespa.hosted.controller.security.AthenzAccessControlRequests'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.controller.athenz.impl.AthenzFacade'/>\n" +

               "  <handler id='com.yahoo.vespa.hosted.controller.restapi.application.ApplicationApiHandler'>\n" +
               "    <binding>http://*/application/v4/*</binding>\n" +
               "  </handler>\n" +
               "  <handler id='com.yahoo.vespa.hosted.controller.restapi.athenz.AthenzApiHandler'>\n" +
               "    <binding>http://*/athenz/v1/*</binding>\n" +
               "    <binding>http://*/api/athenz/v1/*</binding>\n" +
               "  </handler>\n" +
               "  <handler id='com.yahoo.vespa.hosted.controller.restapi.zone.v1.ZoneApiHandler'>\n" +
               "    <binding>http://*/zone/v1</binding>\n" +
               "    <binding>http://*/zone/v1/*</binding>\n" +
               "  </handler>\n" +

               "  <http>\n" +
               "    <server id='default' port='8080' />\n" +
               "    <filtering>\n" +
               "      <request-chain id='default'>\n" +
               "        <filter id='com.yahoo.vespa.hosted.controller.integration.AthenzFilterMock'/>\n" +
               "        <filter id='com.yahoo.vespa.hosted.controller.restapi.filter.AthenzRoleFilter'/>\n" +
               "        <filter id='com.yahoo.vespa.hosted.controller.restapi.filter.ControllerAuthorizationFilter'/>\n" +
               "        <binding>http://*/*</binding>\n" +
               "      </request-chain>\n" +
               "    </filtering>\n" +
               "  </http>\n";
    }

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
        return addIdentityToRequest(new Request(uri), defaultUser);
    }

    protected static Request authenticatedRequest(String uri, String body, Request.Method method) {
        return addIdentityToRequest(new Request(uri, body, method), defaultUser);
    }

    protected static Request operatorRequest(String uri) {
        return addIdentityToRequest(new Request(uri), hostedOperator);
    }

    protected static Request operatorRequest(String uri, String body, Request.Method method) {
        return addIdentityToRequest(new Request(uri, body, method), hostedOperator);
    }

    protected static Request addIdentityToRequest(Request request, AthenzIdentity identity) {
        request.getHeaders().put(IDENTITY_HEADER_NAME, identity.getFullName());
        return request;
    }

    protected static Request addOktaIdentityToken(Request request, OktaIdentityToken token) {
        request.getHeaders().put(OKTA_IDENTITY_TOKEN_HEADER_NAME, token.token());
        return request;
    }

    protected static Request addOktaAccessToken(Request request, OktaAccessToken token) {
        request.getHeaders().put(OKTA_ACCESS_TOKEN_HEADER_NAME, token.token());
        return request;
    }

    protected void addUserToHostedOperatorRole(AthenzIdentity athenzIdentity) {
        AthenzClientFactoryMock mock = (AthenzClientFactoryMock) container.components()
                .getComponent(AthenzClientFactoryMock.class.getName());
        mock.getSetup().addHostedOperator(athenzIdentity);
    }

}
