// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi;

import com.yahoo.application.Networking;
import com.yahoo.application.container.JDisc;
import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.api.OAuthCredentials;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactoryMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static com.yahoo.vespa.hosted.controller.integration.AthenzFilterMock.IDENTITY_HEADER_NAME;
import static com.yahoo.vespa.hosted.controller.integration.AthenzFilterMock.OKTA_ACCESS_TOKEN_HEADER_NAME;
import static com.yahoo.vespa.hosted.controller.integration.AthenzFilterMock.OKTA_IDENTITY_TOKEN_HEADER_NAME;

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

    @BeforeEach
    public void startContainer() {
        container = JDisc.fromServicesXml(controllerServicesXml(), networking());
        addUserToHostedOperatorRole(hostedOperator);
    }

    protected Networking networking() { return Networking.disable; }

    @AfterEach
    public void stopContainer() { container.close(); }

    private String controllerServicesXml() {
        return """
               <container version='1.0'>
                 <config name="container.handler.threadpool">
                   <maxthreads>10</maxthreads>
                 </config>
                 <config name="cloud.config.configserver">
                   <system>%s</system>
                 </config>
                 <config name="vespa.hosted.rotation.config.rotations">
                   <rotations>
                     <item key="rotation-id-1">rotation-fqdn-1</item>
                     <item key="rotation-id-2">rotation-fqdn-2</item>
                     <item key="rotation-id-3">rotation-fqdn-3</item>
                     <item key="rotation-id-4">rotation-fqdn-4</item>
                     <item key="rotation-id-5">rotation-fqdn-5</item>
                   </rotations>
                 </config>
                 <config name="vespa.hosted.controller.config.core-dump-token-resealing">
                   <resealingPrivateKeyName>a.really.cool.key</resealingPrivateKeyName>
                 </config>
               
                 <accesslog type='disabled'/>
               
                 <component id='com.yahoo.vespa.flags.InMemoryFlagSource'/>
                 <component id='com.yahoo.vespa.configserver.flags.db.FlagsDbImpl'/>
                 <component id='com.yahoo.vespa.curator.mock.MockCurator'/>
                 <component id='com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb'/>
                 <component id='com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactoryMock'/>
                 <component id='com.yahoo.vespa.hosted.controller.integration.ServiceRegistryMock'/>
                 <component id='com.yahoo.vespa.hosted.controller.Controller'/>
                 <component id='com.yahoo.vespa.hosted.controller.integration.ConfigServerProxyMock'/>
                 <component id='com.yahoo.vespa.hosted.controller.maintenance.ControllerMaintenance'/>
                 <component id='com.yahoo.vespa.hosted.controller.api.integration.stubs.MockMavenRepository'/>
                 <component id='com.yahoo.vespa.hosted.controller.api.integration.stubs.MockUserManagement'/>
                 <component id='com.yahoo.vespa.hosted.controller.integration.SecretStoreMock'/>
               
                 <handler id='com.yahoo.vespa.hosted.controller.restapi.deployment.DeploymentApiHandler'>
                   <binding>http://localhost/deployment/v1/*</binding>
                 </handler>
                 <handler id='com.yahoo.vespa.hosted.controller.restapi.deployment.BadgeApiHandler'>
                   <binding>http://localhost/badge/v1/*</binding>
                 </handler>
                 <handler id='com.yahoo.vespa.hosted.controller.restapi.deployment.CliApiHandler'>
                   <binding>http://localhost/cli/v1/*</binding>
                 </handler>
                 <handler id='com.yahoo.vespa.hosted.controller.restapi.controller.ControllerApiHandler'>
                   <binding>http://localhost/controller/v1/*</binding>
                 </handler>
                 <handler id='com.yahoo.vespa.hosted.controller.restapi.os.OsApiHandler'>
                   <binding>http://localhost/os/v1/*</binding>
                 </handler>
                 <handler id='com.yahoo.vespa.hosted.controller.restapi.zone.v2.ZoneApiHandler'>
                   <binding>http://localhost/zone/v2</binding>
                   <binding>http://localhost/zone/v2/*</binding>
                 </handler>
                 <handler id='com.yahoo.vespa.hosted.controller.restapi.configserver.ConfigServerApiHandler'>
                   <binding>http://localhost/configserver/v1</binding>
                   <binding>http://localhost/configserver/v1/*</binding>
                 </handler>
                 <handler id='com.yahoo.vespa.hosted.controller.restapi.flags.AuditedFlagsHandler'>
                   <binding>http://localhost/flags/v1</binding>
                   <binding>http://localhost/flags/v1/*</binding>
                 </handler>
                 <handler id='com.yahoo.vespa.hosted.controller.restapi.user.UserApiHandler'>
                   <binding>http://localhost/user/v1/*</binding>
                 </handler>
                 <handler id='com.yahoo.vespa.hosted.controller.restapi.routing.RoutingApiHandler'>
                   <binding>http://localhost/routing/v1/*</binding>
                 </handler>
                 <handler id='com.yahoo.vespa.hosted.controller.restapi.changemanagement.ChangeManagementApiHandler'>
                   <binding>http://localhost/changemanagement/v1/*</binding>
                 </handler>
               %s
               </container>
               """.formatted(system().value(), variablePartXml());
    }

    protected SystemName system() {
        return SystemName.main;
    }

    protected String variablePartXml() {
        return "  <component id='com.yahoo.vespa.hosted.controller.security.AthenzAccessControlRequests'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.controller.athenz.impl.AthenzFacade'/>\n" +

               "  <handler id='com.yahoo.vespa.hosted.controller.restapi.application.ApplicationApiHandler'>\n" +
               "    <binding>http://localhost/application/v4/*</binding>\n" +
               "  </handler>\n" +
               "  <handler id='com.yahoo.vespa.hosted.controller.restapi.athenz.AthenzApiHandler'>\n" +
               "    <binding>http://localhost/athenz/v1/*</binding>\n" +
               "  </handler>\n" +
               "  <handler id='com.yahoo.vespa.hosted.controller.restapi.zone.v1.ZoneApiHandler'>\n" +
               "    <binding>http://localhost/zone/v1</binding>\n" +
               "    <binding>http://localhost/zone/v1/*</binding>\n" +
               "  </handler>\n" +

               "  <http>\n" +
               "    <server id='default' port='8080' />\n" +
               "    <filtering>\n" +
               "      <request-chain id='default'>\n" +
               "        <filter id='com.yahoo.vespa.hosted.controller.integration.AthenzFilterMock'/>\n" +
               "        <filter id='com.yahoo.vespa.hosted.controller.restapi.filter.AthenzRoleFilter'/>\n" +
               "        <filter id='com.yahoo.vespa.hosted.controller.restapi.filter.ControllerAuthorizationFilter'/>\n" +
               "        <binding>http://localhost/*</binding>\n" +
               "      </request-chain>\n" +
               "    </filtering>\n" +
               "  </http>\n";
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

    protected static Request addOAuthCredentials(Request request, OAuthCredentials oAuthCredentials) {
        request.getHeaders().put(OKTA_IDENTITY_TOKEN_HEADER_NAME, oAuthCredentials.idToken());
        request.getHeaders().put(OKTA_ACCESS_TOKEN_HEADER_NAME, oAuthCredentials.accessToken());
        return request;
    }

    protected void addUserToHostedOperatorRole(AthenzIdentity athenzIdentity) {
        AthenzClientFactoryMock mock = (AthenzClientFactoryMock) container.components()
                .getComponent(AthenzClientFactoryMock.class.getName());
        mock.getSetup().addHostedOperator(athenzIdentity);
    }

}
