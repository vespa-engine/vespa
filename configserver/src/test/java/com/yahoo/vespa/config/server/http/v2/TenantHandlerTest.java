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
import com.yahoo.container.jdisc.HttpResponse;
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
    public void setup() throws Exception {
        handler = new TenantHandler(testExecutor(), null, tenants);
    }

    @Test
    public void testTenantCreate() throws Exception {
        assertNull(tenants.getTenant(a));
        TenantCreateResponse response = (TenantCreateResponse) putSync(
                HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/a", Method.PUT));
        assertResponseEquals(response, "{\"message\":\"Tenant a created.\"}");
    }

    @Test
    public void testTenantCreateWithAllPossibleCharactersInName() throws Exception {
        TenantName tenantName = TenantName.from("aB-9999_foo");
        assertNull(tenants.getTenant(tenantName));
        TenantCreateResponse response = (TenantCreateResponse) putSync(
                HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/" + tenantName, Method.PUT));
        assertResponseEquals(response, "{\"message\":\"Tenant " + tenantName + " created.\"}");
    }

    private HttpResponse putSync(HttpRequest testRequest) {
        return handler.handlePUT(testRequest);
    }

    @Test(expected=NotFoundException.class)
    public void testGetNonExisting() throws Exception {
        handler.handleGET(HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/x", Method.GET));
    }
 
    @Test
    public void testGetExisting() throws Exception {
        tenants.writeTenantPath(a);
        TenantGetResponse response = (TenantGetResponse) handler.handleGET(HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/a", Method.GET));
        assertResponseEquals(response, "{\"message\":\"Tenant 'a' exists.\"}");
    }

    @Test(expected=BadRequestException.class)
    public void testCreateExisting() throws Exception {
        assertNull(tenants.getTenant(a));
        TenantCreateResponse response = (TenantCreateResponse) putSync(HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/a", Method.PUT));
        assertResponseEquals(response, "{\"message\":\"Tenant a created.\"}");
        assertEquals(tenants.getTenant(a).getName(), a);
        handler.handlePUT(HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/a", Method.PUT));
    }

    @Test
    public void testDelete() throws IOException, InterruptedException {
        putSync(HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/a", Method.PUT));
        assertEquals(tenants.getTenant(a).getName(), a);
        TenantDeleteResponse delResp = (TenantDeleteResponse) handler.handleDELETE(HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/a", Method.DELETE));
        assertResponseEquals(delResp, "{\"message\":\"Tenant a deleted.\"}");
        assertNull(tenants.getTenant(a));
    }

    @Test
    public void testDeleteTenantWithActiveApplications() throws Exception {
        putSync(HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/" + a, Method.PUT));
        Tenant tenant = tenants.getTenant(a);
        assertEquals(a, tenant.getName());

        int sessionId = 1;
        ApplicationId app = ApplicationId.from(a,
                                               ApplicationName.from("foo"), InstanceName.defaultName());
        ApplicationHandlerTest.addMockApplication(tenant, app, sessionId, Clock.systemUTC());

        try {
            handler.handleDELETE(HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/" + a, Method.DELETE));
            fail();
        } catch (BadRequestException e) {
            assertThat(e.getMessage(), is("Cannot delete tenant 'a', as it has active applications: [tenant 'a', application 'foo', instance 'default']"));
        }
    }

    @Test(expected=NotFoundException.class)
    public void testDeleteNonExisting() {
        handler.handleDELETE(HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/x", Method.DELETE));
    }
    
    @Test(expected=BadRequestException.class)
    public void testIllegalNameSlashes() throws InterruptedException {
        putSync(HttpRequest.createTestRequest("http://deploy.example.yahoo.com:80/application/v2/tenant/a/b", Method.PUT));
    }
    
}
