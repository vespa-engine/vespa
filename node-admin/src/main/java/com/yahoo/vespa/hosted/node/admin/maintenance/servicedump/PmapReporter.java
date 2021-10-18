// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerPath;

import java.util.List;

import static com.yahoo.vespa.hosted.node.admin.maintenance.servicedump.Artifact.Classification.INTERNAL;

/**
 * @author bjorncs
 */
class PmapReporter implements ArtifactProducer {
    @Override public String artifactName() { return "pmap"; }
    @Override public String description() { return "Pmap report"; }

    @Override
    public List<Artifact> produceArtifacts(Context ctx) {
        ContainerPath pmapReport = ctx.outputContainerPath().resolve("pmap.txt");
        List<String> cmd = List.of("bash", "-c", "pmap -x " + ctx.servicePid() + " > " + pmapReport.pathInContainer());
        ctx.executeCommandInNode(cmd, true);
        return List.of(Artifact.newBuilder().classification(INTERNAL).file(pmapReport).build());
    }
}
