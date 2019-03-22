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

    onboardingTenant("/application/v4/tenant/{ignored}"),


    /** Paths used by tenant/application administrators */
    tenant("/athenz/v1/",
           "/athenz/v1/domains",
           "/application/v4/",
           "/application/v4/athensDomain/",
           "/application/v4/property/",
           "/application/v4/tenant/",
           "/application/v4/tenant-pipeline/",
           "/application/v4/tenant/{tenant}",
           "/application/v4/tenant/tenant1/application/",
           "/application/v4/tenant/{tenant}/application/{application}",
           "/application/v4/tenant/{tenant}/application/{application}/deploying/{*}",
           "/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/job/{job}/{*}",
           "/application/v4/tenant/{tenant}/application/{application}/environment/dev/{*}",
           "/application/v4/tenant/{tenant}/application/{application}/environment/perf/{*}",
           "/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/global-rotation/override"),

    /**
     * Paths used for deployments by build service(s). Note that context is ignored in these paths as build service
     * roles are not granted in specific contexts.
     */
    buildService("/zone/v1/{*}",
                 "/application/v4/tenant/{ignored}/application/{ignored2}/jobreport",
                 "/application/v4/tenant/{ignored}/application/{ignored2}/submit",
                 "/application/v4/tenant/{ignored}/application/{ignored2}/promote",
                 "/application/v4/tenant/{ignored}/application/{ignored2}/environment/prod/{*}",
                 "/application/v4/tenant/{ignored}/application/{ignored2}/environment/test/{*}",
                 "/application/v4/tenant/{ignored}/application/{ignored2}/environment/staging/{*}"),

    /** Paths providing information about deployment status */
    deployment("/badge/v1/{*}",
               "/deployment/v1/{*}");

    private final Set<String> pathSpecs;

    PathGroup(String... pathSpecs) {
        this.pathSpecs = Set.of(pathSpecs);
    }

    /** Returns path if it matches any spec in this group */
    private Optional<Path> get(String path) {
        return Optional.of(path).map(Path::new).filter(p -> pathSpecs.stream().anyMatch(p::matches));
    }

    /** All known path groups */
    public static Set<PathGroup> all() {
        return EnumSet.allOf(PathGroup.class);
    }

    /** Returns whether this group matches path in given context */
    public boolean matches(String path, Context context) {
        return get(path).filter(p -> {
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
        }).isPresent();
    }

}
