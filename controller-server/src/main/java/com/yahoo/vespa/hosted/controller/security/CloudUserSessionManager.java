// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.security;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.flags.LongFlag;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.TenantController;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserSessionManager;
import com.yahoo.vespa.hosted.controller.api.role.SecurityContext;
import com.yahoo.vespa.hosted.controller.api.role.TenantRole;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;

import java.time.Instant;

/**
 * @author freva
 */
public class CloudUserSessionManager implements UserSessionManager {

    private final TenantController tenantController;
    private final LongFlag invalidateConsoleSessions;

    public CloudUserSessionManager(Controller controller) {
        this.tenantController = controller.tenants();
        this.invalidateConsoleSessions = PermanentFlags.INVALIDATE_CONSOLE_SESSIONS.bindTo(controller.flagSource());
    }

    @Override
    public boolean shouldExpireSessionFor(SecurityContext context) {
        if (context.issuedAt().isBefore(Instant.ofEpochSecond(invalidateConsoleSessions.value())))
            return true;

        return context.roles().stream()
                .filter(TenantRole.class::isInstance)
                .map(TenantRole.class::cast)
                .map(TenantRole::tenant)
                .distinct()
                .anyMatch(tenantName -> shouldExpireSessionFor(tenantName, context.issuedAt()));
    }

    private boolean shouldExpireSessionFor(TenantName tenantName, Instant contextIssuedAt) {
        return tenantController.get(tenantName)
                .filter(CloudTenant.class::isInstance)
                .map(CloudTenant.class::cast)
                .flatMap(CloudTenant::invalidateUserSessionsBefore)
                .map(contextIssuedAt::isBefore)
                .orElse(false);
    }
}
