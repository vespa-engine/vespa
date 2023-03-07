package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.identifiers.ClusterId;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.api.integration.dns.VpcEndpointService.DnsChallenge;
import com.yahoo.vespa.hosted.controller.api.integration.dns.VpcEndpointService.ChallengeState;

import java.time.Instant;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author jonmv
 */
class DnsChallengeSerializer {

    private static final String nameField = "name";
    private static final String dataField = "data";
    private static final String serviceIdField = "serviceId";
    private static final String accountField = "account";
    private static final String createdAtField = "createdAt";
    private static final String stateField = "state";

    DnsChallenge fromJson(byte[] json, ClusterId clusterId) {
        Cursor object = SlimeUtils.jsonToSlime(json).get();
        return new DnsChallenge(RecordName.from(object.field(nameField).asString()),
                                RecordData.from(object.field(dataField).asString()),
                                clusterId,
                                object.field(serviceIdField).asString(),
                                SlimeUtils.optionalString(object.field(accountField)).map(CloudAccount::from),
                                Instant.ofEpochMilli(object.field(createdAtField).asLong()),
                                toState(object.field(stateField).asString()));
    }

    byte[] toJson(DnsChallenge challenge) {
        Slime slime = new Slime();
        Cursor object = slime.setObject();
        object.setString(nameField, challenge.name().name());
        object.setString(dataField, challenge.data().data());
        object.setString(serviceIdField, challenge.serviceId());
        challenge.account().ifPresent(account -> object.setString(accountField, account.value()));
        object.setLong(createdAtField, challenge.createdAt().toEpochMilli());
        object.setString(stateField, toString(challenge.state()));
        return uncheck(() -> SlimeUtils.toJsonBytes(slime));
    }

    private static ChallengeState toState(String value) {
        return switch (value) {
            case "pending" -> ChallengeState.pending;
            case "ready" -> ChallengeState.ready;
            case "running" -> ChallengeState.running;
            case "done" -> ChallengeState.done;
            default -> throw new IllegalArgumentException("invalid serialized state: " + value);
        };
    }

    private static String toString(ChallengeState state) {
        return switch (state) {
            case pending -> "pending";
            case ready -> "ready";
            case running -> "running";
            case done -> "done";
        };
    }

}
