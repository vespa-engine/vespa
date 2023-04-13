// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.cores.bindings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.vespa.hosted.node.admin.configserver.cores.CoreDumpMetadata;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Jackson class of JSON request, with names of fields verified in unit test.
 *
 * @author hakonhall
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportCoreDumpRequest {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public List<String> backtrace;
    public List<String> backtrace_all_threads;
    public Long created;
    public String type;
    public String bin_path;
    public String coredump_path;
    public String cpu_microcode_version;
    public String decryption_token;
    public String docker_image;
    public String kernel_version;
    public String vespa_version;

    public ReportCoreDumpRequest() {}

    /** Fill this from metadata and return this. */
    @JsonIgnore
    public ReportCoreDumpRequest fillFrom(CoreDumpMetadata metadata) {
        metadata.type().ifPresent(type -> this.type = type.name());
        metadata.binPath().ifPresent(binPath -> this.bin_path = binPath);
        metadata.created().ifPresent(created -> this.created = created.toEpochMilli());
        metadata.backtrace().ifPresent(backtrace -> this.backtrace = List.copyOf(backtrace));
        metadata.backtraceAllThreads().ifPresent(backtraceAllThreads -> this.backtrace_all_threads = List.copyOf(backtraceAllThreads));
        metadata.coredumpPath().ifPresent(coredumpPath -> this.coredump_path = coredumpPath.toString());
        metadata.decryptionToken().ifPresent(decryptionToken -> this.decryption_token = decryptionToken);
        metadata.kernelVersion().ifPresent(kernelVersion -> this.kernel_version = kernelVersion);
        metadata.cpuMicrocodeVersion().ifPresent(cpuMicrocodeVersion -> this.cpu_microcode_version = cpuMicrocodeVersion);
        metadata.dockerImage().ifPresent(dockerImage -> this.docker_image = dockerImage.asString());
        metadata.vespaVersion().ifPresent(vespaVersion -> this.vespa_version = vespaVersion);
        return this;
    }

    @JsonIgnore
    public void populateMetadata(CoreDumpMetadata metadata, FileSystem fileSystem) {
        if (type != null) metadata.setType(CoreDumpMetadata.Type.valueOf(type));
        if (bin_path != null) metadata.setBinPath(bin_path);
        if (created != null) metadata.setCreated(Instant.ofEpochMilli(created));
        if (backtrace != null) metadata.setBacktrace(backtrace);
        if (backtrace_all_threads != null) metadata.setBacktraceAllThreads(backtrace_all_threads);
        if (coredump_path != null) metadata.setCoreDumpPath(fileSystem.getPath(coredump_path));
        if (decryption_token != null) metadata.setDecryptionToken(decryption_token);
        if (kernel_version != null) metadata.setKernelVersion(kernel_version);
        if (cpu_microcode_version != null) metadata.setCpuMicrocodeVersion(cpu_microcode_version);
        if (docker_image != null) metadata.setDockerImage(DockerImage.fromString(docker_image));
        if (vespa_version != null) metadata.setVespaVersion(vespa_version);
    }

    @JsonIgnore
    public void save(Path path) {
        String serialized = uncheck(() -> objectMapper.writeValueAsString(this));
        uncheck(() -> Files.writeString(path, serialized));
    }

    @JsonIgnore
    public static Optional<ReportCoreDumpRequest> load(Path path) {
        final String serialized;
        try {
            serialized = Files.readString(path);
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Optional.of(uncheck(() -> objectMapper.readValue(serialized, ReportCoreDumpRequest.class)));
    }
}
