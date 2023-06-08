// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.config.provision.ClusterInfo;
import com.yahoo.config.provision.IntRange;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.applications.BcpGroupInfo;
import com.yahoo.vespa.hosted.provision.applications.ScalingEvent;
import com.yahoo.vespa.hosted.provision.applications.Status;
import com.yahoo.vespa.hosted.provision.autoscale.Autoscaling;
import com.yahoo.vespa.hosted.provision.autoscale.Load;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

/**
 * @author bratseth
 */
public class ApplicationSerializerTest {

    @Test
    public void testApplicationSerialization() {
        List<Cluster> clusters = new ArrayList<>();
        clusters.add(new Cluster(ClusterSpec.Id.from("c1"),
                                 false,
                                 new ClusterResources( 8, 4, new NodeResources(1, 2,  3,  4)),
                                 new ClusterResources(12, 6, new NodeResources(3, 6, 21, 24)),
                                 IntRange.empty(),
                                 true,
                                 Autoscaling.empty(),
                                 Autoscaling.empty(),
                                 ClusterInfo.empty(),
                                 BcpGroupInfo.empty(),
                                 List.of()));
        var minResources = new NodeResources(1, 2, 3, 4);
        clusters.add(new Cluster(ClusterSpec.Id.from("c2"),
                                 true,
                                 new ClusterResources( 8, 4, minResources),
                                 new ClusterResources(14, 7, new NodeResources(3, 6, 21, 24)),
                                 IntRange.of(3, 5),
                                 false,
                                 new Autoscaling(Autoscaling.Status.unavailable,
                                                 "",
                                                 Optional.of(new ClusterResources(20, 10,
                                                                                  new NodeResources(0.5, 4, 14, 16))),
                                                 Instant.ofEpochMilli(1234L),
                                                 new Load(0.1, 0.2, 0.3),
                                                 new Load(0.4, 0.5, 0.6),
                                                 new Autoscaling.Metrics(0.7, 0.8, 0.9)),
                                 new Autoscaling(Autoscaling.Status.insufficient,
                                                 "Autoscaling status",
                                                 Optional.of(new ClusterResources(10, 5,
                                                                                  new NodeResources(2, 4, 14, 16))),
                                                 Instant.ofEpochMilli(5678L),
                                                 Load.zero(),
                                                 Load.one(),
                                                 Autoscaling.Metrics.zero()),
                                 new ClusterInfo.Builder().bcpDeadline(Duration.ofMinutes(33)).hostTTL(Duration.ofSeconds(321)).build(),
                                 new BcpGroupInfo(0.1, 0.2, 0.3),
                                 List.of(new ScalingEvent(new ClusterResources(10, 5, minResources),
                                                          new ClusterResources(12, 6, minResources),
                                                          7L,
                                                          Instant.ofEpochMilli(12345L),
                                                          Optional.of(Instant.ofEpochMilli(67890L))))
                                 ));
        Application original = new Application(ApplicationId.from("myTenant", "myApplication", "myInstance"),
                                               Status.initial().withCurrentReadShare(0.3).withMaxReadShare(0.5),
                                               clusters);

        Application serialized = ApplicationSerializer.fromJson(ApplicationSerializer.toJson(original));
        assertNotSame(original, serialized);
        assertEquals(original, serialized);
        assertEquals(original.id(), serialized.id());
        assertNotSame(original.status(), serialized.status());
        assertEquals(original.status(), serialized.status());
        assertEquals(original.clusters(), serialized.clusters());
        for (Cluster originalCluster : original.clusters().values()) {
            Cluster serializedCluster = serialized.clusters().get(originalCluster.id());
            assertNotNull(serializedCluster);
            assertNotSame(originalCluster, serializedCluster);
            assertEquals(originalCluster.id(), serializedCluster.id());
            assertEquals(originalCluster.exclusive(), serializedCluster.exclusive());
            assertEquals(originalCluster.minResources(), serializedCluster.minResources());
            assertEquals(originalCluster.maxResources(), serializedCluster.maxResources());
            assertEquals(originalCluster.groupSize(), serializedCluster.groupSize());
            assertEquals(originalCluster.required(), serializedCluster.required());
            assertEquals(originalCluster.suggested(), serializedCluster.suggested());
            assertEquals(originalCluster.target(), serializedCluster.target());
            assertEquals(originalCluster.clusterInfo(), serializedCluster.clusterInfo());
            assertEquals(originalCluster.bcpGroupInfo(), serializedCluster.bcpGroupInfo());
            assertEquals(originalCluster.scalingEvents(), serializedCluster.scalingEvents());
        }
    }

}
