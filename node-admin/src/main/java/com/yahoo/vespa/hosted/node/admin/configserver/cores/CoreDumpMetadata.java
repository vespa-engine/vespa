// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.cores;

import com.yahoo.component.Version;
import com.yahoo.config.provision.DockerImage;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * @author hakonhall
 */
public class CoreDumpMetadata {
    private String binPath;
    private List<String> backtrace;
    private List<String> backtraceAllThreads;
    private Path coreDumpPath;
    private String kernelVersion;
    private String cpuMicrocodeVersion;
    private DockerImage dockerImage;
    private String vespaVersion;

    public CoreDumpMetadata() {}

    public Optional<String> binPath() { return Optional.ofNullable(binPath); };
    public Optional<List<String>> backtrace() { return Optional.ofNullable(backtrace); };
    public Optional<List<String>> backtraceAllThreads() { return Optional.ofNullable(backtraceAllThreads); };
    public Optional<Path> coredumpPath() { return Optional.ofNullable(coreDumpPath); };
    public Optional<String> kernelVersion() { return Optional.ofNullable(kernelVersion); };
    public Optional<String> cpuMicrocodeVersion() { return Optional.ofNullable(cpuMicrocodeVersion); };
    public Optional<DockerImage> dockerImage() { return Optional.ofNullable(dockerImage); };
    public Optional<String> vespaVersion() { return Optional.ofNullable(vespaVersion); };

    public CoreDumpMetadata setBinPath(String binPath) { this.binPath = binPath; return this; };
    public CoreDumpMetadata setBacktrace(List<String> backtrace) { this.backtrace = backtrace; return this; };
    public CoreDumpMetadata setBacktraceAllThreads(List<String> backtraceAllThreads) { this.backtraceAllThreads = backtraceAllThreads; return this; };
    public CoreDumpMetadata setCoreDumpPath(Path coreDumpPath) { this.coreDumpPath = coreDumpPath; return this; };
    public CoreDumpMetadata setKernelVersion(String kernelVersion) { this.kernelVersion = kernelVersion; return this; };
    public CoreDumpMetadata setCpuMicrocodeVersion(String cpuMicrocodeVersion) { this.cpuMicrocodeVersion = cpuMicrocodeVersion; return this; };
    public CoreDumpMetadata setDockerImage(DockerImage dockerImage) { this.dockerImage = dockerImage; return this; };
    public CoreDumpMetadata setVespaVersion(String vespaVersion) { this.vespaVersion = vespaVersion; return this; };

    @Override
    public String toString() {
        return "CoreDumpMetadata{" +
               "binPath=" + binPath +
               ", backtrace=" + backtrace +
               ", backtraceAllThreads=" + backtraceAllThreads +
               ", coreDumpPath=" + coreDumpPath +
               ", kernelVersion='" + kernelVersion + '\'' +
               ", cpuMicrocodeVersion='" + cpuMicrocodeVersion + '\'' +
               ", dockerImage=" + dockerImage +
               ", vespaVersion=" + vespaVersion +
               '}';
    }

    @Override public boolean equals(Object o) { throw new UnsupportedOperationException(); }
    @Override public int hashCode() { throw new UnsupportedOperationException(); }
}
