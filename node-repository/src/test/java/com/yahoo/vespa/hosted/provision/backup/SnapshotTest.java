package com.yahoo.vespa.hosted.provision.backup;

import ai.vespa.secret.model.SecretVersionId;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.security.KeyId;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SecretSharedKey;
import com.yahoo.security.SharedKeyGenerator;
import com.yahoo.vespa.hosted.provision.node.ClusterId;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;


/**
 * @author mpolden
 */
class SnapshotTest {

    @Test
    void state_changes() {
        assertAllowed(Snapshot.State.creating, Snapshot.State.created);
        assertAllowed(Snapshot.State.created, Snapshot.State.restoring);
        assertAllowed(Snapshot.State.restoring, Snapshot.State.restored);
        assertAllowed(Snapshot.State.restored, Snapshot.State.restoring);

        assertDisallowed(Snapshot.State.created, Snapshot.State.creating);
        assertDisallowed(Snapshot.State.creating, Snapshot.State.restoring);
        assertDisallowed(Snapshot.State.creating, Snapshot.State.restored);
        assertDisallowed(Snapshot.State.restored, Snapshot.State.created);
        assertDisallowed(Snapshot.State.restored, Snapshot.State.created);
    }

    private static void assertAllowed(Snapshot.State from, Snapshot.State to) {
        Snapshot snapshot = snapshot(from);
        assertSame(to, snapshot.with(to, Instant.ofEpochMilli(123)).state());
    }

    private static void assertDisallowed(Snapshot.State from, Snapshot.State to) {
        Snapshot snapshot = snapshot(from);
        try {
            snapshot.with(to, Instant.ofEpochMilli(123));
            fail("Changing state " + from + " -> " + to + " should fail");
        } catch (IllegalArgumentException ignored) {}
    }

    private static Snapshot snapshot(Snapshot.State state) {
        PublicKey publicKey = KeyUtils.generateX25519KeyPair().getPublic();
        SecretSharedKey sharedKey = SharedKeyGenerator.generateForReceiverPublicKey(publicKey,
                                                                                    KeyId.ofString("mykey"));
        return new Snapshot(Snapshot.generateId(), HostName.of("h1.example.com"), state,
                            Snapshot.History.of(state, Instant.ofEpochMilli(123)), new ClusterId(ApplicationId.defaultId(), ClusterSpec.Id.from("c1")),
                            0, CloudAccount.empty, new SnapshotKey(sharedKey.sealedSharedKey(), SecretVersionId.of("v1")));
    }

}
