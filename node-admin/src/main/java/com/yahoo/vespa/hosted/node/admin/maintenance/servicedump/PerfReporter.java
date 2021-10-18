// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerPath;

import java.util.ArrayList;
import java.util.List;

import static com.yahoo.vespa.hosted.node.admin.maintenance.servicedump.Artifact.Classification.CONFIDENTIAL;
import static com.yahoo.vespa.hosted.node.admin.maintenance.servicedump.Artifact.Classification.INTERNAL;

/**
 * @author bjorncs
 */
class PerfReporter implements ArtifactProducer {

    PerfReporter() {}

    @Override public String artifactName() { return "perf-report"; }
    @Override public String description() { return "Perf recording and report"; }

    @Override
    public List<Artifact> produceArtifacts(Context ctx) {
        int duration = (int)ctx.options().duration().orElse(30.0);
        List<String> perfRecordCommand = new ArrayList<>(List.of("perf", "record"));
        if (ctx.options().callGraphRecording()) {
            perfRecordCommand.add("-g");
        }
        ContainerPath recordFile = ctx.outputContainerPath().resolve("perf-record.bin");
        perfRecordCommand.addAll(
                List.of("--output=" + recordFile.pathInContainer(),
                        "--pid=" + ctx.servicePid(), "sleep", Integer.toString(duration)));
        ctx.executeCommandInNode(perfRecordCommand, true);
        ContainerPath reportFile = ctx.outputContainerPath().resolve("perf-report.txt");
        ctx.executeCommandInNode(List.of("bash", "-c", "perf report --input=" + recordFile.pathInContainer() + " > " + reportFile.pathInContainer()), true);
        return List.of(
                Artifact.newBuilder().classification(CONFIDENTIAL).file(recordFile).compressOnUpload().build(),
                Artifact.newBuilder().classification(INTERNAL).file(reportFile).build());
    }
}
