// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerPath;

import java.util.List;

import static com.yahoo.vespa.hosted.node.admin.maintenance.servicedump.Artifact.Classification.CONFIDENTIAL;

/**
 * Performs dump of ZooKeeper snapshots. Can be used for controllers, config servers, cluster controllers and tenant containers
 * where zookeeper is configured.
 *
 * @author hmusum
 */
class ZooKeeperSnapshotDumper implements ArtifactProducer {
    @Override public String artifactName() { return "zookeeper-snapshot"; }
    @Override public String description() { return "Dumps ZooKeeper snapshots"; }

    @Override
    public List<Artifact> produceArtifacts(Context ctx) {
        ContainerPath zookeeperSnapshot = ctx.outputContainerPath().resolve("zookeeper-snapshot.tgz");
        List<String> cmd = List.of("bash", "-c", String.format("/opt/vespa/bin/vespa-backup-zk-data.sh -o %s -k -f", zookeeperSnapshot.pathInContainer()));
        ctx.executeCommandInNode(cmd, true);
        return List.of(Artifact.newBuilder().classification(CONFIDENTIAL).file(zookeeperSnapshot).build());
    }
}
