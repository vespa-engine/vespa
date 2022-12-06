package com.yahoo.vespa.hosted.controller.api.integration.dns;

import ai.vespa.http.DomainName;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.vespa.hosted.controller.api.identifiers.ClusterId;

import java.util.List;
import java.util.Optional;

/**
 * @author jonmv
 */
public class MockVpcEndpointService implements VpcEndpointService {

    public interface Stub extends VpcEndpointService {
        @Override default List<VpcEndpoint> getConnections(ClusterId clusterId, Optional<CloudAccount> account) {
            return List.of(new VpcEndpoint("endpoint-1", "available"));
        }
    }

    public static final Stub empty = (name, cluster, account) -> Optional.empty();

    public Stub delegate = empty;

    @Override
    public Optional<DnsChallenge> setPrivateDns(DomainName privateDnsName, ClusterId clusterId, Optional<CloudAccount> account) {
        return delegate.setPrivateDns(privateDnsName, clusterId, account);
    }

    @Override
    public List<VpcEndpoint> getConnections(ClusterId cluster, Optional<CloudAccount> account) {
        return List.of(new VpcEndpoint("endpoint-1", "available"));
    }

}
