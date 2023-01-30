// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import ai.vespa.http.DomainName;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.ZoneEndpoint.AllowedUrn;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 *  Represents an exclusive load balancer, assigned to an application's cluster.
 *
 * @author mortent
 */
public record LoadBalancer(String id, ApplicationId application, ClusterSpec.Id cluster,
                           Optional<DomainName> hostname, Optional<String> ipAddress,
                           State state, Optional<String> dnsZone, Optional<CloudAccount> cloudAccount,
                           Optional<PrivateServiceInfo> service, boolean isPublic) {

    public LoadBalancer {
        requireNonNull(id, "id must be non-null");
        requireNonNull(application, "application must be non-null");
        requireNonNull(cluster, "cluster must be non-null");
        requireNonNull(hostname, "hostname must be non-null");
        requireNonNull(ipAddress, "ipAddress must be non-null");
        requireNonNull(state, "state must be non-null");
        requireNonNull(dnsZone, "dnsZone must be non-null");
        requireNonNull(cloudAccount, "cloudAccount must be non-null");
        requireNonNull(service, "service must be non-null");
    }

    public enum State {
        active,
        inactive,
        reserved,
        unknown
    }

    public record PrivateServiceInfo(String id, List<AllowedUrn> allowedUrns) { }

}
