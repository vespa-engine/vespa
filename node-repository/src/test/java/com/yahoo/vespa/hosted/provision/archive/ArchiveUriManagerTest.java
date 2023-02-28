// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.archive;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Cloud;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

/**
 * @author freva
 */
public class ArchiveUriManagerTest {

    @Test
    public void archive_uri() {
        ApplicationId app1 = ApplicationId.from("vespa", "music", "main");
        ApplicationId app2 = ApplicationId.from("yahoo", "music", "main");
        CloudAccount account1 = CloudAccount.from("123456789012");
        CloudAccount account2 = CloudAccount.from("210987654321");
        CloudAccount accountSystem = CloudAccount.from("555444333222");
        ArchiveUriManager archiveUriManager = new ProvisioningTester.Builder()
                .zone(new Zone(Cloud.builder().account(accountSystem).build(), SystemName.Public, Environment.prod, RegionName.defaultName()))
                .build().nodeRepository().archiveUriManager();

        // Initially no uris are set
        assertFalse(archiveUriManager.archiveUriFor(createNode(null, null)).isPresent());
        assertFalse(archiveUriManager.archiveUriFor(createNode(app1, account1)).isPresent());

        archiveUriManager.setArchiveUri(app1.tenant(), Optional.of("scheme://tenant-bucket/dir"));
        archiveUriManager.setArchiveUri(account1, Optional.of("scheme://account-bucket/dir"));
        assertThrows(IllegalArgumentException.class, () -> archiveUriManager.setArchiveUri(accountSystem, Optional.of("scheme://something")));
        assertThrows(IllegalArgumentException.class, () -> archiveUriManager.setArchiveUri(CloudAccount.empty, Optional.of("scheme://something")));

        assertFalse(archiveUriManager.archiveUriFor(createNode(null, null)).isPresent()); // Not allocated
        assertFalse(archiveUriManager.archiveUriFor(createNode(null, account1)).isPresent()); // URI set for this account, but not allocated
        assertFalse(archiveUriManager.archiveUriFor(createNode(null, account2)).isPresent()); // Not allocated
        assertFalse(archiveUriManager.archiveUriFor(createNode(app2, null)).isPresent()); // No URI set for this tenant or account
        assertEquals("scheme://tenant-bucket/dir/vespa/music/main/default/h432a/", archiveUriManager.archiveUriFor(createNode(app1, null)).get());
        assertEquals("scheme://account-bucket/dir/vespa/music/main/default/h432a/", archiveUriManager.archiveUriFor(createNode(app1, account1)).get()); // Account has precedence
        assertFalse(archiveUriManager.archiveUriFor(createNode(app1, account2)).isPresent()); // URI set for this tenant, but is ignored because enclave account
        assertEquals("scheme://tenant-bucket/dir/vespa/music/main/default/h432a/", archiveUriManager.archiveUriFor(createNode(app1, accountSystem)).get()); // URI for tenant because non-enclave acocunt
    }

    @Test
    public void handles_uri_with_tenant_name() {
        ApplicationId app1 = ApplicationId.from("vespa", "music", "main");
        ArchiveUriManager archiveUriManager = new ProvisioningTester.Builder().build().nodeRepository().archiveUriManager();
        archiveUriManager.setArchiveUri(app1.tenant(), Optional.of("scheme://tenant-bucket/vespa"));
        assertEquals("scheme://tenant-bucket/vespa/music/main/default/h432a/", archiveUriManager.archiveUriFor(createNode(app1, null)).get());

        // Archive URI ends with the tenant name
        archiveUriManager.setArchiveUri(app1.tenant(), Optional.of("scheme://tenant-vespa/"));
        assertEquals("scheme://tenant-vespa/vespa/music/main/default/h432a/", archiveUriManager.archiveUriFor(createNode(app1, null)).get());
    }

    private Node createNode(ApplicationId appId, CloudAccount account) {
        Node.Builder nodeBuilder = Node.create("id", "h432a.prod.us-south-1.vespa.domain.tld", new Flavor(NodeResources.unspecified()), Node.State.parked, NodeType.tenant);
        Optional.ofNullable(appId)
                .map(app -> new Allocation(app,
                        ClusterMembership.from("container/default/0/0", Version.fromString("1.2.3"), Optional.empty()),
                        NodeResources.unspecified(),
                        Generation.initial(),
                        false))
                .ifPresent(nodeBuilder::allocation);
        Optional.ofNullable(account).ifPresent(nodeBuilder::cloudAccount);
        return nodeBuilder.build();
    }
}
