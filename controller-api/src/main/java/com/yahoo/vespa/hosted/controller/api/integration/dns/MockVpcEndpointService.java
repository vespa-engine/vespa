package com.yahoo.vespa.hosted.controller.api.integration.dns;

import ai.vespa.http.DomainName;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.vespa.hosted.controller.api.identifiers.ClusterId;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record.Type;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author jonmv
 */
public class MockVpcEndpointService implements VpcEndpointService {

    public final AtomicBoolean enabled = new AtomicBoolean();
    public final Map<RecordName, State> outcomes = new ConcurrentHashMap<>();

    private final Clock clock;
    private final NameService nameService;

    public MockVpcEndpointService(Clock clock, NameService nameService) {
        this.clock = clock;
        this.nameService = nameService;
    }

    @Override
    public synchronized Optional<DnsChallenge> setPrivateDns(DomainName privateDnsName, ClusterId clusterId, Optional<CloudAccount> account) {
        DnsChallenge challenge = new DnsChallenge(RecordName.from("challenge--" + privateDnsName.value()),
                                                  RecordData.from(account.map(CloudAccount::value).orElse("system")),
                                                  clusterId,
                                                  "service-id",
                                                  account,
                                                  clock.instant(),
                                                  State.pending);
        return Optional.ofNullable(enabled.get() && nameService.findRecords(Type.TXT, challenge.name()).isEmpty() ? challenge : null);
    }

    @Override
    public synchronized State process(DnsChallenge challenge) {
        if (outcomes.containsKey(challenge.name())) return outcomes.get(challenge.name());
        if (nameService.findRecords(Type.TXT, challenge.name()).isEmpty()) throw new RuntimeException("No TXT record found for " + challenge.name());
        return State.done;
    }

    @Override
    public synchronized List<VpcEndpoint> getConnections(ClusterId cluster, Optional<CloudAccount> account) {
        return List.of(new VpcEndpoint("endpoint-1", "available"));
    }

}
