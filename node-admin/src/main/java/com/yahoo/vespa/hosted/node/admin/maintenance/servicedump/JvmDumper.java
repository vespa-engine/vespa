// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import com.yahoo.yolean.concurrent.Sleeper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static com.yahoo.vespa.hosted.node.admin.maintenance.servicedump.Artifact.Classification.CONFIDENTIAL;
import static com.yahoo.vespa.hosted.node.admin.maintenance.servicedump.Artifact.Classification.INTERNAL;

/**
 * @author bjorncs
 */
class JvmDumper {
    private JvmDumper() {}

    static class HeapDump implements ArtifactProducer {
        @Override public String artifactName() { return "jvm-heap-dump"; }
        @Override public String description() { return "JVM heap dump"; }

        @Override
        public List<Artifact> produceArtifacts(Context ctx) {
            Path heapDumpFile = ctx.outputDirectoryInNode().resolve("jvm-heap-dump.bin");
            List<String> cmd = List.of(
                    "jmap", "-dump:live,format=b,file=" + heapDumpFile, Integer.toString(ctx.servicePid()));
            ctx.executeCommandInNode(cmd, true);
            return List.of(
                    Artifact.newBuilder().classification(CONFIDENTIAL).fileInNode(heapDumpFile).compressOnUpload().build());
        }
    }

    static class Jmap implements ArtifactProducer {
        @Override public String artifactName() { return "jvm-jmap"; }
        @Override public String description() { return "JVM jmap output"; }

        @Override
        public List<Artifact> produceArtifacts(Context ctx) {
            Path jmapReport = ctx.outputDirectoryInNode().resolve("jvm-jmap.txt");
            List<String> cmd = List.of("bash", "-c", "jhsdb jmap --heap --pid " + ctx.servicePid() + " > " + jmapReport);
            ctx.executeCommandInNode(cmd, true);
            return List.of(Artifact.newBuilder().classification(INTERNAL).fileInNode(jmapReport).build());
        }
    }

    static class Jstat implements ArtifactProducer {
        @Override public String artifactName() { return "jvm-jstat"; }
        @Override public String description() { return "JVM jstat output"; }

        @Override
        public List<Artifact> produceArtifacts(Context ctx) {
            Path jstatReport = ctx.outputDirectoryInNode().resolve("jvm-jstat.txt");
            List<String> cmd = List.of("bash", "-c", "jstat -gcutil " + ctx.servicePid() + " > " + jstatReport);
            ctx.executeCommandInNode(cmd, true);
            return List.of(Artifact.newBuilder().classification(INTERNAL).fileInNode(jstatReport).build());
        }
    }

    static class Jstack implements ArtifactProducer {
        @Override public String artifactName() { return "jvm-jstack"; }
        @Override public String description() { return "JVM jstack output"; }

        @Override
        public List<Artifact> produceArtifacts(Context ctx) {
            Path jstackReport = ctx.outputDirectoryInNode().resolve("jvm-jstack.txt");
            ctx.executeCommandInNode(List.of("bash", "-c", "jstack " + ctx.servicePid() + " > " + jstackReport), true);
            return List.of(Artifact.newBuilder().classification(INTERNAL).fileInNode(jstackReport).build());
        }
    }

    static class JavaFlightRecorder implements ArtifactProducer {
        private final Sleeper sleeper;

        JavaFlightRecorder(Sleeper sleeper) { this.sleeper = sleeper; }

        @Override public String artifactName() { return "jvm-jfr"; }
        @Override public String description() { return "Java Flight Recorder recording"; }

        @Override
        public List<Artifact> produceArtifacts(ArtifactProducer.Context ctx) {
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
}
