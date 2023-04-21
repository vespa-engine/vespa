// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    classifiedOperator("/application/v4/notifications",
                       "/configserver/v1/{*}",
                       "/deployment/v1/{*}"),

    /** Paths used for system management by operators. */
    operator("/cores/v1/{*}",
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
    user("/application/v4/user",
         "/athenz/v1/{*}"),

    /** Paths used for creating tenants with proper access control. */
    tenant(Matcher.tenant,
           "/application/v4/tenant/{tenant}"),

    /** Paths used for user management on the tenant level. */
    tenantUsers(Matcher.tenant,
                "/user/v1/tenant/{tenant}"),

    /** Paths used by tenant administrators. */
    tenantInfo(Matcher.tenant,
               "/application/v4/tenant/{tenant}/application/",
               "/application/v4/tenant/{tenant}/info/",
               "/application/v4/tenant/{tenant}/info/profile",
               "/application/v4/tenant/{tenant}/info/billing",
               "/application/v4/tenant/{tenant}/info/contacts",
               "/application/v4/tenant/{tenant}/info/resend-mail-verification",
               "/application/v4/tenant/{tenant}/notifications",
               "/routing/v1/status/tenant/{tenant}/{*}"),

    tenantKeys(Matcher.tenant,
               "/application/v4/tenant/{tenant}/key/"),

    tenantArchiveAccess(Matcher.tenant,
                       "/application/v4/tenant/{tenant}/archive-access",
                       "/application/v4/tenant/{tenant}/archive-access/aws",
                       "/application/v4/tenant/{tenant}/archive-access/gcp"),


    billingToken(Matcher.tenant,
                 "/billing/v1/tenant/{tenant}/token"),

    billingInstrument(Matcher.tenant,
                      "/billing/v1/tenant/{tenant}/instrument/{*}"),

    billingPlan(Matcher.tenant,
            "/billing/v1/tenant/{tenant}/plan/{*}"),

    billingCollection(Matcher.tenant,
            "/billing/v1/tenant/{tenant}/collection/{*}"),

    billingList(Matcher.tenant,
                "/billing/v1/tenant/{tenant}/billing/{*}"),

    billing(Matcher.tenant,
            "/billing/v2/tenant/{tenant}/{*}"),

    accountant("/billing/v2/accountant/{*}"),

    userSearch("/user/v1/find"),

    applicationKeys(Matcher.tenant,
                    Matcher.application,
                    "/application/v4/tenant/{tenant}/application/{application}/key/"),

    /** Path for the base application resource. */
    application(Matcher.tenant,
                Matcher.application,
                "/application/v4/tenant/{tenant}/application/{application}",
                "/application/v4/tenant/{tenant}/application/{application}/instance/",
                "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}"),

    /** Paths used for user management on the application level. */
    applicationUsers(Matcher.tenant,
                     Matcher.application,
                     "/user/v1/tenant/{tenant}/application/{application}"),

    /** Paths used by application administrators. */
    applicationInfo(Matcher.tenant,
                    Matcher.application,
                    "/application/v4/tenant/{tenant}/application/{application}/submit/{build}",
                    "/application/v4/tenant/{tenant}/application/{application}/package",
                    "/application/v4/tenant/{tenant}/application/{application}/diff/{number}",
                    "/application/v4/tenant/{tenant}/application/{application}/compile-version",
                    "/application/v4/tenant/{tenant}/application/{application}/deployment",
                    "/application/v4/tenant/{tenant}/application/{application}/deploying/{*}",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/deploying/{*}",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/job/{*}",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/nodes",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/clusters",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/content/{*}",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/logs",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/orchestrator",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/private-services",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/suspended",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/service/{*}",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/access/support",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/global-rotation/{*}",
                    "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/scaling",
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
                       "/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/dev/region/{region}/restart",
                       "/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/perf/region/{region}/restart",
                       "/application/v4/tenant/{tenant}/application/{application}/environment/dev/region/{region}/instance/{instance}/restart",
                       "/application/v4/tenant/{tenant}/application/{application}/environment/perf/region/{region}/instance/{instance}/restart"),

    // TODO jonmv: remove
    /** Path used to restart production nodes. */
    productionRestart(Matcher.tenant,
                      Matcher.application,
                      "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/prod/region/{region}/restart",
                      "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/test/region/{region}/restart",
                      "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/staging/region/{region}/restart",
                      "/application/v4/tenant/{tenant}/application/{application}/environment/prod/region/{region}/instance/{ignored}/restart",
                      "/application/v4/tenant/{tenant}/application/{application}/environment/test/region/{region}/instance/{ignored}/restart",
                      "/application/v4/tenant/{tenant}/application/{application}/environment/staging/region/{region}/instance/{ignored}/restart"),

    /** Path used to manipulate reindexing status. */
    reindexing(Matcher.tenant,
               Matcher.application,
               "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/reindex",
               "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/reindexing"),

    serviceDump(Matcher.tenant,
                Matcher.application,
                "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/node/{node}/service-dump"),

    dropDocuments(Matcher.tenant,
                  Matcher.application,
                  "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/environment/{environment}/region/{region}/drop-documents"),

    /** Paths used for development deployments. */
    developmentDeployment(Matcher.tenant,
                          Matcher.application,
                          Matcher.instance,
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
               "/application/v4/tenant/{tenant}/application/{application}/submit",
               "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/submit"),

    /** Paths used for other tasks by build services. */ // TODO: This will vanish.
    buildService(Matcher.tenant,
                 Matcher.application,
                 "/application/v4/tenant/{tenant}/application/{application}/jobreport",
                 "/application/v4/tenant/{tenant}/application/{application}/instance/{ignored}/jobreport"),

    /** Paths which contain (not very strictly) classified information about customers. */
    classifiedTenantInfo("/application/v4/",
                         "/application/v4/tenant/"),

    /** Paths providing public information. */
    publicInfo("/user/v1/user",     // Information about who you are.
               "/badge/v1/{*}",     // Badges for deployment jobs.
               "/zone/v1/{*}",      // Lists environment and regions.
               "/cli/v1/{*}",       // Public information for Vespa CLI.
               "/.well-known/{*}"),

    /** Paths used for deploying system-wide feature flags. */
    systemFlagsDeploy("/system-flags/v1/deploy"),


    /** Paths used for "dry-running" system-wide feature flags. */
    systemFlagsDryrun("/system-flags/v1/dryrun"),

    /** Paths used for receiving payment callbacks */
    paymentProcessor("/payment/notification"),

    /** Paths used for invoice management */
    hostedAccountant("/billing/v1/invoice/{*}",
                     "/billing/v1/billing",
                     "/billing/v1/plans"),

    /** Path used for listing endpoint certificate request and re-requesting endpoint certificates */
    endpointCertificates("/endpointcertificates/"),

    /** Path used for secret store management */
    secretStore(Matcher.tenant, "/application/v4/tenant/{tenant}/secret-store/{*}"),

    /** Paths used to proxy Horizon metric requests */
    horizonProxy("/horizon/v1/{*}"),

    /** Paths used to list and request access to tenant resources */
    accessRequests(Matcher.tenant, "/application/v4/tenant/{tenant}/access/request/operator"),

    /** Paths used to approve requests to access tenant resources */
    accessRequestApproval(Matcher.tenant, "/application/v4/tenant/{tenant}/access/approve/operator",
            "/application/v4/tenant/{tenant}/access/managed/operator"),

    /** Path used for email verification */
    emailVerification("/user/v1/email/verify");

    final List<String> pathSpecs;
    final List<Matcher> matchers;

    PathGroup(String... pathSpecs) {
        this(List.of(), List.of(pathSpecs));
    }

    PathGroup(Matcher first, String... pathSpecs) {
        this(List.of(first), List.of(pathSpecs));
    }

    PathGroup(Matcher first, Matcher second, String... pathSpecs) {
        this(List.of(first, second), List.of(pathSpecs));
    }

    PathGroup(Matcher first, Matcher second, Matcher third, String... pathSpecs) {
        this(List.of(first, second, third), List.of(pathSpecs));
    }

    /** Creates a new path group, if the given context matchers are each present exactly once in each of the given specs. */
    PathGroup(List<Matcher> matchers, List<String> pathSpecs) {
        this.matchers = matchers;
        this.pathSpecs = pathSpecs;
    }

    /** Returns path if it matches any spec in this group, with match groups set by the match. */
    private Optional<Path> get(URI uri) {
        Path matcher = new Path(uri);
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

    static Set<PathGroup> operatorRestrictedPaths() {
        var paths = billingPathsNoToken();
        paths.add(PathGroup.billingToken);
        paths.add(accessRequestApproval);
        return paths;
    }

    static Set<PathGroup> billingPathsNoToken() {
        return EnumSet.of(
                PathGroup.billingCollection,
                PathGroup.billingInstrument,
                PathGroup.billingList,
                PathGroup.billingPlan,
                PathGroup.billing,
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

}
