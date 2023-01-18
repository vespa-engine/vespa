// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import org.junit.Test;

import java.util.Optional;

import static com.yahoo.vespa.hosted.provision.provisioning.ArchiveUris.normalizeUri;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

/**
 * @author freva
 */
public class ArchiveUrisTest {

    @Test
    public void archive_uri() {
        ApplicationId app = ApplicationId.from("vespa", "music", "main");
        Node allocated = createNode(app);
        Node unallocated = createNode(null);
        ArchiveUris archiveUris = new ProvisioningTester.Builder().build().nodeRepository().archiveUris();

        assertFalse(archiveUris.archiveUriFor(unallocated).isPresent());
        assertFalse(archiveUris.archiveUriFor(allocated).isPresent());

        archiveUris.setArchiveUri(app.tenant(), Optional.of("scheme://hostname/dir"));
        assertEquals("scheme://hostname/dir/music/main/default/h432a/", archiveUris.archiveUriFor(allocated).get());
    }

    private Node createNode(ApplicationId appId) {
        Node.Builder nodeBuilder = Node.create("id", "h432a.prod.us-south-1.vespa.domain.tld", new Flavor(NodeResources.unspecified()), Node.State.parked, NodeType.tenant);
        Optional.ofNullable(appId)
                .map(app -> new Allocation(app,
                        ClusterMembership.from("container/default/0/0", Version.fromString("1.2.3"), Optional.empty()),
                        NodeResources.unspecified(),
                        Generation.initial(),
                        false))
                .ifPresent(nodeBuilder::allocation);
        return nodeBuilder.build();
    }

    @Test
    public void normalize_test() {
        assertEquals("ftp://domain/legal-dir123/", normalizeUri("ftp://domain/legal-dir123"));
        assertEquals("ftp://domain/legal-dir123/", normalizeUri("ftp://domain/legal-dir123/"));
        assertEquals("s3://my-bucket-prod.region/my-tenant-123/", normalizeUri("s3://my-bucket-prod.region/my-tenant-123/"));
        assertEquals("s3://my-bucket-prod.region/my-tenant_123/", normalizeUri("s3://my-bucket-prod.region/my-tenant_123/"));
        assertThrows(IllegalArgumentException.class, () -> normalizeUri("domain/dir/"));
        assertThrows(IllegalArgumentException.class, () -> normalizeUri("ftp:/domain/dir/"));
        assertThrows(IllegalArgumentException.class, () -> normalizeUri("ftp:/domain//dir/"));
        assertThrows(IllegalArgumentException.class, () -> normalizeUri("ftp://domain/illegal:dir/"));
        assertThrows(IllegalArgumentException.class, () -> normalizeUri("ftp://domain/-illegal-dir/"));
        assertThrows(IllegalArgumentException.class, () -> normalizeUri("ftp://domain/_illegal-dir/"));
        assertThrows(IllegalArgumentException.class, () -> normalizeUri("ftp://domain/illegal-dir-/"));
        assertThrows(IllegalArgumentException.class, () -> normalizeUri("ftp://domain/illegal-dir_/"));
    }

    private static void assertThrows(Class<? extends Throwable> clazz, Runnable runnable) {
        try {
            runnable.run();
            fail("Expected " + clazz);
        } catch (Throwable e) {
            if (!clazz.isInstance(e)) throw e;
        }
    }
}
