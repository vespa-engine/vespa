// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import com.yahoo.yolean.concurrent.Sleeper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static com.yahoo.vespa.hosted.node.admin.maintenance.servicedump.Artifact.Classification.CONFIDENTIAL;

/**
 * Creates a Java Flight Recorder dump.
 *
 * @author bjorncs
 */
class JavaFlightRecorder implements ArtifactProducer {

    private final Sleeper sleeper;

    JavaFlightRecorder(Sleeper sleeper) { this.sleeper = sleeper; }

    @Override public String artifactName() { return "jfr-recording"; }
    @Override public String description() { return "Java Flight Recorder recording"; }

    @Override
    public List<Artifact> produceArtifacts(Context ctx) {
        int seconds = (int) (ctx.options().duration().orElse(30.0));
        Path outputFile = ctx.outputDirectoryInNode().resolve("recording.jfr");
        List<String> startCommand = List.of("jcmd", Integer.toString(ctx.servicePid()), "JFR.start", "name=host-admin",
                "path-to-gc-roots=true", "settings=profile", "filename=" + outputFile, "duration=" + seconds + "s");
        ctx.executeCommandInNode(startCommand, true);
        sleeper.sleep(Duration.ofSeconds(seconds).plusSeconds(1));
        int maxRetries = 10;
        List<String> checkCommand = List.of("jcmd", Integer.toString(ctx.servicePid()), "JFR.check", "name=host-admin");
        for (int i = 0; i < maxRetries; i++) {
            boolean stillRunning = ctx.executeCommandInNode(checkCommand, true).getOutputLines().stream()
                    .anyMatch(l -> l.contains("name=host-admin") && l.contains("running"));
            if (!stillRunning) {
                Artifact a = Artifact.newBuilder()
                        .classification(CONFIDENTIAL).fileInNode(outputFile).compressOnUpload().build();
                return List.of(a);
            }
            sleeper.sleep(Duration.ofSeconds(1));
        }
        throw new RuntimeException("Failed to wait for JFR dump to complete after " + maxRetries + " retries");
    }

}
