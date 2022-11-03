// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.cloud;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
public class SystemInfoTest {

    @Test
    @SuppressWarnings("removal")
    void testSystemInfo() {
        ApplicationId application = new ApplicationId("tenant1", "application1", "instance1");
        Zone zone = new Zone(Environment.dev, "us-west-1");
        Cloud cloud = new Cloud("aws");
        String cluster = "clusterName";
        Node node = new Node(0);

        SystemInfo info = new SystemInfo(application, zone, cloud, cluster, node);
        assertEquals(application, info.application());
        assertEquals(zone, info.zone());
        assertEquals(cloud, info.cloud());
        assertEquals(cluster, info.clusterName());
        assertEquals(node, info.node());
    }

    @Test
    void testZone() {
        Zone zone = Zone.from("dev.us-west-1");
        zone = Zone.from(zone.toString());
        assertEquals(Environment.dev, zone.environment());
        assertEquals("us-west-1", zone.region());
        Zone sameZone = Zone.from("dev.us-west-1");
        assertEquals(sameZone.hashCode(), zone.hashCode());
        assertEquals(sameZone, zone);

        try {
            Zone.from("invalid");
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("A zone string must be on the form [environment].[region], but was 'invalid'",
                         e.getMessage());
        }

        try {
            Zone.from("invalid.us-west-1");
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Invalid zone 'invalid.us-west-1': No environment named 'invalid'", e.getMessage());
        }
    }

    @Test
    void testCluster() {
        String id = "clusterId";
        int size = 1;
        var indices = List.of(1);
        Cluster cluster = new Cluster("clusterId", size, indices);
        assertEquals(id, cluster.id());
        assertEquals(size, cluster.size());
        assertEquals(indices, cluster.indices());
    }

    @Test
    void testNode() {
        int index = 0;
        Node node = new Node(index);
        assertEquals(index, node.index());
    }

}
