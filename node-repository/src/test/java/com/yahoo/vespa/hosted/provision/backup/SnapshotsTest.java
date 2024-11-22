package com.yahoo.vespa.hosted.provision.backup;

import ai.vespa.secret.model.Key;
import ai.vespa.secret.model.Secret;
import ai.vespa.secret.model.SecretVersionId;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.security.KeyFormat;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SealedSharedKey;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * @author mpolden
 */
class SnapshotsTest {

    @Test
    void snapshot() {
        ProvisioningTester tester = new ProvisioningTester.Builder().build();
        tester.makeReadyHosts(3, new NodeResources(8, 32, 1000, 10))
              .prepareAndActivateInfraApplication(NodeType.host);

        // Deploy app
        ApplicationId app = ProvisioningTester.applicationId();
        ClusterSpec clusterSpec = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("c1")).vespaVersion("8.0").build();
        int nodeCount = 3;
        NodeResources nodeResources = new NodeResources(4, 16, 100, 10);
        List<Node> nodes = tester.deploy(app, clusterSpec, Capacity.from(new ClusterResources(nodeCount, 1, nodeResources)));

        // Create encrypted snapshot
        Snapshots snapshots = tester.nodeRepository().snapshots();
        String node0 = nodes.get(0).hostname();
        Snapshot snapshot0 = snapshots.create(node0, tester.clock().instant());

        // Request snapshot key
        PublicKey receiverPublicKey = KeyUtils.generateX25519KeyPair().getPublic();
        SealedSharedKey resealedKey = snapshots.keyOf(snapshot0.id(), node0, receiverPublicKey);
        assertNotEquals(snapshot0.key().sharedKey(), resealedKey);

        // Sealing key can be rotated independently of existing snapshots
        KeyPair keyPair = KeyUtils.generateX25519KeyPair();
        tester.secretStore().add(new Secret(Key.fromString("snapshot/sealingPrivateKey"),
                                            KeyUtils.toPem(keyPair.getPrivate(), KeyFormat.PKCS8).getBytes(),
                                            SecretVersionId.of("2")));
        assertEquals(SecretVersionId.of("1"), snapshots.require(snapshot0.id(), node0).key().sealingKeyVersion());
        assertNotEquals(snapshot0.key().sharedKey(), snapshots.keyOf(snapshot0.id(), node0, receiverPublicKey),
                        "Can reseal after key rotation");

        // Next snapshot uses latest sealing key
        String node1 = nodes.get(1).hostname();
        Snapshot snapshot1 = snapshots.create(node1, tester.clock().instant());
        assertEquals(SecretVersionId.of("2"), snapshot1.key().sealingKeyVersion());
    }

}
