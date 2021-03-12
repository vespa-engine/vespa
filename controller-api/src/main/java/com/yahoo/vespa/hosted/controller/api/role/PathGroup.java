// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.role;

import com.yahoo.restapi.Path;

import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
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
    classifiedOperator(PathPrefix.api,
                       "/configserver/v1/{*}",
                       "/deployment/v1/{*}"),

    /** Paths used for system management by operators. */
    operator(PathPrefix.none,
             "/controller/v1/{*}",
             "/flags/v1/{*}",
             "/loadbalancers/v1/{*}",
             "/nodes/v2/{*}",
             "/orchestrator/v1/{*}",
             "/os/v1/{*}",
             "/provision/v2/{*}",
             "/zone/v2/{*}",
             "/routing/v1/",
             "/routing/v1/status/environment/{*}",
             "/routing/v1/inactive/environment/{*}",
             "/state/v1/{*}",
             "/changemanagement/v1/{*}"),

    /** Paths used for creating and reading user resources. */
    user(PathPrefix.api,
         "/application/v4/user",
         "/athenz/v1/{*}"),

    /** Paths used for creating tenants with proper access control. */
    tenant(Matcher.tenant,
           PathPrefix.api,
           "/application/v4/tenant/{tenant}"),

    /** Paths used for user management on the tenant level. */
    tenantUsers(Matcher.tenant,
                PathPrefix.api,
                "/user/v1/tenant/{tenant}"),

    /** Paths used by tenant administrators. */
    tenantInfo(Matcher.tenant,
               PathPrefix.api,
               "/application/v4/tenant/{tenant}/application/",
               "/application/v4/tenant/{tenant}/info/",
               "/routing/v1/status/tenant/{tenant}/{*}"),

    tenantKeys(Matcher.tenant,
               PathPrefix.api,
               "/application/v4/tenant/{tenant}/key/"),


    billingToken(Matcher.tenant,
                 PathPrefix.api,
                 "/billing/v1/tenant/{tenant}/token"),

    billingInstrument(Matcher.tenant,
                      PathPrefix.api,
                      "/billing/v1/tenant/{tenant}/instrument/{*}"),

    billingPlan(Matcher.tenant,
            PathPrefix.api,
            "/billing/v1/tenant/{tenant}/plan/{*}"),

    billingCollection(Matcher.tenant,
            PathPrefix.api,
            "/billing/v1/tenant/{tenant}/collection/{*}"),

    billingList(Matcher.tenant,
                PathPrefix.api,
                "/billing/v1/tenant/{tenant}/billing/{*}"),

    applicationKeys(Matcher.tenant,
                    Matcher.application,
                    PathPrefix.api,
                    "/application/v4/tenant/{tenant}/application/{application}/key/"),

    /** Path for the base application resource. */
    application(Matcher.tenant,
                Matcher.application,
                PathPrefix.api,
                "/application/v4/tenant/{tenant}/application/{application}",
                "/application/v4/tenant/{tenant}/application/{application}/instance/",
                "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}"),

    /** Paths used for user management on the application level. */
    applicationUsers(Matcher.tenant,
                     Matcher.application,
                     PathPrefix.api,
                     "/user/v1/tenant/{tenant}/application/{application}"),

    /** Paths used by application administrators. */
    applicationInfo(Matcher.tenant,
                    Matcher.application,
                    PathPrefix.api,
                    "/application/v4/tenant/{tenant}/application/{application}/package",
                    "/application/v4/tenant/{tenant}/application/{application}/compile-version",
                    "/application/v4/tenant/{tenant}/application/{application}/deployment",
                    "/application/v4/tenant/{tenant}/application/{application}/deploying/{*}",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/deploying/{*}",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/job/{*}",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/nodes",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/clusters",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/content/{*}",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/logs",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/suspended",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/service/{*}",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/global-rotation/{*}",
                    "/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{ignored}/nodes",
                    "/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{ignored}/clusters",
                    "/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{ignored}/logs",
                    "/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{ignored}/metrics",
                    "/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{ignored}/suspended",
                    "/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{ignored}/service/{*}",
                    "/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{ignored}/global-rotation/{*}",
                    "/application/v4/tenant/{tenant}/application/{application}/metering",
                    "/routing/v1/inactive/tenant/{tenant}/application/{application}/instance/{ignored}/environment/prod/region/{region}"),

    // TODO jonmv: remove
    /** Path used to restart development nodes. */
    developmentRestart(Matcher.tenant,
                       Matcher.application,
                       Matcher.instance,
                       PathPrefix.api,
                       "/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/dev/region/{region}/restart",
                       "/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/perf/region/{region}/restart",
                       "/application/v4/tenant/{tenant}/application/{application}/environment/dev/region/{region}/instance/{instance}/restart",
                       "/application/v4/tenant/{tenant}/application/{application}/environment/perf/region/{region}/instance/{instance}/restart"),

    // TODO jonmv: remove
    /** Path used to restart production nodes. */
    productionRestart(Matcher.tenant,
                      Matcher.application,
                      PathPrefix.api,
                      "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/prod/region/{region}/restart",
                      "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/test/region/{region}/restart",
                      "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/staging/region/{region}/restart",
                      "/application/v4/tenant/{tenant}/application/{application}/environment/prod/region/{region}/instance/{ignored}/restart",
                      "/application/v4/tenant/{tenant}/application/{application}/environment/test/region/{region}/instance/{ignored}/restart",
                      "/application/v4/tenant/{tenant}/application/{application}/environment/staging/region/{region}/instance/{ignored}/restart"),

    /** Path used to manipulate reindexing status. */
    reindexing(Matcher.tenant,
               Matcher.application,
               PathPrefix.api,
               "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/reindex",
               "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/reindexing"),

    /** Paths used for development deployments. */
    developmentDeployment(Matcher.tenant,
                          Matcher.application,
                          Matcher.instance,
                          PathPrefix.api,
                          "/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/deploy/{job}",
                          "/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/dev/region/{region}",
                          "/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/dev/region/{region}/deploy",
                          "/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/dev/region/{region}/suspend",
                          "/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/perf/region/{region}",
                          "/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/perf/region/{region}/deploy",
                          "/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/perf/region/{region}/suspend",
                          "/application/v4/tenant/{tenant}/application/{application}/environment/dev/region/{region}/instance/{instance}",
                          "/application/v4/tenant/{tenant}/application/{application}/environment/dev/region/{region}/instance/{instance}/deploy",
                          "/application/v4/tenant/{tenant}/application/{application}/environment/perf/region/{region}/instance/{instance}",
                          "/application/v4/tenant/{tenant}/application/{application}/environment/perf/region/{region}/instance/{instance}/deploy"),

    // TODO jonmv: remove
    /** Paths used for production deployments. */
    productionDeployment(Matcher.tenant,
                         Matcher.application,
                         PathPrefix.api,
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
               PathPrefix.api,
               "/application/v4/tenant/{tenant}/application/{application}/submit",
               "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/submit"),

    /** Paths used for other tasks by build services. */ // TODO: This will vanish.
    buildService(Matcher.tenant,
                 Matcher.application,
                 PathPrefix.api,
                 "/application/v4/tenant/{tenant}/application/{application}/jobreport",
                 "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/jobreport"),

    /** Paths which contain (not very strictly) classified information about customers. */
    classifiedTenantInfo(PathPrefix.api,
                         "/application/v4/",
                         "/application/v4/tenant/"),

    /** Paths which contain (not very strictly) classified information about, e.g., customers. */
    classifiedInfo(PathPrefix.none,
                   "/",
                   "/d/{*}"),

    /** Paths providing public information. */
    publicInfo(PathPrefix.api,
               "/user/v1/user",     // Information about who you are.
               "/badge/v1/{*}",     // Badges for deployment jobs.
               "/zone/v1/{*}"),     // Lists environment and regions.

    /** Paths used for deploying system-wide feature flags. */
    systemFlagsDeploy(PathPrefix.none, "/system-flags/v1/deploy"),


    /** Paths used for "dry-running" system-wide feature flags. */
    systemFlagsDryrun(PathPrefix.none, "/system-flags/v1/dryrun"),

    /** Paths used for receiving payment callbacks */
    paymentProcessor(PathPrefix.none, "/payment/notification"),

    /** Paths used for invoice management */
    hostedAccountant(PathPrefix.api,
            "/billing/v1/invoice/{*}",
            "/billing/v1/billing"),

    /** Path used for listing endpoint certificate request info */
    endpointCertificateRequestInfo(PathPrefix.none, "/certificateRequests/"),

    /** Path used for secret store management */
    secretStore(Matcher.tenant, PathPrefix.api, "/application/v4/tenant/{tenant}/secret-store/{*}");

    final List<String> pathSpecs;
    final PathPrefix prefix;
    final List<Matcher> matchers;

    PathGroup(PathPrefix prefix, String... pathSpecs) {
        this(List.of(), prefix, List.of(pathSpecs));
    }

    PathGroup(Matcher first, PathPrefix prefix, String... pathSpecs) {
        this(List.of(first), prefix, List.of(pathSpecs));
    }

    PathGroup(Matcher first, Matcher second, PathPrefix prefix, String... pathSpecs) {
        this(List.of(first, second), prefix, List.of(pathSpecs));
    }

    PathGroup(Matcher first, Matcher second, Matcher third, PathPrefix prefix, String... pathSpecs) {
        this(List.of(first, second, third), prefix, List.of(pathSpecs));
    }

    /** Creates a new path group, if the given context matchers are each present exactly once in each of the given specs. */
    PathGroup(List<Matcher> matchers, PathPrefix prefix, List<String> pathSpecs) {
        this.matchers = matchers;
        this.prefix = prefix;
        this.pathSpecs = pathSpecs;
    }

    /** Returns path if it matches any spec in this group, with match groups set by the match. */
    private Optional<Path> get(URI uri) {
        Path matcher = new Path(uri, prefix.prefix);
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

    static Set<PathGroup> allExcept(Set<PathGroup> pathGroups) {
        return EnumSet.complementOf(EnumSet.copyOf(pathGroups));
    }

    static Set<PathGroup> billingPaths() {
        var paths = billingPathsNoToken();
        paths.add(PathGroup.billingToken);
        return paths;
    }

    static Set<PathGroup> billingPathsNoToken() {
        return EnumSet.of(
                PathGroup.billingCollection,
                PathGroup.billingInstrument,
                PathGroup.billingList,
                PathGroup.billingPlan,
                PathGroup.hostedAccountant
        );
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

    /**
     * The valid prefixes of paths in a {@link PathGroup}. Provides flexibility in cases where paths are made available
     * under a non-root path.
     */
    enum PathPrefix {

        none(""),
        api("/api");

        private final String prefix;

        PathPrefix(String prefix) {
            this.prefix = Objects.requireNonNull(prefix);
        }

    }

}
