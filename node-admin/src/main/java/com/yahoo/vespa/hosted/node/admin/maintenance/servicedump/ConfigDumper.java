// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerPath;

import java.util.List;

import static com.yahoo.vespa.hosted.node.admin.maintenance.servicedump.Artifact.Classification.CONFIDENTIAL;
import static com.yahoo.vespa.hosted.node.admin.maintenance.servicedump.Artifact.Classification.INTERNAL;

/**
 * Performs dump of config on a node.
 *
 * @author hmusum
 */
class ConfigDumper implements ArtifactProducer {
    @Override public String artifactName() { return "config-dump"; }
    @Override public String description() { return "Dumps config"; }

    @Override
    public List<Artifact> produceArtifacts(Context ctx) {
        ContainerPath dir = ctx.outputContainerPath().resolve("config");
        ContainerPath configDump = ctx.outputContainerPath().resolve("config-dump.tar.zst");
        List<String> cmd = List.of("bash", "-c",
                                   String.format("mkdir -p %s; /opt/vespa/bin/vespa-configproxy-cmd -m dumpcache %s; tar cvf %s.tar %s; zstd %s.tar -o %s",
                                                 dir.pathInContainer(),
                                                 dir.pathInContainer(),
                                                 dir.pathInContainer(),
                                                 dir.pathInContainer(),
                                                 dir.pathInContainer(),
                                                 configDump.pathInContainer()));
        ctx.executeCommandInNode(cmd, true);
        return List.of(Artifact.newBuilder().classification(INTERNAL).file(configDump).build());
    }
}
