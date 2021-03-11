// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.MockLogRetriever;
import com.yahoo.vespa.config.server.MockProvisioner;
import com.yahoo.vespa.config.server.MockSecretStoreValidator;
import com.yahoo.vespa.config.server.MockTesterClient;
import com.yahoo.vespa.config.server.application.ApplicationCuratorDatabase;
import com.yahoo.vespa.config.server.application.ApplicationReindexing;
import com.yahoo.vespa.config.server.application.ClusterReindexing;
import com.yahoo.vespa.config.server.application.ClusterReindexing.Status;
import com.yahoo.vespa.config.server.application.HttpProxy;
import com.yahoo.vespa.config.server.application.OrchestratorMock;
import com.yahoo.vespa.config.server.deploy.DeployTester;
import com.yahoo.vespa.config.server.filedistribution.MockFileDistributionFactory;
import com.yahoo.vespa.config.server.http.HandlerTest;
import com.yahoo.vespa.config.server.http.HttpErrorResponse;
import com.yahoo.vespa.config.server.http.SecretStoreValidator;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;
import com.yahoo.vespa.config.server.http.StaticResponse;
import com.yahoo.vespa.config.server.http.v2.ApplicationHandler.ReindexingResponse;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.tenant.TestTenantRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static com.yahoo.config.model.api.container.ContainerServiceType.CLUSTERCONTROLLER_CONTAINER;
import static com.yahoo.container.jdisc.HttpRequest.createTestRequest;
import static com.yahoo.jdisc.http.HttpRequest.Method.DELETE;
import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static com.yahoo.jdisc.http.HttpRequest.Method.POST;
import static com.yahoo.test.json.JsonTestHelper.assertJsonEquals;
import static com.yahoo.vespa.config.server.http.HandlerTest.assertHttpStatusCodeAndMessage;
import static com.yahoo.vespa.config.server.http.SessionHandlerTest.getRenderedString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author hmusum
 */
public class ApplicationHandlerTest {

    private static final File testApp = new File("src/test/apps/app");
    private static final File testAppMultipleClusters = new File("src/test/apps/app-with-multiple-clusters");
    private static final File testAppJdiscOnly = new File("src/test/apps/app-jdisc-only");

    private final static TenantName mytenantName = TenantName.from("mytenant");
    private final static ApplicationId myTenantApplicationId = ApplicationId.from(mytenantName, ApplicationName.defaultName(), InstanceName.defaultName());
    private final static ApplicationId applicationId = ApplicationId.defaultId();
    private final static MockTesterClient testerClient = new MockTesterClient();
    private static final MockLogRetriever logRetriever = new MockLogRetriever();
    private static final Version vespaVersion = Version.fromString("7.8.9");
    private static final SecretStoreValidator secretStoreValidator = new MockSecretStoreValidator();

