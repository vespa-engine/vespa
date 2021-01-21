// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.MockProvisioner;
import com.yahoo.vespa.config.server.application.OrchestratorMock;
import com.yahoo.vespa.config.server.http.HttpListConfigsHandler.ListConfigsResponse;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.tenant.TestTenantRepository;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.yahoo.container.jdisc.HttpRequest.createTestRequest;
import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static com.yahoo.jdisc.http.HttpResponse.Status.BAD_REQUEST;
import static com.yahoo.jdisc.http.HttpResponse.Status.NOT_FOUND;
import static com.yahoo.vespa.config.server.http.SessionHandlerTest.getRenderedString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 * @author hmusum
 */
public class HttpListConfigsHandlerTest {

    private static final TenantName tenant = TenantName.from("default");
    private static final String baseUri = "http://foo.com:8080/config/v1/";
    private final static File testApp = new File("src/test/resources/deploy/validapp");
    private static final ApplicationId applicationId = ApplicationId.defaultId();

    private HttpListConfigsHandler handler;
    private HttpListNamedConfigsHandler namedHandler;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() throws IOException {
        ConfigserverConfig configserverConfig = new ConfigserverConfig.Builder()
                .configServerDBDir(temporaryFolder.newFolder().getAbsolutePath())
                .configDefinitionsDir(temporaryFolder.newFolder().getAbsolutePath())
                .fileReferencesDir(temporaryFolder.newFolder().getAbsolutePath())
                .build();
        TenantRepository tenantRepository = new TestTenantRepository.Builder()
                .withConfigserverConfig(configserverConfig)
                .build();
        tenantRepository.addTenant(tenant);
        ApplicationRepository applicationRepository = new ApplicationRepository.Builder()
                .withTenantRepository(tenantRepository)
                .withProvisioner(new MockProvisioner())
                .withOrchestrator(new OrchestratorMock())
                .withConfigserverConfig(configserverConfig)
                .build();
        applicationRepository.deploy(testApp, prepareParams());

        HttpListConfigsHandler.Context ctx = HttpListConfigsHandler.testOnlyContext();
        handler = new HttpListConfigsHandler(ctx, tenantRepository);
        namedHandler = new HttpListNamedConfigsHandler(ctx, tenantRepository);
    }
    
    @Test
    public void require_that_handler_can_be_created() throws IOException {
        HttpResponse response = handler.handle(HttpRequest.createTestRequest("/config/v1/", GET));
        String renderedString = getRenderedString(response);
        assertTrue(renderedString, renderedString.startsWith("{\"children\":["));
        assertTrue(renderedString, renderedString.contains(",\"configs\":["));
    }
    
    @Test
    public void require_that_named_handler_can_be_created() throws IOException {
        HttpRequest req = createTestRequest(baseUri + "cloud.config.sentinel/hosts/localhost/sentinel", GET);
        req.getJDiscRequest().parameters().put("http.path", List.of("cloud.config.sentinel"));
        HttpResponse response = namedHandler.handle(req);
        String renderedString = getRenderedString(response);
        assertTrue(renderedString, renderedString.startsWith("{\"children\":["));
        assertTrue(renderedString, renderedString.contains(",\"configs\":["));
    }
    
    @Test
    public void require_child_listings_correct() {
        Set<ConfigKey<?>> keys = new LinkedHashSet<>() {{
            add(new ConfigKey<>("name1", "id/1", "ns1"));
            add(new ConfigKey<>("name1", "id/1", "ns1"));
            add(new ConfigKey<>("name1", "id/2", "ns1"));
            add(new ConfigKey<>("name1", "", "ns1"));
            add(new ConfigKey<>("name1", "id/1/1", "ns1"));
            add(new ConfigKey<>("name1", "id2", "ns1"));
            add(new ConfigKey<>("name1", "id/2/1", "ns1"));
            add(new ConfigKey<>("name1", "id/2/1/5/6", "ns1"));
        }};
        Set<ConfigKey<?>> keysThatHaveChild = HttpListConfigsHandler.ListConfigsResponse.keysThatHaveAChildWithSameName(keys, keys);
        assertEquals(keysThatHaveChild.size(), 3);
    }
 
    @Test
    public void require_url_building_and_mimetype_correct() {
        HttpListConfigsHandler.ListConfigsResponse resp = new ListConfigsResponse(new HashSet<>(), null, "http://foo.com/config/v1/", true);
        assertEquals(resp.toUrl(new ConfigKey<>("myconfig", "my/id", "mynamespace"), true), "http://foo.com/config/v1/mynamespace.myconfig/my/id");
        assertEquals(resp.toUrl(new ConfigKey<>("myconfig", "my/id", "mynamespace"), false), "http://foo.com/config/v1/mynamespace.myconfig/my/id/");
        assertEquals(resp.getContentType(), "application/json");
    }
 
    @Test
    public void require_error_on_bad_request() throws IOException {
        HttpRequest req = createTestRequest(baseUri + "foobar/hosts/localhost/sentinel/", GET);
        HttpResponse resp = namedHandler.handle(req);
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(resp, BAD_REQUEST, HttpErrorResponse.errorCodes.BAD_REQUEST, "Illegal config, must be of form namespace.name.");
        req = createTestRequest(baseUri + "foo.barNOPE/conf/id/", GET);
        resp = namedHandler.handle(req);
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(resp, NOT_FOUND, HttpErrorResponse.errorCodes.NOT_FOUND, "No such config: foo.barNOPE");
        req = createTestRequest(baseUri + "cloud.config.sentinel/conf/id/NOPE/", GET);
        resp = namedHandler.handle(req);
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(resp, NOT_FOUND, HttpErrorResponse.errorCodes.NOT_FOUND, "No such config id: conf/id/NOPE");
    }

    private PrepareParams prepareParams() {
        return new PrepareParams.Builder().applicationId(applicationId).build();
    }

}
