// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import java.nio.file.Path;
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
        Path pmapReport = ctx.outputDirectoryInNode().resolve("pmap.txt");
        List<String> cmd = List.of("bash", "-c", "pmap -x " + ctx.servicePid() + " > " + pmapReport);
        ctx.executeCommandInNode(cmd, true);
        return List.of(Artifact.newBuilder().classification(INTERNAL).fileInNode(pmapReport).build());
    }
}
