// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.google.common.base.Strings;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents a DNS alias for a load balancer.
 *
 * @author mortent
 */
public class LoadBalancerAlias {

    private static final String ignoredEndpointPart = "default";
    private final ApplicationId owner;
    private final String id;
    private final HostName alias;
    private final HostName canonicalName;

    public LoadBalancerAlias(ApplicationId owner, String id, HostName alias, HostName canonicalName) {
        this.owner = Objects.requireNonNull(owner, "owner must be non-null");
        this.id = Objects.requireNonNull(id, "id must be non-null");
        this.alias = Objects.requireNonNull(alias, "alias must be non-null");
        this.canonicalName = Objects.requireNonNull(canonicalName, "canonicalName must be non-null");
    }

    /** The application owning this */
    public ApplicationId owner() {
        return owner;
    }

    /** The ID of the DNS record represented by this */
    public String id() {
        return id;
    }

    /** This alias (lhs of the CNAME record) */
    public HostName alias() {
        return alias;
    }

    /** The canonical name of this (rhs of the CNAME record) */
    public HostName canonicalName() {
        return canonicalName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LoadBalancerAlias that = (LoadBalancerAlias) o;
        return Objects.equals(owner, that.owner) &&
               Objects.equals(id, that.id) &&
               Objects.equals(alias, that.alias) &&
               Objects.equals(canonicalName, that.canonicalName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, id, alias, canonicalName);
    }

    @Override
    public String toString() {
        return String.format("%s: %s -> %s, owned by %s", id, alias, canonicalName, owner.toShortString());
    }

    public static String createAlias(ClusterSpec.Id clusterId, ApplicationId applicationId, ZoneId zoneId) {
        List<String> parts = Arrays.asList(ignorePartIfDefault(clusterId.value()),
                                           ignorePartIfDefault(applicationId.instance().value()),
                                           applicationId.application().value(),
                                           applicationId.tenant().value(),
                                           zoneId.value() + "." + "vespa.oath.cloud"
        );
        return parts.stream()
                    .filter(s -> !Strings.isNullOrEmpty((s)))
                    .collect(Collectors.joining("--"));
    }

    private static String ignorePartIfDefault(String s) {
        return ignoredEndpointPart.equalsIgnoreCase(s) ? "" : s;
    }

}
