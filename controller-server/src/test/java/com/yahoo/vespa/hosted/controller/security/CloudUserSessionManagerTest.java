// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.security;

import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.LockedTenant;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.SecurityContext;
import com.yahoo.vespa.hosted.controller.api.role.SimplePrincipal;
import com.yahoo.vespa.hosted.controller.api.role.TenantRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author freva
 */
class CloudUserSessionManagerTest {

    private final ControllerTester tester = new ControllerTester(SystemName.Public);
    private final CloudUserSessionManager userSessionManager = new CloudUserSessionManager(tester.controller());

    @Test
    void test() {
        createTenant("tenant1", null);
        createTenant("tenant2", 1234);
        createTenant("tenant3", 1543);
        createTenant("tenant4", 2313);

        assertShouldExpire(false, 123);
        assertShouldExpire(false, 123, "tenant1");
        assertShouldExpire(true, 123, "tenant2");
        assertShouldExpire(false, 2123, "tenant2");
        assertShouldExpire(true, 123, "tenant1", "tenant2");

        ((InMemoryFlagSource) tester.controller().flagSource()).withLongFlag(PermanentFlags.INVALIDATE_CONSOLE_SESSIONS.id(), 150);
        assertShouldExpire(true, 123);
        assertShouldExpire(true, 123, "tenant1");
    }

    private void assertShouldExpire(boolean expected, long issuedAtSeconds, String... tenantNames) {
        Set<Role> roles = Stream.of(tenantNames).map(name -> TenantRole.developer(TenantName.from(name))).collect(Collectors.toSet());
        SecurityContext context = new SecurityContext(new SimplePrincipal("dev"), roles, Instant.ofEpochSecond(issuedAtSeconds));
        assertEquals(expected, userSessionManager.shouldExpireSessionFor(context));
    }

    private void createTenant(String tenantName, Integer invalidateAfterSeconds) {
        tester.createTenant(tenantName);
        Optional.ofNullable(invalidateAfterSeconds)
                .map(Instant::ofEpochSecond)
                .ifPresent(instant ->
                        tester.controller().tenants().lockOrThrow(TenantName.from(tenantName), LockedTenant.Cloud.class, tenant ->
                                tester.controller().tenants().store(tenant.withInvalidateUserSessionsBefore(instant))));
    }
}
