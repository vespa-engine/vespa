// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.cores;

import com.yahoo.config.provision.DockerImage;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author hakonhall
 */
public class CoreDumpMetadata {
    public enum Type { CORE_DUMP, JVM_HEAP, OOM }

    private Type type;
    private String binPath;
    private Instant created;
    private List<String> backtrace;
    private List<String> backtraceAllThreads;
    private Path coreDumpPath;
    private String decryptionToken;
    private String kernelVersion;
    private String cpuMicrocodeVersion;
    private DockerImage dockerImage;
    private String vespaVersion;

    public CoreDumpMetadata() {}

    public Optional<Type> type() { return Optional.ofNullable(type); }
    public Optional<String> binPath() { return Optional.ofNullable(binPath); }
    public Optional<Instant> created() { return Optional.ofNullable(created); }
    public Optional<List<String>> backtrace() { return Optional.ofNullable(backtrace); }
    public Optional<List<String>> backtraceAllThreads() { return Optional.ofNullable(backtraceAllThreads); }
    public Optional<Path> coredumpPath() { return Optional.ofNullable(coreDumpPath); }
    public Optional<String> decryptionToken() { return Optional.ofNullable(decryptionToken); }
    public Optional<String> kernelVersion() { return Optional.ofNullable(kernelVersion); }
    public Optional<String> cpuMicrocodeVersion() { return Optional.ofNullable(cpuMicrocodeVersion); }
    public Optional<DockerImage> dockerImage() { return Optional.ofNullable(dockerImage); }
    public Optional<String> vespaVersion() { return Optional.ofNullable(vespaVersion); }

    public CoreDumpMetadata setType(Type type) { this.type = type; return this; }
    public CoreDumpMetadata setBinPath(String binPath) { this.binPath = binPath; return this; }
    public CoreDumpMetadata setCreated(Instant created) { this.created = created; return this; }
    public CoreDumpMetadata setBacktrace(List<String> backtrace) { this.backtrace = backtrace; return this; }
    public CoreDumpMetadata setBacktraceAllThreads(List<String> backtraceAllThreads) { this.backtraceAllThreads = backtraceAllThreads; return this; }
    public CoreDumpMetadata setCoreDumpPath(Path coreDumpPath) { this.coreDumpPath = coreDumpPath; return this; }
    public CoreDumpMetadata setDecryptionToken(String decryptionToken) { this.decryptionToken = decryptionToken; return this; }
    public CoreDumpMetadata setKernelVersion(String kernelVersion) { this.kernelVersion = kernelVersion; return this; }
    public CoreDumpMetadata setCpuMicrocodeVersion(String cpuMicrocodeVersion) { this.cpuMicrocodeVersion = cpuMicrocodeVersion; return this; }
    public CoreDumpMetadata setDockerImage(DockerImage dockerImage) { this.dockerImage = dockerImage; return this; }
    public CoreDumpMetadata setVespaVersion(String vespaVersion) { this.vespaVersion = vespaVersion; return this; }

    @Override
    public String toString() {
        return "CoreDumpMetadata{" +
               "type=" + type +
               ", binPath=" + binPath +
               ", created=" + created +
               ", backtrace=" + backtrace +
               ", backtraceAllThreads=" + backtraceAllThreads +
               ", coreDumpPath=" + coreDumpPath +
               ", decryptionToken=" + decryptionToken +
               ", kernelVersion='" + kernelVersion + '\'' +
               ", cpuMicrocodeVersion='" + cpuMicrocodeVersion + '\'' +
               ", dockerImage=" + dockerImage +
               ", vespaVersion=" + vespaVersion +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CoreDumpMetadata metadata = (CoreDumpMetadata) o;
        return type == metadata.type &&
               Objects.equals(binPath, metadata.binPath) &&
               Objects.equals(created, metadata.created) &&
               Objects.equals(backtrace, metadata.backtrace) &&
               Objects.equals(backtraceAllThreads, metadata.backtraceAllThreads) &&
               Objects.equals(coreDumpPath, metadata.coreDumpPath) &&
               Objects.equals(decryptionToken, metadata.decryptionToken) &&
               Objects.equals(kernelVersion, metadata.kernelVersion) &&
               Objects.equals(cpuMicrocodeVersion, metadata.cpuMicrocodeVersion) &&
               Objects.equals(dockerImage, metadata.dockerImage) &&
               Objects.equals(vespaVersion, metadata.vespaVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, binPath, created, backtrace, backtraceAllThreads, coreDumpPath, decryptionToken, kernelVersion,
                            cpuMicrocodeVersion, dockerImage, vespaVersion);
    }
}
