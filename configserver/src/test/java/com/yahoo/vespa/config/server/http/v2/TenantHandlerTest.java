// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import java.io.IOException;
import java.time.Clock;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.tenant.Tenant;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.vespa.config.server.http.BadRequestException;
import com.yahoo.vespa.config.server.http.NotFoundException;

public class TenantHandlerTest extends TenantTest {

    private TenantHandler handler;
    private final TenantName a = TenantName.from("a");

    @Before
    public void setup() {
        handler = new TenantHandler(TenantHandler.testOnlyContext(), tenantRepository);
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

        int sessionId = 1;
        ApplicationId app = ApplicationId.from(a, ApplicationName.from("foo"), InstanceName.defaultName());
        ApplicationHandlerTest.addMockApplication(tenant, app, sessionId, Clock.systemUTC());

        try {
            handler.handleDELETE(HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/" + a, Method.DELETE));
            fail();
        } catch (BadRequestException e) {
            assertThat(e.getMessage(), is("Cannot delete tenant 'a', as it has active applications: [a.foo]"));
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

}
