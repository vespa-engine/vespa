// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.role;

import com.yahoo.restapi.Path;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * This declares and groups all known REST API paths in the controller.
 *
 * When creating a new API, its paths must be added here and a policy must be declared in {@link Policy}.
 *
 * @author mpolden
 */
public enum PathGroup {

    /** Paths used for system management by operators */
    operator("/controller/v1/{*}",
             "/provision/v2/{*}",
             "/flags/v1/{*}",
             "/os/v1/{*}",
             "/cost/v1/{*}",
             "/zone/v2/{*}",
             "/nodes/v2/{*}",
             "/orchestrator/v1/{*}"),

    /** Paths used when onboarding and creating a new tenants */
    onboardingUser("/application/v4/user"),

    // Tenant parameter is ignored here as context for the role is not defined until after a tenant has been created
    onboardingTenant("/application/v4/tenant/{ignored}"),

    /** Read-only paths used when onboarding tenants */
    onboardingTenantInformation("/athenz/v1/",
                                "/athenz/v1/domains"),


    /** Paths used by tenant/application administrators */
    tenant("/application/v4/",
           "/application/v4/property/",
           "/application/v4/tenant/",
           "/application/v4/tenant-pipeline/",
           "/application/v4/tenant/{tenant}",
           "/application/v4/tenant/{tenant}/application/",
           "/application/v4/tenant/{tenant}/application/{application}",
           "/application/v4/tenant/{tenant}/application/{application}/deploying/{*}",
           "/application/v4/tenant/{tenant}/application/{application}/instance/{*}",
           "/application/v4/tenant/{tenant}/application/{application}/environment/dev/{*}",
           "/application/v4/tenant/{tenant}/application/{application}/environment/perf/{*}",
           "/application/v4/tenant/{tenant}/application/{application}/environment/prod/region/{region}/instance/{instance}/global-rotation/override"),

    /** Paths used for deployments by build service(s) */
    buildService("/application/v4/tenant/{tenant}/application/{application}/jobreport",
                 "/application/v4/tenant/{tenant}/application/{application}/submit",
                 "/application/v4/tenant/{tenant}/application/{application}/promote",
                 "/application/v4/tenant/{tenant}/application/{application}/environment/prod/{*}",
                 "/application/v4/tenant/{tenant}/application/{application}/environment/test/{*}",
                 "/application/v4/tenant/{tenant}/application/{application}/environment/staging/{*}"),

    /** Read-only paths providing information related to deployments */
    deploymentStatus("/badge/v1/{*}",
                     "/deployment/v1/{*}",
                     "/zone/v1/{*}"),

    /** Paths used by some dashboard */
    dashboard("/",
              "/d/{*}",
              "/statuspage/v1/{*}");

    final Set<String> pathSpecs;

    PathGroup(String... pathSpecs) {
        this.pathSpecs = Set.of(pathSpecs);
    }

    /** Returns path if it matches any spec in this group, with match groups set by the match. */
    private Optional<Path> get(String path) {
        Path matcher = new Path(path);
        for (String spec : pathSpecs) // Iterate to be sure the Path's state is that of the match.
            if (matcher.matches(spec)) return Optional.of(matcher);
        return Optional.empty();
    }

    /** All known path groups */
    public static Set<PathGroup> all() {
        return EnumSet.allOf(PathGroup.class);
    }

    /** Returns whether this group matches path in given context */
    public boolean matches(String path, Context context) {
        return get(path).map(p -> {
            boolean match = true;
            String tenant = p.get("tenant");
            if (tenant != null && context.tenant().isPresent()) {
                match = context.tenant().get().value().equals(tenant);
            }
            String application = p.get("application");
            if (application != null && context.application().isPresent()) {
                match &= context.application().get().value().equals(application);
            }
            return match;
        }).orElse(false);
    }

}
