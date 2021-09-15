// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import com.yahoo.yolean.concurrent.Sleeper;
import com.yahoo.vespa.hosted.node.admin.container.ContainerOperations;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Creates a Java Flight Recorder dump.
 *
 * @author bjorncs
 */
class JavaFlightRecorder extends AbstractProducer {

    private final Sleeper sleeper;

    JavaFlightRecorder(ContainerOperations container, Sleeper sleeper) {
        super(container);
        this.sleeper = sleeper;
    }

    static String NAME = "jfr-recording";

    @Override public String name() { return NAME; }

    @Override
    public void produceArtifact(NodeAgentContext ctx, String configId, ServiceDumpReport.DumpOptions options,
                                UnixPath resultDirectoryInNode) throws IOException {
        int pid = findVespaServicePid(ctx, configId);
        int seconds = (int) (duration(ctx, options, 30.0));
        UnixPath outputFile = resultDirectoryInNode.resolve("recording.jfr");
        List<String> startCommand = List.of("jcmd", Integer.toString(pid), "JFR.start", "name=host-admin",
                "path-to-gc-roots=true", "settings=profile", "filename=" + outputFile, "duration=" + seconds + "s");
        executeCommand(ctx, startCommand, true);
        sleeper.sleep(Duration.ofSeconds(seconds).plusSeconds(1));
        int maxRetries = 10;
        List<String> checkCommand = List.of("jcmd", Integer.toString(pid), "JFR.check", "name=host-admin");
        for (int i = 0; i < maxRetries; i++) {
            boolean stillRunning = executeCommand(ctx, checkCommand, true).getOutputLines().stream()
                    .anyMatch(l -> l.contains("name=host-admin") && l.contains("running"));
            if (!stillRunning) return;
            sleeper.sleep(Duration.ofSeconds(1));
        }
        throw new IOException("Failed to wait for JFR dump to complete after " + maxRetries + " retries");
    }
}
