// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.cores;

import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApi;
import com.yahoo.vespa.hosted.node.admin.configserver.StandardConfigServerResponse;
import com.yahoo.vespa.hosted.node.admin.configserver.cores.bindings.ReportCoreDumpRequest;

import java.util.List;

/**
 * @author hakonhall
 */
public class CoresImpl implements Cores {
    private final ConfigServerApi configServerApi;

    public CoresImpl(ConfigServerApi configServerApi) {
        this.configServerApi = configServerApi;
    }

    @Override
    public void report(HostName hostname, String id, CoreDumpMetadata metadata) {
        var request = new ReportCoreDumpRequest();
        metadata.binPath().ifPresent(binPath -> request.bin_path = binPath);
        metadata.backtrace().ifPresent(backtrace -> request.backtrace = List.copyOf(backtrace));
        metadata.backtraceAllThreads().ifPresent(backtraceAllThreads -> request.backtrace_all_threads = List.copyOf(backtraceAllThreads));
        metadata.coredumpPath().ifPresent(coredumpPath -> request.coredump_path = coredumpPath.toString());
        metadata.kernelVersion().ifPresent(kernelVersion -> request.kernel_version = kernelVersion);
        metadata.cpuMicrocodeVersion().ifPresent(cpuMicrocodeVersion -> request.cpu_microcode_version = cpuMicrocodeVersion);
        metadata.dockerImage().ifPresent(dockerImage -> request.docker_image = dockerImage.asString());
        metadata.vespaVersion().ifPresent(vespaVersion -> request.vespa_version = vespaVersion);

        String uriPath = "/cores/v1/report/" + hostname.value() + "/" + id;
        configServerApi.post(uriPath, request, StandardConfigServerResponse.class)
                       .throwOnError("Failed to report core dump at " + metadata.coredumpPath());
    }
}
