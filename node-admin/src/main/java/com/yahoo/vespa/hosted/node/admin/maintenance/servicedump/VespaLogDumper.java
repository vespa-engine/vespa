// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import com.yahoo.yolean.concurrent.Sleeper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.node.admin.maintenance.servicedump.Artifact.Classification.CONFIDENTIAL;
import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author bjorncs
 */
class VespaLogDumper implements ArtifactProducer {

    private static final Logger log = Logger.getLogger(VespaLogDumper.class.getName());

    private final Sleeper sleeper;

    VespaLogDumper(Sleeper sleeper) { this.sleeper = sleeper; }

    @Override public String artifactName() { return "vespa-log"; }
    @Override public String description() { return "Current Vespa logs"; }

    @Override
    public List<Artifact> produceArtifacts(Context ctx) {
        if (ctx.options().sendProfilingSignal()) {
            log.info("Sending SIGPROF to process to include vespa-malloc dump in Vespa log");
            ctx.executeCommandInNode(List.of("kill", "-SIGPROF", Integer.toString(ctx.servicePid())), true);
            sleeper.sleep(Duration.ofSeconds(3));
        }
        Path vespaLogFile = ctx.pathOnHostFromPathInNode(ctx.pathInNodeUnderVespaHome("logs/vespa/vespa.log"));
        Path destination = ctx.pathOnHostFromPathInNode(ctx.outputDirectoryInNode()).resolve("vespa.log");
        if (Files.exists(vespaLogFile)) {
            uncheck(() -> Files.copy(vespaLogFile, destination));
            return List.of(
                    Artifact.newBuilder().classification(CONFIDENTIAL).fileOnHost(destination).compressOnUpload().build());
        } else {
            log.info("Log file '" + vespaLogFile + "' does not exist");
            return List.of();
        }
    }
}