    private TenantRepository tenantRepository;
    private ApplicationRepository applicationRepository;
    private MockProvisioner provisioner;
    private OrchestratorMock orchestrator;
    private ManualClock clock;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        clock = new ManualClock();
        List<ModelFactory> modelFactories = List.of(DeployTester.createModelFactory(vespaVersion));
        ConfigserverConfig configserverConfig = new ConfigserverConfig.Builder()
                .configServerDBDir(temporaryFolder.newFolder().getAbsolutePath())
                .configDefinitionsDir(temporaryFolder.newFolder().getAbsolutePath())
                .fileReferencesDir(temporaryFolder.newFolder().getAbsolutePath())
                .build();
        provisioner = new MockProvisioner();
        tenantRepository = new TestTenantRepository.Builder()
                .withClock(clock)
                .withConfigserverConfig(configserverConfig)
                .withFileDistributionFactory(new MockFileDistributionFactory(configserverConfig))
                .withHostProvisionerProvider(HostProvisionerProvider.withProvisioner(provisioner, false))
                .withModelFactoryRegistry(new ModelFactoryRegistry(modelFactories))
                .build();
        tenantRepository.addTenant(mytenantName);
        orchestrator = new OrchestratorMock();
        applicationRepository = new ApplicationRepository.Builder()
                .withTenantRepository(tenantRepository)
                .withProvisioner(provisioner)
                .withOrchestrator(orchestrator)
                .withClock(clock)
                .withTesterClient(testerClient)
                .withLogRetriever(logRetriever)
                .withConfigserverConfig(configserverConfig)
                .withSecretStoreValidator(secretStoreValidator)
                .build();
    }

    @After
    public void shutdown() {
        tenantRepository.close();
    }

    @Test
    public void testDelete() throws Exception {
        TenantName foobar = TenantName.from("foobar");
        tenantRepository.addTenant(foobar);

        {
            applicationRepository.deploy(testApp, prepareParams(applicationId));
            Tenant mytenant = applicationRepository.getTenant(applicationId);
            deleteAndAssertOKResponse(mytenant, applicationId);
        }
        
        {
            applicationRepository.deploy(testApp, prepareParams(applicationId));
            deleteAndAssertOKResponseMocked(applicationId, true);
            applicationRepository.deploy(testApp, prepareParams(applicationId));

            ApplicationId fooId = new ApplicationId.Builder()
                    .tenant(foobar)
                    .applicationName("foo")
                    .instanceName("quux")
                    .build();
            PrepareParams prepareParams2 = new PrepareParams.Builder().applicationId(fooId).build();
            applicationRepository.deploy(testAppJdiscOnly, prepareParams2);

            assertApplicationExists(fooId, Zone.defaultZone());
            deleteAndAssertOKResponseMocked(fooId, true);
            assertApplicationExists(applicationId, Zone.defaultZone());

            deleteAndAssertOKResponseMocked(applicationId, true);
        }

        {
            ApplicationId baliId = new ApplicationId.Builder()
                    .tenant(mytenantName)
                    .applicationName("bali")
                    .instanceName("quux")
                    .build();
            PrepareParams prepareParamsBali = new PrepareParams.Builder().applicationId(baliId).build();
            applicationRepository.deploy(testApp, prepareParamsBali);
            deleteAndAssertOKResponseMocked(baliId, true);
        }
    }

    @Test
    public void testDeleteNonExistent() throws Exception {
        deleteAndAssertResponse(myTenantApplicationId,
                                Zone.defaultZone(),
                                Response.Status.NOT_FOUND,
                                HttpErrorResponse.errorCodes.NOT_FOUND,
                                "Unable to delete mytenant.default.default: Not found");
    }

    @Test
    public void testGet() throws Exception {
        PrepareParams prepareParams = new PrepareParams.Builder()
                .applicationId(applicationId)
                .vespaVersion(vespaVersion)
                .build();
        long sessionId = applicationRepository.deploy(testApp, prepareParams).sessionId();

        assertApplicationResponse(applicationId, Zone.defaultZone(), sessionId, true, vespaVersion);
        assertApplicationResponse(applicationId, Zone.defaultZone(), sessionId, false, vespaVersion);
    }

    @Test
    public void testGetQuota() throws Exception {
        PrepareParams prepareParams = new PrepareParams.Builder()
                .applicationId(applicationId)
                .vespaVersion(vespaVersion)
                .build();
        applicationRepository.deploy(testApp, prepareParams).sessionId();

        var url = toUrlPath(applicationId, Zone.defaultZone(), true) + "/quota";
        var response = createApplicationHandler().handle(createTestRequest(url, GET));
        assertEquals(200, response.getStatus());
        var renderedString = SessionHandlerTest.getRenderedString(response);

        assertEquals("{\"rate\":0.0}", renderedString);
    }

    @Test
    public void testReindex() throws Exception {
        ApplicationCuratorDatabase database = applicationRepository.getTenant(applicationId).getApplicationRepo().database();
        reindexing(applicationId, GET, "{\"error-code\": \"NOT_FOUND\", \"message\": \"Application 'default.default' not found\"}", 404);

        applicationRepository.deploy(testAppMultipleClusters, prepareParams(applicationId));
        ApplicationReindexing expected = ApplicationReindexing.empty();
        assertEquals(expected,
                     database.readReindexingStatus(applicationId).orElseThrow());

        clock.advance(Duration.ofSeconds(1));
        reindex(applicationId, "", "{\"message\":\"Reindexing document types [bar] in 'boo', [bar, bax, baz] in 'foo' of application default.default\"}");
        expected = expected.withReady("boo", "bar", clock.instant())
                           .withReady("foo", "bar", clock.instant())
                           .withReady("foo", "baz", clock.instant())
                           .withReady("foo", "bax", clock.instant());
        assertEquals(expected,
                     database.readReindexingStatus(applicationId).orElseThrow());

        clock.advance(Duration.ofSeconds(1));
        reindex(applicationId, "?indexedOnly=true", "{\"message\":\"Reindexing document types [bar] in 'foo' of application default.default\"}");
        expected = expected.withReady("foo", "bar", clock.instant());
        assertEquals(expected,
                     database.readReindexingStatus(applicationId).orElseThrow());

        clock.advance(Duration.ofSeconds(1));
        expected = expected.withReady("boo", "bar", clock.instant())
                           .withReady("foo", "bar", clock.instant())
                           .withReady("foo", "baz", clock.instant())
                           .withReady("foo", "bax", clock.instant());
        reindex(applicationId, "?clusterId=", "{\"message\":\"Reindexing document types [bar] in 'boo', [bar, bax, baz] in 'foo' of application default.default\"}");
        assertEquals(expected,
                     database.readReindexingStatus(applicationId).orElseThrow());

        clock.advance(Duration.ofSeconds(1));
        expected = expected.withReady("boo", "bar", clock.instant())
                           .withReady("foo", "bar", clock.instant());
        reindex(applicationId, "?documentType=bar", "{\"message\":\"Reindexing document types [bar] in 'boo', [bar] in 'foo' of application default.default\"}");
        assertEquals(expected,
                     database.readReindexingStatus(applicationId).orElseThrow());

        clock.advance(Duration.ofSeconds(1));
        reindex(applicationId, "?clusterId=foo,boo", "{\"message\":\"Reindexing document types [bar] in 'boo', [bar, bax, baz] in 'foo' of application default.default\"}");
        expected = expected.withReady("boo", "bar", clock.instant())
                           .withReady("foo", "bar", clock.instant())
                           .withReady("foo", "baz", clock.instant())
                           .withReady("foo", "bax", clock.instant());
        assertEquals(expected,
                     database.readReindexingStatus(applicationId).orElseThrow());

        clock.advance(Duration.ofSeconds(1));
        reindex(applicationId, "?clusterId=foo&documentType=bar,baz", "{\"message\":\"Reindexing document types [bar, baz] in 'foo' of application default.default\"}");
        expected = expected.withReady("foo", "bar", clock.instant())
                           .withReady("foo", "baz", clock.instant());
        assertEquals(expected,
                     database.readReindexingStatus(applicationId).orElseThrow());

        reindexing(applicationId, DELETE, "{\"message\":\"Reindexing disabled\"}");
        expected = expected.enabled(false);
        assertEquals(expected,
                     database.readReindexingStatus(applicationId).orElseThrow());

        reindexing(applicationId, POST, "{\"message\":\"Reindexing enabled\"}");
        expected = expected.enabled(true);
        assertEquals(expected,
                     database.readReindexingStatus(applicationId).orElseThrow());

        applicationRepository.modifyReindexing(applicationId, reindexing -> reindexing.withPending("boo", "bar", 123L));

        long now = clock.instant().toEpochMilli();
        reindexing(applicationId, GET, "{" +
                                       "  \"enabled\": true," +
                                       "  \"clusters\": {" +
                                       "    \"boo\": {" +
                                       "      \"pending\": {" +
                                       "        \"bar\": 123" +
                                       "      }," +
                                       "      \"ready\": {" +
                                       "        \"bar\": {" +
                                       "          \"readyMillis\": " + (now - 1000) +
                                       "        }," +
                                       "      }" +
                                       "    }," +
                                       "    \"foo\": {" +
                                       "      \"pending\": {}," +
                                       "      \"ready\": {" +
                                       "        \"bar\": {" +
                                       "          \"readyMillis\": " + now +
                                       "        }," +
                                       "        \"bax\": {" +
                                       "          \"readyMillis\": " + (now - 1000) +
                                       "        }," +
                                       "        \"baz\": {" +
                                       "          \"readyMillis\": " + now +
                                       "        }" +
                                       "      }" +
                                       "    }" +
                                       "  }" +
                                       "}");
    }

    @Test
    public void testRestart() throws Exception {
        applicationRepository.deploy(testApp, prepareParams(applicationId));
        assertFalse(provisioner.restarted());
        restart(applicationId, Zone.defaultZone());
        assertTrue(provisioner.restarted());
        assertEquals(applicationId, provisioner.lastApplicationId());
    }

    @Test
    public void testSuspended() throws Exception {
        applicationRepository.deploy(testApp, prepareParams(applicationId));
        assertSuspended(false, applicationId, Zone.defaultZone());
        orchestrator.suspend(applicationId);
        assertSuspended(true, applicationId, Zone.defaultZone());
    }

    @Test
    public void testConverge() throws Exception {
        applicationRepository.deploy(testApp, prepareParams(applicationId));
        converge(applicationId, Zone.defaultZone());
    }

    @Test
    public void testClusterControllerStatus() throws Exception {
        applicationRepository.deploy(testApp, prepareParams(applicationId));
        String host = "foo.yahoo.com";
        String url = toUrlPath(applicationId, Zone.defaultZone(), true) + "/clustercontroller/" + host + "/status/v1/clusterName1";
        HttpProxy mockHttpProxy = mock(HttpProxy.class);
        ApplicationRepository applicationRepository = new ApplicationRepository.Builder()
                .withTenantRepository(tenantRepository)
                .withHostProvisionerProvider(HostProvisionerProvider.empty())
                .withOrchestrator(orchestrator)
                .withTesterClient(testerClient)
                .withHttpProxy(mockHttpProxy)
                .build();
        ApplicationHandler mockHandler = createApplicationHandler(applicationRepository);
        when(mockHttpProxy.get(any(), eq(host), eq(CLUSTERCONTROLLER_CONTAINER.serviceName), eq("clustercontroller-status/v1/clusterName1")))
                .thenReturn(new StaticResponse(200, "text/html", "<html>...</html>"));

        HttpResponse response = mockHandler.handle(createTestRequest(url, GET));
        assertHttpStatusCodeAndMessage(response, 200, "text/html", "<html>...</html>");
    }

    @Test
    public void testPutIsIllegal() throws IOException {
        assertNotAllowed(Method.PUT);
    }

    @Test
    public void testFileDistributionStatus() throws Exception {
        applicationRepository.deploy(testApp, prepareParams(applicationId));
        Zone zone = Zone.defaultZone();

        HttpResponse response = fileDistributionStatus(applicationId, zone);
        assertEquals(200, response.getStatus());
        assertEquals("{\"hosts\":[{\"hostname\":\"mytesthost\",\"status\":\"UNKNOWN\",\"message\":\"error: Connection error(104)\",\"fileReferences\":[]}],\"status\":\"UNKNOWN\"}",
                     getRenderedString(response));

        // 404 for unknown application
        ApplicationId unknown = new ApplicationId.Builder().applicationName("unknown").tenant("default").build();
        HttpResponse responseForUnknown = fileDistributionStatus(unknown, zone);
        assertEquals(404, responseForUnknown.getStatus());
        assertEquals("{\"error-code\":\"NOT_FOUND\",\"message\":\"Unknown application id 'default.unknown'\"}",
                     getRenderedString(responseForUnknown));
    }

    @Test
    public void testGetLogs() throws IOException {
        applicationRepository.deploy(new File("src/test/apps/app-logserver-with-container"), prepareParams(applicationId));
        String url = toUrlPath(applicationId, Zone.defaultZone(), true) + "/logs?from=100&to=200";
        ApplicationHandler mockHandler = createApplicationHandler();

        HttpResponse response = mockHandler.handle(createTestRequest(url, GET));
        assertEquals(200, response.getStatus());

        assertEquals("log line", getRenderedString(response));
    }

    @Test
    public void testValidateSecretStore() throws IOException {
        applicationRepository.deploy(new File("src/test/apps/app-logserver-with-container"), prepareParams(applicationId));
        var url = toUrlPath(applicationId, Zone.defaultZone(), true) + "/validate-secret-store";
        var mockHandler = createApplicationHandler();

        var requestString = "{\"name\":\"store\",\"awsId\":\"aws-id\",\"role\":\"role\",\"region\":\"us-west-1\",\"parameterName\":\"some-parameter\"}";
        var requestData = new ByteArrayInputStream(requestString.getBytes(StandardCharsets.UTF_8));
        var response = mockHandler.handle(createTestRequest(url, POST, requestData));
        assertEquals(200, response.getStatus());


        // MockSecretStoreValidator simply returns the request body
        assertEquals(requestString, getRenderedString(response));
    }

    @Test
    public void testTesterStatus() throws IOException {
        applicationRepository.deploy(testApp, prepareParams(applicationId));
        String url = toUrlPath(applicationId, Zone.defaultZone(), true) + "/tester/status";
        ApplicationHandler mockHandler = createApplicationHandler();
        HttpResponse response = mockHandler.handle(createTestRequest(url, GET));
        assertEquals(200, response.getStatus());
        assertEquals("OK", getRenderedString(response));
    }

    @Test
    public void testTesterGetLog() throws IOException {
        applicationRepository.deploy(testApp, prepareParams(applicationId));
        String url = toUrlPath(applicationId, Zone.defaultZone(), true) + "/tester/log?after=1234";
        ApplicationHandler mockHandler = createApplicationHandler();

        HttpResponse response = mockHandler.handle(createTestRequest(url, GET));
        assertEquals(200, response.getStatus());
        assertEquals("log", getRenderedString(response));
    }

    @Test
    public void testTesterStartTests() {
        applicationRepository.deploy(testApp, prepareParams(applicationId));
        String url = toUrlPath(applicationId, Zone.defaultZone(), true) + "/tester/run/staging-test";
        ApplicationHandler mockHandler = createApplicationHandler();

        InputStream requestData =  new ByteArrayInputStream("foo".getBytes(StandardCharsets.UTF_8));
        HttpRequest testRequest = createTestRequest(url, POST, requestData);
        HttpResponse response = mockHandler.handle(testRequest);
        assertEquals(200, response.getStatus());
    }

    @Test
    public void testTesterReady() {
        applicationRepository.deploy(testApp, prepareParams(applicationId));
        String url = toUrlPath(applicationId, Zone.defaultZone(), true) + "/tester/ready";
        ApplicationHandler mockHandler = createApplicationHandler();
        HttpRequest testRequest = createTestRequest(url, GET);
        HttpResponse response = mockHandler.handle(testRequest);
        assertEquals(200, response.getStatus());
    }

    @Test
    public void testGetTestReport() throws IOException {
        applicationRepository.deploy(testApp, prepareParams(applicationId));
        String url = toUrlPath(applicationId, Zone.defaultZone(), true) + "/tester/report";
        ApplicationHandler mockHandler = createApplicationHandler();
        HttpRequest testRequest = createTestRequest(url, GET);
        HttpResponse response = mockHandler.handle(testRequest);
        assertEquals(200, response.getStatus());
        assertEquals("report", getRenderedString(response));
    }

    @Test
    public void testClusterReindexingStateSerialization() {
        Stream.of(ClusterReindexing.State.values()).forEach(ClusterReindexing.State::toString);
    }

    @Test
    public void testReindexingSerialization() throws IOException {
        Instant now = Instant.ofEpochMilli(123456);
        ApplicationReindexing applicationReindexing = ApplicationReindexing.empty()
                                                                           .withPending("foo", "bar", 123L)
                                                                           .withReady("moo", "baz", now);
        ClusterReindexing clusterReindexing = new ClusterReindexing(Map.of("bax", new Status(now, null, null, null, null),
                                                                           "baz", new Status(now.plusSeconds(1),
                                                                                             now.plusSeconds(2),
                                                                                             ClusterReindexing.State.FAILED,
                                                                                             "message",
                                                                                             0.1)));
        Map<String, Set<String>> documentTypes = new TreeMap<>(Map.of("boo", new TreeSet<>(Set.of("bar", "baz", "bax")),
                                                                      "foo", new TreeSet<>(Set.of("bar", "hax")),
                                                                      "moo", new TreeSet<>(Set.of("baz", "bax"))));

        assertJsonEquals(getRenderedString(new ReindexingResponse(documentTypes,
                                                                  applicationReindexing,
                                                                  Map.of("boo", clusterReindexing,
                                                                         "moo", clusterReindexing))),
                         "{\n" +
                         "  \"enabled\": true,\n" +
                         "  \"clusters\": {\n" +
                         "    \"boo\": {\n" +
                         "      \"pending\": {},\n" +
                         "      \"ready\": {\n" +
                         "        \"bar\": {},\n" +
                         "        \"bax\": {\n" +
                         "          \"startedMillis\": 123456\n" +
                         "        },\n" +
                         "        \"baz\": {\n" +
                         "          \"startedMillis\": 124456,\n" +
                         "          \"endedMillis\": 125456,\n" +
                         "          \"state\": \"failed\",\n" +
                         "          \"message\": \"message\",\n" +
                         "          \"progress\": 0.1\n" +
                         "        }\n" +
                         "      }\n" +
                         "    },\n" +
                         "    \"foo\": {\n" +
                         "      \"pending\": {\n" +
                         "        \"bar\": 123\n" +
                         "      },\n" +
                         "      \"ready\": {\n" +
                         "        \"bar\": {},\n" +
                         "        \"hax\": {}\n" +
                         "      },\n" +
                         "    },\n" +
                         "    \"moo\": {\n" +
                         "      \"pending\": {},\n" +
                         "      \"ready\": {\n" +
                         "        \"bax\": {\n" +
                         "          \"startedMillis\": 123456\n" +
                         "        },\n" +
                         "        \"baz\": {\n" +
                         "          \"readyMillis\": 123456,\n" +
                         "          \"startedMillis\": 124456,\n" +
                         "          \"endedMillis\": 125456,\n" +
                         "          \"state\": \"failed\",\n" +
                         "          \"message\": \"message\",\n" +
                         "          \"progress\": 0.1\n" +
                         "        }\n" +
                         "      }\n" +
                         "    }\n" +
                         "  }\n" +
                         "}\n");
    }

    private void assertNotAllowed(Method method) throws IOException {
        String url = "http://myhost:14000/application/v2/tenant/" + mytenantName + "/application/default";
        deleteAndAssertResponse(url, Response.Status.METHOD_NOT_ALLOWED, HttpErrorResponse.errorCodes.METHOD_NOT_ALLOWED, "{\"error-code\":\"METHOD_NOT_ALLOWED\",\"message\":\"Method '" + method + "' is not supported\"}",
                                method);
    }

    private void deleteAndAssertOKResponseMocked(ApplicationId applicationId, boolean fullAppIdInUrl) throws IOException {
        Tenant tenant = applicationRepository.getTenant(applicationId);
        long sessionId = tenant.getApplicationRepo().requireActiveSessionOf(applicationId);
        deleteAndAssertResponse(applicationId, Zone.defaultZone(), Response.Status.OK, null, fullAppIdInUrl);
    }

    private void deleteAndAssertOKResponse(Tenant tenant, ApplicationId applicationId) throws IOException {
        long sessionId = tenant.getApplicationRepo().requireActiveSessionOf(applicationId);
        deleteAndAssertResponse(applicationId, Zone.defaultZone(), Response.Status.OK, null, true);
    }

    private void deleteAndAssertResponse(ApplicationId applicationId, Zone zone, int expectedStatus, HttpErrorResponse.errorCodes errorCode, boolean fullAppIdInUrl) throws IOException {
        String expectedResponse = "{\"message\":\"Application '" + applicationId + "' deleted\"}";
        deleteAndAssertResponse(toUrlPath(applicationId, zone, fullAppIdInUrl), expectedStatus, errorCode, expectedResponse, Method.DELETE);
    }

    private void deleteAndAssertResponse(ApplicationId applicationId, Zone zone, int expectedStatus, HttpErrorResponse.errorCodes errorCode, String expectedResponse) throws IOException {
        deleteAndAssertResponse(toUrlPath(applicationId, zone, true), expectedStatus, errorCode, expectedResponse, Method.DELETE);
    }

    private void deleteAndAssertResponse(String url, int expectedStatus, HttpErrorResponse.errorCodes errorCode, String expectedResponse, Method method) throws IOException {
        ApplicationHandler handler = createApplicationHandler();
        HttpResponse response = handler.handle(createTestRequest(url, method));
        if (expectedStatus == 200) {
            assertHttpStatusCodeAndMessage(response, 200, expectedResponse);
        } else {
            HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(response, expectedStatus, errorCode, expectedResponse);
        }
    }

    private void assertApplicationResponse(ApplicationId applicationId, Zone zone, long expectedGeneration,
                                           boolean fullAppIdInUrl, Version expectedVersion) throws IOException {
        assertApplicationResponse(toUrlPath(applicationId, zone, fullAppIdInUrl), expectedGeneration, expectedVersion);
    }

    private void assertSuspended(boolean expectedValue, ApplicationId application, Zone zone) throws IOException {
        String restartUrl = toUrlPath(application, zone, true) + "/suspended";
        HttpResponse response = createApplicationHandler().handle(createTestRequest(restartUrl, GET));
        assertHttpStatusCodeAndMessage(response, 200, "{\"suspended\":" + expectedValue + "}");
    }

    private String toUrlPath(ApplicationId application, Zone zone, boolean fullAppIdInUrl) {
        String url = "http://myhost:14000/application/v2/tenant/" + application.tenant().value() + "/application/" + application.application().value();
        if (fullAppIdInUrl)
            url = url + "/environment/" + zone.environment().value() + "/region/" + zone.region().value() + "/instance/" + application.instance().value();
        return url;
    }

    private void assertApplicationResponse(String url, long expectedGeneration, Version expectedVersion) throws IOException {
        HttpResponse response = createApplicationHandler().handle(createTestRequest(url, GET));
        assertEquals(200, response.getStatus());
        String renderedString = SessionHandlerTest.getRenderedString(response);
        assertEquals("{\"generation\":" + expectedGeneration +
                     ",\"applicationPackageFileReference\":\"./\"" +
                     ",\"modelVersions\":[\"" + expectedVersion.toFullString() + "\"]}", renderedString);
    }

    private void assertApplicationExists(ApplicationId applicationId, Zone zone) throws IOException {
        String tenantName = applicationId.tenant().value();
        String expected = "[\"http://myhost:14000/application/v2/tenant/" +
                          tenantName + "/application/" + applicationId.application().value() +
                          "/environment/" + zone.environment().value() +
                          "/region/" + zone.region().value() +
                          "/instance/" + applicationId.instance().value() + "\"]";
        ListApplicationsHandler listApplicationsHandler = new ListApplicationsHandler(ListApplicationsHandler.testOnlyContext(),
                                                                                      tenantRepository,
                                                                                      Zone.defaultZone());
        ListApplicationsHandlerTest.assertResponse(listApplicationsHandler,
                                                   "http://myhost:14000/application/v2/tenant/" + tenantName + "/application/",
                                                   Response.Status.OK,
                                                   expected,
                                                   GET);
    }

    private void reindexing(ApplicationId application, Method method, String expectedBody, int statusCode) throws IOException {
        String reindexingUrl = toUrlPath(application, Zone.defaultZone(), true) + "/reindexing";
        HttpResponse response = createApplicationHandler().handle(createTestRequest(reindexingUrl, method));
        if (expectedBody != null) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            response.render(out);
            assertJsonEquals(out.toString(), expectedBody);
        }
        assertEquals(statusCode, response.getStatus());
    }

    private void reindexing(ApplicationId application, Method method, String expectedBody) throws IOException {
        reindexing(application, method, expectedBody, 200);
    }

    private void reindex(ApplicationId application, String query, String message) throws IOException {
        String reindexUrl = toUrlPath(application, Zone.defaultZone(), true) + "/reindex" + query;
        assertHttpStatusCodeAndMessage(createApplicationHandler().handle(createTestRequest(reindexUrl, POST)), 200, message);
    }

    private void restart(ApplicationId application, Zone zone) throws IOException {
        String restartUrl = toUrlPath(application, zone, true) + "/restart";
        HttpResponse response = createApplicationHandler().handle(createTestRequest(restartUrl, POST));
        assertHttpStatusCodeAndMessage(response, 200, "");
    }

    private void converge(ApplicationId application, Zone zone) throws IOException {
        String convergeUrl = toUrlPath(application, zone, true) + "/serviceconverge";
        HttpResponse response = createApplicationHandler().handle(createTestRequest(convergeUrl, GET));
        assertHttpStatusCodeAndMessage(response, 200, "");
    }

    private HttpResponse fileDistributionStatus(ApplicationId application, Zone zone) {
        String restartUrl = toUrlPath(application, zone, true) + "/filedistributionstatus";
        return createApplicationHandler().handle(createTestRequest(restartUrl, GET));
    }

    private ApplicationHandler createApplicationHandler() {
        return createApplicationHandler(applicationRepository);
    }

    private ApplicationHandler createApplicationHandler(ApplicationRepository applicationRepository) {
        return new ApplicationHandler(ApplicationHandler.testOnlyContext(), Zone.defaultZone(), applicationRepository);
    }

    private PrepareParams prepareParams(ApplicationId applicationId) {
        return new PrepareParams.Builder().applicationId(applicationId).build();
    }

}
