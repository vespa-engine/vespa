// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.MockProvisioner;
import com.yahoo.vespa.config.server.application.OrchestratorMock;
import com.yahoo.vespa.config.server.http.BadRequestException;
import com.yahoo.vespa.config.server.http.NotFoundException;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.tenant.Tenant;
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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class TenantHandlerTest {

    private static final File testApp = new File("src/test/apps/app");

    private TenantRepository tenantRepository;
    private ApplicationRepository applicationRepository;
    private TenantHandler handler;
    private final TenantName a = TenantName.from("a");

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
                .withProvisioner(new MockProvisioner())
                .withOrchestrator(new OrchestratorMock())
                .withConfigserverConfig(configserverConfig)
                .build();
        handler = new TenantHandler(TenantHandler.testOnlyContext(), applicationRepository);
    }

    @After
    public void closeTenantRepo() {
        tenantRepository.close();
    }

    @Test
    public void testTenantCreate() throws Exception {
        assertNull(tenantRepository.getTenant(a));
        TenantCreateResponse response = putSync(
                HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/a", Method.PUT));
        assertResponseEquals(response, "{\"message\":\"Tenant a created.\"}");
    }

    @Test
    public void testTenantCreateWithAllPossibleCharactersInName() throws Exception {
        TenantName tenantName = TenantName.from("aB-9999_foo");
        assertNull(tenantRepository.getTenant(tenantName));
        TenantCreateResponse response = putSync(
                HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/" + tenantName, Method.PUT));
        assertResponseEquals(response, "{\"message\":\"Tenant " + tenantName + " created.\"}");
    }

    @Test(expected=NotFoundException.class)
    public void testGetNonExisting() {
        handler.handleGET(HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/x", Method.GET));
    }
 
    @Test
    public void testGetAndList() throws Exception {
        tenantRepository.addTenant(a);
        assertResponseEquals((TenantGetResponse) handler.handleGET(
                HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/a", Method.GET)),
                "{\"message\":\"Tenant 'a' exists.\"}");
        assertResponseEquals((ListTenantsResponse) handler.handleGET(
                HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/", Method.GET)),
                "{\"tenants\":[\"default\",\"a\"]}");
    }

    @Test(expected=BadRequestException.class)
    public void testCreateExisting() throws Exception {
        assertNull(tenantRepository.getTenant(a));
        TenantCreateResponse response = putSync(HttpRequest.createTestRequest(
                "http://deploy.example.yahoo.com:80/application/v2/tenant/a", Method.PUT));
        assertResponseEquals(response, "{\"message\":\"Tenant a created.\"}");
        assertEquals(tenantRepository.getTenant(a).getName(), a);
        handler.handlePUT(HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/a", Method.PUT));
    }

    @Test
    public void testDelete() throws IOException {
        putSync(HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/a", Method.PUT));
        assertEquals(tenantRepository.getTenant(a).getName(), a);
        TenantDeleteResponse delResp = (TenantDeleteResponse) handler.handleDELETE(
                HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/a", Method.DELETE));
        assertResponseEquals(delResp, "{\"message\":\"Tenant a deleted.\"}");
        assertNull(tenantRepository.getTenant(a));
    }

    @Test
    public void testDeleteTenantWithActiveApplications() {
        putSync(HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/" + a, Method.PUT));
        Tenant tenant = tenantRepository.getTenant(a);
        assertEquals(a, tenant.getName());

        ApplicationId applicationId = ApplicationId.from(a, ApplicationName.from("foo"), InstanceName.defaultName());
        applicationRepository.deploy(testApp, new PrepareParams.Builder().applicationId(applicationId).build());

        try {
            handler.handleDELETE(HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/" + a, Method.DELETE));
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("Cannot delete tenant 'a', it has active applications: [a.foo]"));
        }
    }

    @Test(expected=NotFoundException.class)
    public void testDeleteNonExisting() {
        handler.handleDELETE(HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/x", Method.DELETE));
    }
    
    @Test(expected=BadRequestException.class)
    public void testIllegalNameSlashes() {
        putSync(HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/a/b", Method.PUT));
    }

    private TenantCreateResponse putSync(HttpRequest testRequest) {
        return (TenantCreateResponse) handler.handlePUT(testRequest);
    }

    private void assertResponseEquals(HttpResponse response, String payload) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.render(baos);
        assertEquals(baos.toString(StandardCharsets.UTF_8), payload);
    }

}
