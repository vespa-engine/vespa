package com.yahoo.vespa.hosted.controller.api.integration.dns;

import ai.vespa.http.DomainName;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.vespa.hosted.controller.api.identifiers.ClusterId;

import java.util.Optional;

/**
 * @author jonmv
 */
public interface VpcEndpointService {

    /** Sets the private DNS name for any VPC endpoint for the given cluster, potentially guarded by a challenge. */
    Optional<DnsChallenge> setPrivateDns(DomainName privateDnsName, ClusterId clusterId, Optional<CloudAccount> account);

    /** Create a TXT record with this name and token, then run the trigger, to pass this challenge. */
    record DnsChallenge(RecordName name, RecordData data, Runnable trigger) { }

}
