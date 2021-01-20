package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.SecurityContext;
import com.yahoo.vespa.hosted.controller.restapi.ApplicationRequestToDiscFilterRequestWrapper;
import com.yahoo.vespa.hosted.controller.tenant.LastLoginInfo;
import org.junit.Test;

import java.time.Instant;
import java.util.Set;

import static com.yahoo.vespa.hosted.controller.tenant.LastLoginInfo.UserLevel.administrator;
import static com.yahoo.vespa.hosted.controller.tenant.LastLoginInfo.UserLevel.developer;
import static com.yahoo.vespa.hosted.controller.tenant.LastLoginInfo.UserLevel.user;

import static org.junit.Assert.assertEquals;

public class LastLoginUpdateFilterTest {

    private static final TenantName tenant1 = TenantName.from("tenant1");
    private static final TenantName tenant2 = TenantName.from("tenant2");

    private final ControllerTester tester = new ControllerTester();
    private final LastLoginUpdateFilter filter = new LastLoginUpdateFilter(tester.controller());

    @Test
    public void updateLastLoginTimeTest() {
        tester.createTenant(tenant1.value());
        tester.createTenant(tenant2.value());

        request(123, Role.developer(tenant1), Role.reader(tenant1), Role.athenzTenantAdmin(tenant2));
        assertLastLoginBy(tenant1, 123L, 123L, null);
        assertLastLoginBy(tenant2, 123L, 123L, 123L);

        request(321, Role.administrator(tenant1), Role.reader(tenant1));
        assertLastLoginBy(tenant1, 321L, 123L, 321L);
        assertLastLoginBy(tenant2, 123L, 123L, 123L);
    }

    private void assertLastLoginBy(TenantName tenantName, Long lastUserLoginAt, Long lastDeveloperLoginAt, Long lastAdministratorLoginAt) {
        LastLoginInfo loginInfo = tester.controller().tenants().require(tenantName).lastLoginInfo();
        assertEquals(lastUserLoginAt, loginInfo.get(user).map(Instant::toEpochMilli).orElse(null));
        assertEquals(lastDeveloperLoginAt, loginInfo.get(developer).map(Instant::toEpochMilli).orElse(null));
        assertEquals(lastAdministratorLoginAt, loginInfo.get(administrator).map(Instant::toEpochMilli).orElse(null));
    }

    private void request(long issuedAt, Role... roles) {
        SecurityContext context = new SecurityContext(() -> "bob", Set.of(roles), Instant.ofEpochMilli(issuedAt));
        Request request = new Request("/", new byte[0], Request.Method.GET, context.principal());
        request.getAttributes().put(SecurityContext.ATTRIBUTE_NAME, context);
        filter.filter(new ApplicationRequestToDiscFilterRequestWrapper(request));
    }
}