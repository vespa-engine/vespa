// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.role;

import com.yahoo.restapi.Path;

import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * This declares and groups all known REST API paths in the controller.
 *
 * When creating a new API, its paths must be added here and a policy must be declared in {@link Policy}.
 *
 * @author mpolden
 * @author jonmv
 */
enum PathGroup {

    /** Paths exclusive to operators (including read), used for system management. */
    classifiedOperator(Optional.of("/api"),
                       "/configserver/v1/{*}"),

    /** Paths used for system management by operators. */
    operator("/controller/v1/{*}",
             "/flags/v1/{*}",
             "/nodes/v2/{*}",
             "/orchestrator/v1/{*}",
             "/os/v1/{*}",
             "/provision/v2/{*}",
             "/zone/v2/{*}"),

    /** Paths used for creating and reading user resources. */
    user("/application/v4/user",
         "/athenz/v1/{*}"),

    /** Paths used for creating tenants with proper access control. */
    tenant(Matcher.tenant,
           Optional.of("/api"),
           "/application/v4/tenant/{tenant}"),

    /** Paths used for user management on the tenant level. */
    tenantUsers(Matcher.tenant,
                Optional.of("/api"),
                "/user/v1/tenant/{tenant}"),

    /** Paths used by tenant administrators. */
    tenantInfo(Matcher.tenant,
               Optional.of("/api"),
               "/application/v4/tenant/{tenant}/application/",
               "/application/v4/tenant/{tenant}/cost",
               "/application/v4/tenant/{tenant}/cost/{date}"),

    tenantKeys(Matcher.tenant,
               Optional.of("/api"),
               "/application/v4/tenant/{tenant}/key/"),

    applicationKeys(Matcher.tenant,
                    Matcher.application,
                    Optional.of("/api"),
                    "/application/v4/tenant/{tenant}/application/{application}/key/"),

    /** Path for the base application resource. */
    application(Matcher.tenant,
                Matcher.application,
                Optional.of("/api"),
                "/application/v4/tenant/{tenant}/application/{application}",
                "/application/v4/tenant/{tenant}/application/{application}/instance/",
                "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}"),

    /** Paths used for user management on the application level. */
    applicationUsers(Matcher.tenant,
                     Matcher.application,
                     Optional.of("/api"),
                     "/user/v1/tenant/{tenant}/application/{application}"),

    /** Paths used by application administrators. */
    applicationInfo(Matcher.tenant,
                    Matcher.application,
                    Optional.of("/api"),
                    "/application/v4/tenant/{tenant}/application/{application}/package",
                    "/application/v4/tenant/{tenant}/application/{application}/deploying/{*}",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/deploying/{*}",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/job/{*}",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/nodes",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/logs",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/suspended",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/service/{*}",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/global-rotation/{*}",
                    "/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{ignored}/nodes",
                    "/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{ignored}/logs",
                    "/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{ignored}/suspended",
                    "/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{ignored}/service/{*}",
                    "/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{ignored}/global-rotation/{*}",
                    "/application/v4/tenant/{tenant}/application/{application}/metering"),

    // TODO jonmv: remove
    /** Path used to restart development nodes. */
    developmentRestart(Matcher.tenant,
                       Matcher.application,
                       Matcher.instance,
                       Optional.of("/api"),
                       "/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/dev/region/{region}/restart",
                       "/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/perf/region/{region}/restart",
                       "/application/v4/tenant/{tenant}/application/{application}/environment/dev/region/{region}/instance/{instance}/restart",
                       "/application/v4/tenant/{tenant}/application/{application}/environment/perf/region/{region}/instance/{instance}/restart"),

    // TODO jonmv: remove
    /** Path used to restart production nodes. */
    productionRestart(Matcher.tenant,
                      Matcher.application,
                      Optional.of("/api"),
                      "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/prod/region/{region}/restart",
                      "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/test/region/{region}/restart",
                      "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/staging/region/{region}/restart",
                      "/application/v4/tenant/{tenant}/application/{application}/environment/prod/region/{region}/instance/{ignored}/restart",
                      "/application/v4/tenant/{tenant}/application/{application}/environment/test/region/{region}/instance/{ignored}/restart",
                      "/application/v4/tenant/{tenant}/application/{application}/environment/staging/region/{region}/instance/{ignored}/restart"),

    /** Paths used for development deployments. */
    developmentDeployment(Matcher.tenant,
                          Matcher.application,
                          Matcher.instance,
                          Optional.of("/api"),
                          "/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/deploy/{job}",
                          "/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/dev/region/{region}",
                          "/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/dev/region/{region}/deploy",
                          "/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/perf/region/{region}",
                          "/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/perf/region/{region}/deploy",
                          "/application/v4/tenant/{tenant}/application/{application}/environment/dev/region/{region}/instance/{instance}",
                          "/application/v4/tenant/{tenant}/application/{application}/environment/dev/region/{region}/instance/{instance}/deploy",
                          "/application/v4/tenant/{tenant}/application/{application}/environment/perf/region/{region}/instance/{instance}",
                          "/application/v4/tenant/{tenant}/application/{application}/environment/perf/region/{region}/instance/{instance}/deploy"),

