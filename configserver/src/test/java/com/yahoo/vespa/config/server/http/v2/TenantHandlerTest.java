// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequestBuilder;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.restapi.RestApiTestDriver;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.application.OrchestratorMock;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.tenant.TestTenantRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.yahoo.jdisc.http.HttpRequest.Method.DELETE;
import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static com.yahoo.jdisc.http.HttpRequest.Method.PUT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TenantHandlerTest {

    private static final File testApp = new File("src/test/apps/app");

    private TenantRepository tenantRepository;
    private ApplicationRepository applicationRepository;
    private TenantHandler handler;
    private final TenantName a = TenantName.from("a");

    private RestApiTestDriver testDriver;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        ConfigserverConfig configserverConfig = new ConfigserverConfig.Builder()
                .configServerDBDir(temporaryFolder.newFolder().getAbsolutePath())
                .configDefinitionsDir(temporaryFolder.newFolder().getAbsolutePath())
                .fileReferencesDir(temporaryFolder.newFolder().getAbsolutePath())
                .build();
        tenantRepository = new TestTenantRepository.Builder()
                .withConfigserverConfig(configserverConfig)
                .build();
        applicationRepository = new ApplicationRepository.Builder()
                .withTenantRepository(tenantRepository)
                .withOrchestrator(new OrchestratorMock())
                .withConfigserverConfig(configserverConfig)
                .build();
        handler = new TenantHandler(RestApiTestDriver.createHandlerTestContext(), applicationRepository);
        testDriver = RestApiTestDriver.newBuilder(handler).build();
    }

    @After
    public void closeTenantRepo() {
        tenantRepository.close();
    }

    @Test
    public void testTenantCreate() throws Exception {
        assertNull(tenantRepository.getTenant(a));
        assertResponse(PUT, "/application/v2/tenant/a",
                       "{\"message\":\"Tenant a created.\"}");
        assertEquals(a, tenantRepository.getTenant(a).getName());
    }

    @Test
    public void testTenantCreateWithAllPossibleCharactersInName() throws Exception {
        TenantName tenantName = TenantName.from("aB-9999_foo");
        assertNull(tenantRepository.getTenant(tenantName));
        assertResponse(PUT, "/application/v2/tenant/aB-9999_foo",
                       "{\"message\":\"Tenant " + tenantName + " created.\"}");
    }

    @Test
    public void testGetNonExisting() throws IOException {
        assertResponse(GET, "/application/v2/tenant/x",
                       "{\"error-code\":\"NOT_FOUND\",\"message\":\"Tenant 'x' was not found.\"}");
    }

    @Test
    public void testGetAndList() throws Exception {
        tenantRepository.addTenant(a);
        assertResponse(GET, "/application/v2/tenant/a",
                       "{\"message\":\"Tenant 'a' exists.\"}");
        assertResponse(GET, "/application/v2/tenant/",
                       "{\"tenants\":[\"default\",\"a\"]}");
        assertResponse(GET, "/application/v2/tenant",
                       "{\"tenants\":[\"default\",\"a\"]}");
    }

    @Test
    public void testCreateExisting() throws Exception {
        assertNull(tenantRepository.getTenant(a));
        assertResponse(PUT, "/application/v2/tenant/a",
                       "{\"message\":\"Tenant a created.\"}");
        assertEquals(tenantRepository.getTenant(a).getName(), a);
        assertResponse(PUT, "/application/v2/tenant/a",
                       "{\"error-code\":\"BAD_REQUEST\",\"message\":\"There already exists a tenant 'a'\"}");
    }

    @Test
    public void testDelete() throws IOException {
        tenantRepository.addTenant(a);
        assertResponse(DELETE, "/application/v2/tenant/a",
                       "{\"message\":\"Tenant a deleted.\"}");
        assertNull(tenantRepository.getTenant(a));
    }

    @Test
    public void testDeleteTenantWithActiveApplications() throws IOException {
        tenantRepository.addTenant(a);
        ApplicationId applicationId = ApplicationId.from(a, ApplicationName.from("foo"), InstanceName.defaultName());
        applicationRepository.deploy(testApp, new PrepareParams.Builder().applicationId(applicationId).build());

        assertResponse(DELETE, "/application/v2/tenant/a",
                       "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Cannot delete tenant 'a', it has active applications: [a.foo]\"}");
    }

    @Test
    public void testDeleteNonExisting() throws IOException {
        assertResponse(DELETE, "/application/v2/tenant/a",
                       "{\"error-code\":\"NOT_FOUND\",\"message\":\"Tenant 'a' was not found.\"}");
    }

    @Test
    public void testIllegalNameSlashes() throws IOException {
        assertResponse(PUT, "/application/v2/tenant/a/b",
                       "{\"error-code\":\"NOT_FOUND\",\"message\":\"Nothing at '/application/v2/tenant/a/b'\"}");
    }

    private void assertResponse(Method method, String path, String payload) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        testDriver.executeRequest(HttpRequestBuilder.create(method, path).build())
                  .render(baos);
        assertEquals(payload, baos.toString(StandardCharsets.UTF_8));
    }

}
