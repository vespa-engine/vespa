// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.role;

import com.yahoo.restapi.Path;

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
 */
public enum PathGroup {

    /** Paths used for system management by operators. */
    operator("/controller/v1/{*}",
             "/flags/v1/{*}",
             "/nodes/v2/{*}",
             "/orchestrator/v1/{*}",
             "/os/v1/{*}",
             "/provision/v2/{*}",
             "/zone/v2/{*}"),

    /** Paths used for user management. */
    userManagement("/user/v1/{*}"), // TODO probably add tenant and application levels.

    /** Paths used for creating user tenants. */
    onboardingUser("/application/v4/user"),

    /** Paths used for creating tenants with proper access control. */
    onboarding("/application/v4/tenant/{ignored}"),

    /** Paths used by tenant administrators. */
    tenant(Matcher.tenant,
           "/application/v4/tenant/{tenant}",
           "/application/v4/tenant/{tenant}/application/"),

    /** Paths used by application administrators. */
    application(Matcher.tenant,
                Matcher.application,
                "/application/v4/tenant/{tenant}/application/{application}",
                "/application/v4/tenant/{tenant}/application/{application}/deploying/{*}",
                "/application/v4/tenant/{tenant}/application/{application}/instance/{*}",
                "/application/v4/tenant/{tenant}/application/{application}/environment/prod/region/{region}/instance/{instance}/global-rotation/override"),// TODO add restart path?

    /** Paths used for direct deployment to development zones. */
    development(Matcher.tenant,
                Matcher.application,
                "/application/v4/tenant/{tenant}/application/{application}/environment/dev/{*}",
                "/application/v4/tenant/{tenant}/application/{application}/environment/perf/{*}"),

    /** Paths used for direct deployment to production zones. */
    deployment(Matcher.tenant,
               Matcher.application,
               "/application/v4/tenant/{tenant}/application/{application}/environment/prod/{*}",
               "/application/v4/tenant/{tenant}/application/{application}/environment/test/{*}",
               "/application/v4/tenant/{tenant}/application/{application}/environment/staging/{*}"),

    /** Paths used for continuous deployment to production. */
    submission(Matcher.tenant,
                 Matcher.application,
                 "/application/v4/tenant/{tenant}/application/{application}/submit"),

    /** Paths used for other tasks by build services. */ // TODO: This will vanish.
    buildService(Matcher.tenant,
                 Matcher.application,
                 "/application/v4/tenant/{tenant}/application/{application}/jobreport",
                 "/application/v4/tenant/{tenant}/application/{application}/promote"),

    /** Paths which contain (not very strictly) classified information about, e.g., customers. */
    classifiedInfo("/athenz/v1/{*}",
                   "/cost/v1/{*}",
                   "/deployment/v1/{*}",
                   "/application/v4/",
                   "/application/v4/tenant/",
                   "/",
                   "/d/{*}",
                   "/statuspage/v1/{*}"
    ),

    /** Paths providing public information. */
    publicInfo("/badge/v1/{*}",
               "/zone/v1/{*}");

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

    /** Creates a new path group, if the given context matchers are each present exactly once in each of the given specs. */
    PathGroup(List<Matcher> matchers, List<String> pathSpecs) {
        this.matchers = matchers;
        this.pathSpecs = pathSpecs;
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


    /** Fragments used to match parts of a path to create a context. */
    enum Matcher {

        tenant("{tenant}"),
        application("{application}");

        final String pattern;

        Matcher(String pattern) { this.pattern = pattern; }

    }

}
