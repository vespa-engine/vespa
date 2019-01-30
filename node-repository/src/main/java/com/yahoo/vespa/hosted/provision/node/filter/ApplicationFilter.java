// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node.filter;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A node filter which matches a set of applications.
 *
 * @author bratseth
 */
public class ApplicationFilter extends NodeFilter {

    private final Set<ApplicationId> applications;

    /** Creates a node filter which filters using the given host filter */
    private ApplicationFilter(Set<ApplicationId> applications, NodeFilter next) {
        super(next);
        Objects.requireNonNull(applications, "Applications set cannot be null, use an empty set");
        this.applications = applications;
    }

    @Override
    public boolean matches(Node node) {
        if ( ! applications.isEmpty() && ! (node.allocation().isPresent() && applications.contains(node.allocation().get().owner()))) return false;
        return nextMatches(node);
    }

    public static ApplicationFilter from(ApplicationId applicationId, NodeFilter next) {
        return new ApplicationFilter(ImmutableSet.of(applicationId), next);
    }

    public static ApplicationFilter from(Set<ApplicationId> applicationIds, NodeFilter next) {
        return new ApplicationFilter(ImmutableSet.copyOf(applicationIds), next);
    }

    public static ApplicationFilter from(String applicationIds, NodeFilter next) {
        return new ApplicationFilter(StringUtilities.split(applicationIds).stream().map(ApplicationFilter::toApplicationId).collect(Collectors.toSet()), next);
    }

    public static ApplicationId toApplicationId(String applicationIdString) {
        String[] parts = applicationIdString.split("\\.");
        if (parts.length != 3)
            throw new IllegalArgumentException("Application id must be on the form tenant.application.instance, got '" +
                                               applicationIdString + "'");
        return ApplicationId.from(TenantName.from(parts[0]), ApplicationName.from(parts[1]), InstanceName.from(parts[2]));
    }

}
