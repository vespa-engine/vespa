// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
public class NodesResponseTest {
    private final InMemoryFlagSource flagSource = new InMemoryFlagSource();

    @Test
    public void node_archive_url() {
        ApplicationId app = ApplicationId.from("vespa", "music", "main");
        // Flag not set, no archive url
        assertNodeArchiveUrl(null, "h432a.prod.us-south-1.vespa.domain.tld", NodeType.tenant, app);
        assertNodeArchiveUrl(null, "cfg1.prod.us-south-1.vespa.domain.tld", NodeType.config, app);

        flagSource.withStringFlag(Flags.SYNC_HOST_LOGS_TO_S3_BUCKET.id(), "vespa-data-bucket");
        // Flag is set, but node not allocated, only sync non-tenant nodes
        assertNodeArchiveUrl(null, "h432a.prod.us-south-1.vespa.domain.tld", NodeType.tenant, null);
        assertNodeArchiveUrl("s3://vespa-data-bucket/hosted-vespa/cfg1/", "cfg1.prod.us-south-1.vespa.domain.tld", NodeType.config, null);

        // Flag is set and node is allocated
        assertNodeArchiveUrl("s3://vespa-data-bucket/vespa/music/main/h432a/", "h432a.prod.us-south-1.vespa.domain.tld", NodeType.tenant, app);
        assertNodeArchiveUrl("s3://vespa-data-bucket/hosted-vespa/cfg1/", "cfg1.prod.us-south-1.vespa.domain.tld", NodeType.config, app);
    }

    private void assertNodeArchiveUrl(String archiveUrl, String hostname, NodeType type, ApplicationId appId) {
        Node.Builder nodeBuilder = Node.create("id", hostname, new Flavor(NodeResources.unspecified()), Node.State.parked, type);
        Optional.ofNullable(appId)
                .map(app -> new Allocation(app,
                        ClusterMembership.from("container/default/0/0", Version.fromString("1.2.3"), Optional.empty()),
                        NodeResources.unspecified(),
                        Generation.initial(),
                        false))
                .ifPresent(nodeBuilder::allocation);

        assertEquals(archiveUrl, NodesResponse.nodeArchiveUrl(flagSource, nodeBuilder.build()).orElse(null));
    }
}