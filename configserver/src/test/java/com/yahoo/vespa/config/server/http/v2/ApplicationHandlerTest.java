// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import ai.vespa.http.DomainName;
import ai.vespa.http.HttpURL;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.EndpointsChecker;
import com.yahoo.config.provision.EndpointsChecker.Availability;
import com.yahoo.config.provision.EndpointsChecker.Endpoint;
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
import com.yahoo.vespa.config.server.http.v2.response.ReindexingResponse;
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
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.yahoo.container.jdisc.HttpRequest.createTestRequest;
import static com.yahoo.jdisc.http.HttpRequest.Method.DELETE;
import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static com.yahoo.jdisc.http.HttpRequest.Method.POST;
import static com.yahoo.test.json.JsonTestHelper.assertJsonEquals;
import static com.yahoo.vespa.config.server.application.ConfigConvergenceChecker.ServiceListResponse;
import static com.yahoo.vespa.config.server.application.ConfigConvergenceChecker.ServiceResponse;
import static com.yahoo.vespa.config.server.http.HandlerTest.assertHttpStatusCodeAndMessage;
import static com.yahoo.vespa.config.server.http.SessionHandlerTest.getRenderedString;
import static com.yahoo.vespa.config.server.http.v2.ApplicationHandler.HttpServiceListResponse;
import static com.yahoo.vespa.config.server.http.v2.ApplicationHandler.HttpServiceResponse.createResponse;
import static com.yahoo.yolean.Exceptions.uncheck;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

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
    private List<Endpoint> expectedEndpoints;
    private Availability availability;

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
                .withHostProvisionerProvider(HostProvisionerProvider.withProvisioner(provisioner, configserverConfig))
                .withModelFactoryRegistry(new ModelFactoryRegistry(modelFactories))
                .build();
        tenantRepository.addTenant(mytenantName);
        orchestrator = new OrchestratorMock();
        applicationRepository = new ApplicationRepository.Builder()
                .withTenantRepository(tenantRepository)
                .withOrchestrator(orchestrator)
                .withClock(clock)
                .withTesterClient(testerClient)
                .withLogRetriever(logRetriever)
                .withConfigserverConfig(configserverConfig)
                .withSecretStoreValidator(secretStoreValidator)
                .withEndpointsChecker(endpoints -> { assertEquals(expectedEndpoints, endpoints); return availability; })
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
                                HttpErrorResponse.ErrorCode.NOT_FOUND,
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
        expected = expected.withReady("boo", "bar", clock.instant(), 1, "reindexing for an unknown reason")
                           .withReady("foo", "bar", clock.instant(), 1, "reindexing for an unknown reason")
                           .withReady("foo", "baz", clock.instant(), 1, "reindexing for an unknown reason")
                           .withReady("foo", "bax", clock.instant(), 1, "reindexing for an unknown reason");
        assertEquals(expected,
                     database.readReindexingStatus(applicationId).orElseThrow());

        clock.advance(Duration.ofSeconds(1));
        reindex(applicationId, "?indexedOnly=true&cause=test%20reindexing", "{\"message\":\"Reindexing document types [bar] in 'foo' of application default.default\"}");
        expected = expected.withReady("foo", "bar", clock.instant(), 1, "test reindexing");
        assertEquals(expected,
                     database.readReindexingStatus(applicationId).orElseThrow());

        clock.advance(Duration.ofSeconds(1));
        expected = expected.withReady("boo", "bar", clock.instant(), 1, "reindexing")
                           .withReady("foo", "bar", clock.instant(), 1, "reindexing")
                           .withReady("foo", "baz", clock.instant(), 1, "reindexing")
                           .withReady("foo", "bax", clock.instant(), 1, "reindexing");
        reindex(applicationId, "?clusterId=&cause=reindexing", "{\"message\":\"Reindexing document types [bar] in 'boo', [bar, bax, baz] in 'foo' of application default.default\"}");
        assertEquals(expected,
                     database.readReindexingStatus(applicationId).orElseThrow());

        clock.advance(Duration.ofSeconds(1));
        expected = expected.withReady("boo", "bar", clock.instant(), 1, "reindexing")
                           .withReady("foo", "bar", clock.instant(), 1, "reindexing");
        reindex(applicationId, "?documentType=bar&cause=reindexing", "{\"message\":\"Reindexing document types [bar] in 'boo', [bar] in 'foo' of application default.default\"}");
        assertEquals(expected,
                     database.readReindexingStatus(applicationId).orElseThrow());

        clock.advance(Duration.ofSeconds(1));
        reindex(applicationId, "?clusterId=foo,boo&cause=reindexing", "{\"message\":\"Reindexing document types [bar] in 'boo', [bar, bax, baz] in 'foo' of application default.default\"}");
        expected = expected.withReady("boo", "bar", clock.instant(), 1, "reindexing")
                           .withReady("foo", "bar", clock.instant(), 1, "reindexing")
                           .withReady("foo", "baz", clock.instant(), 1, "reindexing")
                           .withReady("foo", "bax", clock.instant(), 1, "reindexing");
        assertEquals(expected,
                     database.readReindexingStatus(applicationId).orElseThrow());

        clock.advance(Duration.ofSeconds(1));
        reindex(applicationId, "?clusterId=foo&documentType=bar,baz&speed=0.1&cause=reindexing", "{\"message\":\"Reindexing document types [bar, baz] in 'foo' of application default.default\"}");
        expected = expected.withReady("foo", "bar", clock.instant(), 0.1, "reindexing")
                           .withReady("foo", "baz", clock.instant(), 0.1, "reindexing");
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
                                       "          \"readyMillis\": " + (now - 1000) + ", " +
                                       "          \"speed\": 1.0," +
                                       "          \"cause\": \"reindexing\"" +
                                       "        }" +
                                       "      }" +
                                       "    }," +
                                       "    \"foo\": {" +
                                       "      \"pending\": {}," +
                                       "      \"ready\": {" +
                                       "        \"bar\": {" +
                                       "          \"readyMillis\": " + now + ", " +
                                       "          \"speed\": 0.1," +
                                       "          \"cause\": \"reindexing\"" +
                                       "        }," +
                                       "        \"bax\": {" +
                                       "          \"readyMillis\": " + (now - 1000) + ", " +
                                       "          \"speed\": 1.0," +
                                       "          \"cause\": \"reindexing\"" +
                                       "        }," +
                                       "        \"baz\": {" +
                                       "          \"readyMillis\": " + now + ", " +
                                       "          \"speed\": 0.1," +
                                       "          \"cause\": \"reindexing\"" +
                                       "        }" +
                                       "      }" +
                                       "    }" +
                                       "  }" +
                                       "}");
    }

    @Test
    public void testRestart() throws Exception {
        var result = applicationRepository.deploy(testApp, prepareParams(applicationId));
        assertTrue(result.configChangeActions().getRestartActions().isEmpty());
        restart(applicationId, Zone.defaultZone());
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
    public void testServiceStatus() throws Exception {
        applicationRepository.deploy(testApp, prepareParams(applicationId));
        String host = "foo.yahoo.com";
        HttpProxy mockHttpProxy = mock(HttpProxy.class);
        ApplicationRepository applicationRepository = new ApplicationRepository.Builder()
                .withTenantRepository(tenantRepository)
                .withOrchestrator(orchestrator)
                .withTesterClient(testerClient)
                .withHttpProxy(mockHttpProxy)
                .build();
        ApplicationHandler mockHandler = createApplicationHandler(applicationRepository);
        doAnswer(invoc -> new StaticResponse(200,
                                             "text/html",
                                             "<html>" +
                                             "host=" + invoc.getArgument(1, String.class) + "," +
                                             "service=" + invoc.getArgument(2, String.class) + "," +
                                             "path=" + invoc.getArgument(3, HttpURL.Path.class) + "," +
                                             "query=" + invoc.getArgument(4, HttpURL.Query.class) +
                                             (invoc.getArgument(5, HttpURL.class) == null ? "" : ("," +
                                             "forwardedUrl=" + invoc.getArgument(5, HttpURL.class))) +
                                             "</html>"))
                .when(mockHttpProxy).get(any(), any(), any(), any(), any(), any());

        HttpResponse response = mockHandler.handle(createTestRequest(toUrlPath(applicationId, Zone.defaultZone(), true) + "/service/container-clustercontroller/" + host + "/status/some/path/clusterName1?foo=bar", GET));
        assertHttpStatusCodeAndMessage(response, 200, "text/html", "<html>host=foo.yahoo.com,service=container-clustercontroller,path=path '/clustercontroller-status/v1/some/path/clusterName1',query=query 'foo=bar'</html>");

        String forwarded = "https://api:123/my/base/path?bar=%2E";
        response = mockHandler.handle(createTestRequest(toUrlPath(applicationId, Zone.defaultZone(), true) + "/service/distributor/" + host + "/state/v1/something/more?foo=bar&forwarded-url=" + URLEncoder.encode(forwarded, UTF_8), GET));
        assertHttpStatusCodeAndMessage(response, 200, "text/html", "<html>host=foo.yahoo.com,service=distributor,path=path '/state/v1/something/more',query=query 'foo=bar',forwardedUrl=https://api:123/my/base/path?bar=.</html>");

        response = mockHandler.handle(createTestRequest(toUrlPath(applicationId, Zone.defaultZone(), true) + "/service/distributor/" + host + "/status/something?foo=bar", GET));
        assertHttpStatusCodeAndMessage(response, 200, "text/html", "<html>host=foo.yahoo.com,service=distributor,path=path '/contentnode-status/v1/something',query=query 'foo=bar'</html>");

        response = mockHandler.handle(createTestRequest(toUrlPath(applicationId, Zone.defaultZone(), true) + "/service/fake-service/" + host + "/status/something?foo=bar", GET));
        assertHttpStatusCodeAndMessage(response, 404, "{\"error-code\":\"NOT_FOUND\",\"message\":\"No status page for service: fake-service\"}");
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
        var requestData = new ByteArrayInputStream(requestString.getBytes(UTF_8));
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

        InputStream requestData =  new ByteArrayInputStream("foo".getBytes(UTF_8));
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
    public void testVerifyEndpoints() {
        expectedEndpoints = List.of(new Endpoint(ClusterSpec.Id.from("bluster"),
                                                 HttpURL.from(URI.create("https://bluster.tld:1234")),
                                                 Optional.of(uncheck(() -> InetAddress.getByName("4.3.2.1"))),
                                                 Optional.of(DomainName.of("fluster.tld")),
                                                 false));
        availability = new Availability(EndpointsChecker.Status.available, "Endpoints are ready");
        ApplicationHandler handler = createApplicationHandler();
        HttpRequest request = createTestRequest(toUrlPath(applicationId, Zone.defaultZone(), true) + "/verify-endpoints",
                                                POST,
                                                new ByteArrayInputStream("""
                                                                         {
                                                                           "endpoints": [
                                                                             {
                                                                               "clusterName": "bluster",
                                                                               "url": "https://bluster.tld:1234",
                                                                               "ipAddress": "4.3.2.1",
                                                                               "canonicalName": "fluster.tld",
                                                                               "public": false
                                                                             }
                                                                           ]
                                                                         }""".getBytes(UTF_8)));
        HttpResponse response = handler.handle(request);
        assertEquals(200, response.getStatus());
        assertEquals("{\"status\":\"available\",\"message\":\"Endpoints are ready\"}",
                     new ByteArrayOutputStream() {{ uncheck(() -> response.render(this)); }}.toString(UTF_8));
    }

    @Test
    public void testClusterReindexingStateSerialization() {
        Stream.of(ClusterReindexing.State.values()).forEach(ClusterReindexing.State::toString);
    }

    @Test
    public void testReindexingSerialization() throws IOException {
        Instant now = Instant.ofEpochMilli(123456);
        ApplicationReindexing applicationReindexing = ApplicationReindexing.empty()
                                                                           .withPending("foo", "bar", 123L).withReady("moo", "baz", now, 1, "reindexing");
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
                         """
                         {
                           "enabled": true,
                           "clusters": {
                             "boo": {
                               "pending": {},
                               "ready": {
                                 "bar": {},
                                 "bax": {
                                   "startedMillis": 123456
                                 },
                                 "baz": {
                                   "startedMillis": 124456,
                                   "endedMillis": 125456,
                                   "state": "failed",
                                   "message": "message",
                                   "progress": 0.1
                                 }
                               }
                             },
                             "foo": {
                               "pending": {
                                 "bar": 123
                               },
                               "ready": {
                                 "bar": {},
                                 "hax": {}
                               }
                             },
                             "moo": {
                               "pending": {},
                               "ready": {
                                 "bax": {
                                   "startedMillis": 123456
                                 },
                                 "baz": {
                                   "readyMillis": 123456,
                                   "speed": 1.0,
                                   "cause": "reindexing",
                                   "startedMillis": 124456,
                                   "endedMillis": 125456,
                                   "state": "failed",
                                   "message": "message",
                                   "progress": 0.1
                                 }
                               }
                             }
                           }
                         }
                         """);
    }

    @Test
    public void service_convergence() {
        String hostAndPort = "localhost:1234";
        URI uri = URI.create("https://" + hostAndPort + "/serviceconvergence/container");

        { // Known service
            HttpResponse response = createResponse(new ServiceResponse(ServiceResponse.Status.ok,
                                                                       3,
                                                                       3,
                                                                       true),
                                                   hostAndPort,
                                                   uri);
            assertResponse("{\n" +
                           "  \"url\": \"" + uri.toString() + "\",\n" +
                           "  \"host\": \"" + hostAndPort + "\",\n" +
                           "  \"wantedGeneration\": 3,\n" +
                           "  \"converged\": true,\n" +
                           "  \"currentGeneration\": 3\n" +
                           "}",
                           200,
                           response);
        }

        { // Missing service
            HttpResponse response = createResponse(new ServiceResponse(ServiceResponse.Status.hostNotFound,
                                                                       3L),
                                                   hostAndPort,
                                                   uri);

            assertResponse("{\n" +
                           "  \"url\": \"" + uri.toString() + "\",\n" +
                           "  \"host\": \"" + hostAndPort + "\",\n" +
                           "  \"wantedGeneration\": 3,\n" +
                           "  \"problem\": \"Host:port (service) no longer part of application, refetch list of services.\"\n" +
                           "}",
                           410,
                           response);
        }
    }

    @Test
    public void service_list_convergence() {
        URI requestUrl = URI.create("https://configserver/serviceconvergence");

        String hostname = "localhost";
        int port = 1234;
        String hostAndPort = hostname + ":" + port;
        URI serviceUrl = URI.create("https://configserver/serviceconvergence/" + hostAndPort);

        {
            HttpServiceListResponse response =
                    new HttpServiceListResponse(new ServiceListResponse(Map.of(createServiceInfo(hostname, port), 3L),
                                                                        3L,
                                                                        3L),
                                                requestUrl);
            assertResponse("{\n" +
                           "  \"services\": [\n" +
                           "    {\n" +
                           "      \"host\": \"" + hostname + "\",\n" +
                           "      \"port\": " + port + ",\n" +
                           "      \"type\": \"container\",\n" +
                           "      \"url\": \"" + serviceUrl.toString() + "\",\n" +
                           "      \"currentGeneration\":" + 3 + "\n" +
                           "    }\n" +
                           "  ],\n" +
                           "  \"url\": \"" + requestUrl.toString() + "\",\n" +
                           "  \"currentGeneration\": 3,\n" +
                           "  \"wantedGeneration\": 3,\n" +
                           "  \"converged\": true\n" +
                           "}",
                           200,
                           response);
        }

        { // Two hosts on different generations
            String hostname2 = "localhost2";
            int port2 = 5678;
            String hostAndPort2 = hostname2 + ":" + port2;
            URI serviceUrl2 = URI.create("https://configserver/serviceconvergence/" + hostAndPort2);

            Map<ServiceInfo, Long> serviceInfos = new HashMap<>();
            serviceInfos.put(createServiceInfo(hostname, port), 4L);
            serviceInfos.put(createServiceInfo(hostname2, port2), 3L);

            HttpServiceListResponse response =
                    new HttpServiceListResponse(new ServiceListResponse(serviceInfos,
                                                                        4L,
                                                                        3L),
                                                requestUrl);
            assertResponse("{\n" +
                           "  \"services\": [\n" +
                           "    {\n" +
                           "      \"host\": \"" + hostname + "\",\n" +
                           "      \"port\": " + port + ",\n" +
                           "      \"type\": \"container\",\n" +
                           "      \"url\": \"" + serviceUrl.toString() + "\",\n" +
                           "      \"currentGeneration\":" + 4 + "\n" +
                           "    },\n" +
                           "    {\n" +
                           "      \"host\": \"" + hostname2 + "\",\n" +
                           "      \"port\": " + port2 + ",\n" +
                           "      \"type\": \"container\",\n" +
                           "      \"url\": \"" + serviceUrl2.toString() + "\",\n" +
                           "      \"currentGeneration\":" + 3 + "\n" +
                           "    }\n" +
                           "  ],\n" +
                           "  \"url\": \"" + requestUrl.toString() + "\",\n" +
                           "  \"currentGeneration\": 3,\n" +
                           "  \"wantedGeneration\": 4,\n" +
                           "  \"converged\": false\n" +
                           "}",
                           200,
                           response);
        }
    }

    @Test
    public void service_convergence_timeout() {
        String hostAndPort = "localhost:1234";
        URI uri = URI.create("https://" + hostAndPort + "/serviceconvergence/container");

        HttpResponse response = createResponse(new ServiceResponse(ServiceResponse.Status.notFound,
                                                                   3L,
                                                                   "some error message"),
                                               hostAndPort,
                                               uri);

        assertResponse("{\n" +
                       "  \"url\": \"" + uri + "\",\n" +
                       "  \"host\": \"" + hostAndPort + "\",\n" +
                       "  \"wantedGeneration\": 3,\n" +
                       "  \"error\": \"some error message\"" +
                       "}",
                       404,
                       response);
    }

    private void assertNotAllowed(Method method) throws IOException {
        String url = "http://myhost:14000/application/v2/tenant/" + mytenantName + "/application/default";
        deleteAndAssertResponse(url, Response.Status.METHOD_NOT_ALLOWED, HttpErrorResponse.ErrorCode.METHOD_NOT_ALLOWED, "{\"error-code\":\"METHOD_NOT_ALLOWED\",\"message\":\"Method '" + method + "' is not supported\"}",
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

    private void deleteAndAssertResponse(ApplicationId applicationId, Zone zone, int expectedStatus, HttpErrorResponse.ErrorCode errorCode, boolean fullAppIdInUrl) throws IOException {
        String expectedResponse = "{\"message\":\"Application '" + applicationId + "' deleted\"}";
        deleteAndAssertResponse(toUrlPath(applicationId, zone, fullAppIdInUrl), expectedStatus, errorCode, expectedResponse, Method.DELETE);
    }

    private void deleteAndAssertResponse(ApplicationId applicationId, Zone zone, int expectedStatus, HttpErrorResponse.ErrorCode errorCode, String expectedResponse) throws IOException {
        deleteAndAssertResponse(toUrlPath(applicationId, zone, true), expectedStatus, errorCode, expectedResponse, Method.DELETE);
    }

    private void deleteAndAssertResponse(String url, int expectedStatus, HttpErrorResponse.ErrorCode errorCode, String expectedResponse, Method method) throws IOException {
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
        ListApplicationsHandler listApplicationsHandler = new ListApplicationsHandler(ListApplicationsHandler.testContext(),
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
        return new ApplicationHandler(ApplicationHandler.testContext(), Zone.defaultZone(), applicationRepository);
    }

    private PrepareParams prepareParams(ApplicationId applicationId) {
        return new PrepareParams.Builder().applicationId(applicationId).build();
    }

    private static void assertResponse(String expectedJson, int status, HttpResponse response) {
        assertResponse((responseBody) -> assertJsonEquals(new String(responseBody
                                                                             .getBytes()), expectedJson), status, response);
    }

    private static void assertResponse(Consumer<String> assertFunc, int status, HttpResponse response) {
        ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        try {
            response.render(responseBody);
            assertFunc.accept(responseBody.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertEquals(status, response.getStatus());
    }

    private ServiceInfo createServiceInfo(String hostname, int port) {
        return new ServiceInfo("container",
                               "container",
                               List.of(new PortInfo(port, List.of("state"))),
                               Map.of(),
                               "configId",
                               hostname);
    }

}
