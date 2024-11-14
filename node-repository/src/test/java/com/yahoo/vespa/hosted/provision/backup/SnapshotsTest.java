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
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SealedSharedKey;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.XECPrivateKey;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertTrue(snapshot0.key().isPresent());

        // Request snapshot key
        PublicKey receiverPublicKey = KeyUtils.generateX25519KeyPair().getPublic();
        SealedSharedKey resealedKey = snapshots.keyOf(snapshot0.id(), node0, receiverPublicKey);
        assertNotEquals(snapshot0.key().get().sharedKey(), resealedKey);

        // Sealing key can be rotated independently of existing snapshots
        KeyPair keyPair = KeyUtils.generateX25519KeyPair();
        tester.secretStore().add(new Secret(Key.fromString("snapshot/sealingPrivateKey"),
                                            KeyUtils.toBase64EncodedX25519PrivateKey((XECPrivateKey) keyPair.getPrivate())
                                                    .getBytes(),
                                            SecretVersionId.of("2")));
        assertEquals(SecretVersionId.of("1"), snapshots.require(snapshot0.id(), node0).key().get().sealingKeyVersion());
        assertNotEquals(snapshot0.key().get().sharedKey(), snapshots.keyOf(snapshot0.id(), node0, receiverPublicKey),
                        "Can reseal after key rotation");

        // Next snapshot uses latest sealing key
        String node1 = nodes.get(1).hostname();
        Snapshot snapshot1 = snapshots.create(node1, tester.clock().instant());
        assertEquals(SecretVersionId.of("2"), snapshot1.key().get().sealingKeyVersion());
    }

}
