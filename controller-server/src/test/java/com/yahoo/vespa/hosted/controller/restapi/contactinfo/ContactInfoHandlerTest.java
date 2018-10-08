package com.yahoo.vespa.hosted.controller.restapi.contactinfo;

import com.yahoo.application.Networking;
import com.yahoo.application.container.JDisc;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
import com.yahoo.vespa.hosted.controller.restapi.ContainerControllerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import com.yahoo.vespa.hosted.controller.tenant.Contact;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class ContactInfoHandlerTest extends ControllerContainerTest {

    private ContainerControllerTester tester;

    @Override
    public void startContainer() { container = JDisc.fromServicesXml(controllerServicesXml, Networking.disable); }

    @Before
    public void before() {
        tester = new ContainerControllerTester(container, null);
    }

    @Test
    public void testGettingAndFeedingContactInfo() throws Exception {
        tester.createApplication();

        // No contact information available yet
        String notFoundMessage = "{\"error-code\":\"NOT_FOUND\",\"message\":\"Could not find contact info for tenant1\"}";
        assertResponse(new Request("https://localhost:8080/contactinfo/v1/tenant/tenant1"), 404, notFoundMessage);

        // Feed contact information for tenant1
        Contact contact = new Contact(URI.create("https://localhost:4444/"), URI.create("https://localhost:4444/"), URI.create("https://localhost:4444/"), Arrays.asList(Arrays.asList("foo", "bar")));
        Slime contactSlime = contact.toSlime();
        byte[] body = SlimeUtils.toJsonBytes(contactSlime);
        String expectedResponseMessage = "Added contact info for tenant1 - Contact{url=https://localhost:4444/, propertyUrl=https://localhost:4444/, issueTrackerUrl=https://localhost:4444/, persons=[[foo, bar]]}";
        assertResponse(new Request("https://localhost:8080/contactinfo/v1/tenant/tenant1", body, Request.Method.POST), 200, expectedResponseMessage);

        // Get contact information for tenant1
        Response response = container.handleRequest(new Request("https://localhost:8080/contactinfo/v1/tenant/tenant1"));
        Contact actualContact = Contact.fromSlime(SlimeUtils.jsonToSlime(response.getBody()));
        assertEquals(contact, actualContact);
    }

    private final String controllerServicesXml =
            "<jdisc version='1.0'>\n" +
                    "  <config name=\"container.handler.threadpool\">\n" +
                    "    <maxthreads>10</maxthreads>\n" +
                    "  </config> \n" +
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
                    "  <config name=\"jdisc.http.filter.security.cors.cors-filter\">\n" +
                    "    <allowedUrls>\n" +
                    "      <item>http://localhost</item>\n" +
                    "    </allowedUrls>\n" +
                    "  </config>\n" +
                    "  <component id='com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb'/>\n" +
                    "  <component id='com.yahoo.vespa.hosted.controller.athenz.mock.AthenzClientFactoryMock'/>\n" +
                    "  <component id='com.yahoo.vespa.hosted.controller.api.integration.chef.ChefMock'/>\n" +
                    "  <component id='com.yahoo.vespa.hosted.controller.api.integration.dns.MemoryNameService'/>\n" +
                    "  <component id='com.yahoo.vespa.hosted.controller.api.integration.entity.MemoryEntityService'/>\n" +
                    "  <component id='com.yahoo.vespa.hosted.controller.api.integration.github.GitHubMock'/>\n" +
                    "  <component id='com.yahoo.vespa.hosted.controller.api.integration.stubs.LoggingDeploymentIssues'/>\n" +
                    "  <component id='com.yahoo.vespa.hosted.controller.api.integration.stubs.DummyOwnershipIssues'/>\n" +
                    "  <component id='com.yahoo.vespa.hosted.controller.api.integration.stubs.MockRunDataStore'/>\n" +
                    "  <component id='com.yahoo.vespa.hosted.controller.api.integration.organization.MockOrganization'/>\n" +
                    "  <component id='com.yahoo.vespa.hosted.controller.integration.ConfigServerMock'/>\n" +
                    "  <component id='com.yahoo.vespa.hosted.controller.integration.NodeRepositoryClientMock'/>\n" +
                    "  <component id='com.yahoo.vespa.hosted.controller.integration.ZoneRegistryMock'/>\n" +
                    "  <component id='com.yahoo.vespa.hosted.controller.Controller'/>\n" +
                    "  <component id='com.yahoo.vespa.hosted.controller.api.integration.stubs.MockBuildService'/>\n" +
                    "  <component id='com.yahoo.vespa.hosted.controller.integration.ConfigServerProxyMock'/>\n" +
                    "  <component id='com.yahoo.vespa.hosted.controller.integration.MetricsServiceMock'/>\n" +
                    "  <component id='com.yahoo.vespa.hosted.controller.maintenance.ControllerMaintenance'/>\n" +
                    "  <component id='com.yahoo.vespa.hosted.controller.maintenance.JobControl'/>\n" +
                    "  <component id='com.yahoo.vespa.hosted.controller.integration.RoutingGeneratorMock'/>\n" +
                    "  <component id='com.yahoo.vespa.hosted.controller.integration.ArtifactRepositoryMock'/>\n" +
                    "  <component id='com.yahoo.vespa.hosted.controller.integration.ApplicationStoreMock'/>\n" +
                    "  <component id='com.yahoo.vespa.hosted.controller.api.integration.stubs.MockTesterCloud'/>\n" +
                    "  <handler id='com.yahoo.vespa.hosted.controller.restapi.application.ApplicationApiHandler'>\n" +
                    "    <binding>http://*/application/v4/*</binding>\n" +
                    "  </handler>\n" +
                    "  <handler id='com.yahoo.vespa.hosted.controller.restapi.contactinfo.ContactInfoHandler'>\n" +
                    "    <binding>https://*/contactinfo/v1/*</binding>\n" +
                    "  </handler>\n" +
                    "  <handler id='com.yahoo.vespa.hosted.controller.restapi.deployment.DeploymentApiHandler'>\n" +
                    "    <binding>http://*/deployment/v1/*</binding>\n" +
                    "  </handler>\n" +
                    "  <handler id='com.yahoo.vespa.hosted.controller.restapi.controller.ControllerApiHandler'>\n" +
                    "    <binding>http://*/controller/v1/*</binding>\n" +
                    "  </handler>\n" +
                    "  <handler id='com.yahoo.vespa.hosted.controller.restapi.os.OsApiHandler'>\n" +
                    "    <binding>http://*/os/v1/*</binding>\n" +
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
                    "        <filter id='com.yahoo.vespa.hosted.controller.integration.AthenzFilterMock'/>\n" +
                    "        <filter id='com.yahoo.vespa.hosted.controller.restapi.filter.ControllerAuthorizationFilter'/>\n" +
                    "        <binding>http://*/*</binding>\n" +
                    "      </request-chain>\n" +
                    "    </filtering>\n" +
                    "  </http>\n" +
                    "</jdisc>";
}