package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.identifiers.ClusterId;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.api.integration.dns.VpcEndpointService.DnsChallenge;
import com.yahoo.vespa.hosted.controller.api.integration.dns.VpcEndpointService.State;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author jonmv
 */
class DnsChallengeSerializerTest {

    private final DnsChallengeSerializer serializer = new DnsChallengeSerializer();
    private final ClusterId clusterId = new ClusterId(new DeploymentId(ApplicationId.defaultId(),
                                                                       ZoneId.defaultId()),
                                                      ClusterSpec.Id.from("default"));
    private final DnsChallenge challenge = new DnsChallenge(RecordName.from("name.tld"),
                                                            RecordData.from("1234"),
                                                            clusterId,
                                                            "deadbeef",
                                                            Optional.of(CloudAccount.from("123321123321")),
                                                            Instant.ofEpochMilli(123),
                                                            State.pending);

    @Test
    void testSerialization() {
        DnsChallenge deserialized = serializer.fromJson(serializer.toJson(challenge), clusterId);
        assertEquals(challenge, deserialized);
        for (State state : State.values())
            assertEquals(challenge.withState(state), serializer.fromJson(serializer.toJson(challenge.withState(state)), clusterId));
    }

}
