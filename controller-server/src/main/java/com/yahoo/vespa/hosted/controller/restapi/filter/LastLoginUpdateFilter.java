// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.google.inject.Inject;
import com.yahoo.config.provision.TenantName;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.base.JsonSecurityRequestFilterBase;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.TenantController;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.SecurityContext;
import com.yahoo.vespa.hosted.controller.api.role.TenantRole;
import com.yahoo.vespa.hosted.controller.tenant.LastLoginInfo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.controller.tenant.LastLoginInfo.UserLevel.administrator;
import static com.yahoo.vespa.hosted.controller.tenant.LastLoginInfo.UserLevel.developer;
import static com.yahoo.vespa.hosted.controller.tenant.LastLoginInfo.UserLevel.user;

/**
 * A security filter protects all controller apis.
 *
 * @author freva
 */
public class LastLoginUpdateFilter extends JsonSecurityRequestFilterBase {

    private static final Logger log = Logger.getLogger(LastLoginUpdateFilter.class.getName());

    private final TenantController tenantController;

    @Inject
    public LastLoginUpdateFilter(Controller controller) {
        this.tenantController = controller.tenants();
    }

    @Override
    public Optional<ErrorResponse> filter(DiscFilterRequest request) {
        try {
            SecurityContext context = (SecurityContext) request.getAttribute(SecurityContext.ATTRIBUTE_NAME);
            Map<TenantName, List<LastLoginInfo.UserLevel>> userLevelsByTenant = context.roles().stream()
                    .flatMap(LastLoginUpdateFilter::filterTenantUserLevels)
                    .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

            userLevelsByTenant.forEach((tenant, userLevels) -> tenantController.updateLastLogin(tenant, userLevels, context.issuedAt()));
        } catch (Exception e) {
            log.log(Level.WARNING, "Exception updating last login:", e);
        }
        return Optional.empty();
    }

    public static Stream<Map.Entry<TenantName, LastLoginInfo.UserLevel>> filterTenantUserLevels(Role role) {
        if (!(role instanceof TenantRole))
            return Stream.empty();

        TenantRole tenantRole = (TenantRole) role;
        TenantName name = tenantRole.tenant();
        switch (tenantRole.definition()) {
            case athenzTenantAdmin:
                return Stream.of(Map.entry(name, user), Map.entry(name, developer), Map.entry(name, administrator));
            case reader: return Stream.of(Map.entry(name, user));
            case developer: return Stream.of(Map.entry(name, developer));
            case administrator: return Stream.of(Map.entry(name, administrator));
            default: return Stream.empty();
        }
    }
}