    // TODO jonmv: remove
    /** Paths used for production deployments. */
    productionDeployment(Matcher.tenant,
                         Matcher.application,
                         Optional.of("/api"),
                         "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/prod/region/{region}",
                         "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/prod/region/{region}/deploy",
                         "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/test/region/{region}",
                         "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/test/region/{region}/deploy",
                         "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/staging/region/{region}",
                         "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/staging/region/{region}/deploy",
                         "/application/v4/tenant/{tenant}/application/{application}/environment/prod/region/{region}/instance/{ignored}",
                         "/application/v4/tenant/{tenant}/application/{application}/environment/prod/region/{region}/instance/{ignored}/deploy",
                         "/application/v4/tenant/{tenant}/application/{application}/environment/test/region/{region}/instance/{ignored}",
                         "/application/v4/tenant/{tenant}/application/{application}/environment/test/region/{region}/instance/{ignored}/deploy",
                         "/application/v4/tenant/{tenant}/application/{application}/environment/staging/region/{region}/instance/{ignored}",
                         "/application/v4/tenant/{tenant}/application/{application}/environment/staging/region/{region}/instance/{ignored}/deploy"),

    /** Paths used for continuous deployment to production. */
    submission(Matcher.tenant,
               Matcher.application,
               Optional.of("/api"),
               "/application/v4/tenant/{tenant}/application/{application}/submit",
               "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/submit"),

    /** Paths used for other tasks by build services. */ // TODO: This will vanish.
    buildService(Matcher.tenant,
                 Matcher.application,
                 Optional.of("/api"),
                 "/application/v4/tenant/{tenant}/application/{application}/jobreport",
                 "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/jobreport"),

    /** Paths which contain (not very strictly) classified information about customers. */
    classifiedTenantInfo(Optional.of("/api"),
                         "/application/v4/",
                         "/application/v4/tenant/"),

    /** Paths which contain (not very strictly) classified information about, e.g., customers. */
    classifiedInfo("/cost/v1/{*}",
                   "/deployment/v1/{*}",
                   "/",
                   "/d/{*}",
                   "/statuspage/v1/{*}"),

    /** Same as classifiedInfo, but with optional /api prefix */
    classifiedApiInfo(Optional.of("/api"),
                      "/user/v1/user"),

    /** Paths providing public information. */
    publicInfo(Optional.of("/api"),
               "/badge/v1/{*}",
               "/zone/v1/{*}"),

    /** Paths used for deploying system-wide feature flags. */
    systemFlagsDeploy("/system-flags/v1/deploy"),


    /** Paths used for "dry-running" system-wide feature flags. */
    systemFlagsDryrun("/system-flags/v1/dryrun");

    final List<String> pathSpecs;
    final String prefix;
    final List<Matcher> matchers;

    PathGroup(String... pathSpecs) {
        this(List.of(), Optional.empty(), List.of(pathSpecs));
    }

    PathGroup(Optional<String> prefix, String... pathSpecs) {
        this(List.of(), prefix, List.of(pathSpecs));
    }

    PathGroup(Matcher first, String... pathSpecs) {
        this(List.of(first), Optional.empty(), List.of(pathSpecs));
    }

    PathGroup(Matcher first, Optional<String> prefix, String... pathSpecs) {
        this(List.of(first), prefix, List.of(pathSpecs));
    }

    PathGroup(Matcher first, Matcher second, String... pathSpecs) {
        this(List.of(first, second), Optional.empty(), List.of(pathSpecs));
    }

    PathGroup(Matcher first, Matcher second, Optional<String> prefix, String... pathSpecs) {
        this(List.of(first, second), prefix, List.of(pathSpecs));
    }

    PathGroup(Matcher first, Matcher second, Matcher third, Optional<String> prefix, String... pathSpecs) {
        this(List.of(first, second, third), prefix, List.of(pathSpecs));
    }

    /** Creates a new path group, if the given context matchers are each present exactly once in each of the given specs. */
    PathGroup(List<Matcher> matchers, Optional<String> prefix, List<String> pathSpecs) {
        this.matchers = matchers;
        this.prefix = prefix.orElse("");
        this.pathSpecs = pathSpecs;
    }

    /** Returns path if it matches any spec in this group, with match groups set by the match. */
    private Optional<Path> get(URI uri) {
        Path matcher = new Path(uri, prefix);
        for (String spec : pathSpecs) // Iterate to be sure the Path's state is that of the match.
            if (matcher.matches(spec)) return Optional.of(matcher);
        return Optional.empty();
    }

    /** All known path groups */
    static Set<PathGroup> all() {
        return EnumSet.allOf(PathGroup.class);
    }

    static Set<PathGroup> allExcept(PathGroup... pathGroups) {
        return EnumSet.complementOf(EnumSet.copyOf(List.of(pathGroups)));
    }

    /** Returns whether this group matches path in given context */
    boolean matches(URI uri, Context context) {
        return get(uri).map(p -> {
            boolean match = true;
            String tenant = p.get(Matcher.tenant.name);
            if (tenant != null && context.tenant().isPresent()) {
                match = context.tenant().get().value().equals(tenant);
            }
            String application = p.get(Matcher.application.name);
            if (application != null && context.application().isPresent()) {
                match &= context.application().get().value().equals(application);
            }
            String instance = p.get(Matcher.instance.name);
            if (instance != null && context.instance().isPresent()) {
                match &= context.instance().get().value().equals(instance);
            }
            return match;
        }).orElse(false);
    }


    /** Fragments used to match parts of a path to create a context. */
    enum Matcher {

        tenant("{tenant}"),
        application("{application}"),
        instance("{instance}");

        final String pattern;
        final String name;

        Matcher(String pattern) {
            this.pattern = pattern;
            this.name = pattern.substring(1, pattern.length() - 1);
        }

    }

}
