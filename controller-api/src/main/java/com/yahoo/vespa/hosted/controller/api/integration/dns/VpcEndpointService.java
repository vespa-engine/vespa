package com.yahoo.vespa.hosted.controller.api.integration.dns;

import ai.vespa.http.DomainName;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.vespa.hosted.controller.api.identifiers.ClusterId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * @author jonmv
 */
public interface VpcEndpointService {

    /** Create a TXT record with this name and token, and then complete the challenge. */
    record DnsChallenge(RecordName name, RecordData data, ClusterId clusterId, String serviceId,
                        Optional<CloudAccount> account, Instant createdAt, State state) {

            public DnsChallenge {
                requireNonNull(name, "name must be non-null");
                requireNonNull(data, "data must be non-null");
                requireNonNull(clusterId, "clusterId must be non-null");
                requireNonNull(serviceId, "serviceId must be non-null");
                requireNonNull(account, "account must be non-null");
                requireNonNull(createdAt, "createdAt must be non-null");
                requireNonNull(state, "state must be non-null");
            }

            public DnsChallenge withState(State state) {
                return new DnsChallenge(name, data, clusterId, serviceId, account, createdAt, state);
            }

    }

    enum State { pending, ready, running, done }

    /** Sets the private DNS name for any VPC endpoint for the given cluster, potentially guarded by a challenge. */
    Optional<DnsChallenge> setPrivateDns(DomainName privateDnsName, ClusterId clusterId, Optional<CloudAccount> account);

    /** Attempts to complete the challenge, and returns the updated challenge state. */
    State process(DnsChallenge challenge);

    /** A connection made to an endpoint service. */
    record VpcEndpoint(String endpointId, String state) { }

    /** Lists all endpoints connected to an endpoint service (owned by account) for the given cluster. */
    List<VpcEndpoint> getConnections(ClusterId cluster, Optional<CloudAccount> account);

}
