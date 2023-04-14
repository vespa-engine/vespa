// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.cores;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.HostName;
import com.yahoo.test.json.JsonTestHelper;
import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApi;
import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.node.admin.configserver.StandardConfigServerResponse;
import com.yahoo.vespa.hosted.node.admin.configserver.cores.bindings.ReportCoreDumpRequest;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.yahoo.yolean.Exceptions.uncheck;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author hakonhall
 */
class CoresTest {
    private final FileSystem fileSystem = TestFileSystem.create();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConfigServerApi configServerApi = mock(ConfigServerApi.class);
    private final Cores cores = new CoresImpl(configServerApi);
    private final HostName hostname = HostName.of("foo.com");
    private final String id = "5c987afb-347a-49ee-a0c5-bef56bbddeb0";
    private final CoreDumpMetadata metadata = new CoreDumpMetadata()
            .setType(CoreDumpMetadata.Type.OOM)
            .setCreated(Instant.ofEpochMilli(12345678))
            .setKernelVersion("4.18.0-372.26.1.el8_6.x86_64")
            .setCpuMicrocodeVersion("0x1000065")
            .setCoreDumpPath(fileSystem.getPath("/data/vespa/processed-coredumps/h7641a/5c987afb-347a-49ee-a0c5-bef56bbddeb0/dump_java.core.813"))
            .setDecryptionToken("987def")
            .setDockerImage(DockerImage.fromString("us-central1-docker.pkg.dev/vespa-external-cd/vespa-cloud/vespa/cloud-tenant-rhel8:8.68.8"))
            .setBinPath("/usr/bin/java")
            .setVespaVersion("8.68.8")
            .setBacktraceAllThreads(List.of("Attaching to core /opt/vespa/var/crash/processing/5c987afb-347a-49ee-a0c5-bef56bbddeb0/dump_java.core.813 from executable /usr/bin/java, please wait...",
                                            "Debugger attached successfully.",
                                            " - com.yahoo.jdisc.core.TimeoutManagerImpl$ManagerTask.run() @bci=3, line=123 (Interpreted frame)",
                                            " - java.lang.Thread.run() @bci=11, line=833 (Interpreted frame)"))
            .setBacktrace(List.of("Example", "of", "backtrace"));

    @Test
    void reportOK() {
        var oKResponse = new StandardConfigServerResponse();
        oKResponse.message = "OK";
        when(configServerApi.post(any(), any(), any())).thenReturn(oKResponse);

        cores.report(hostname, id, metadata);

        var pathCaptor = ArgumentCaptor.forClass(String.class);
        var bodyJsonPojoCaptor = ArgumentCaptor.forClass(Object.class);
        verify(configServerApi, times(1)).post(pathCaptor.capture(), bodyJsonPojoCaptor.capture(), any());

        assertEquals("/cores/v1/report/" + hostname + "/" + id, pathCaptor.getValue());

        assertEquals("""
                     {
                       "backtrace": [
                         "Example",
                         "of",
                         "backtrace"
                       ],
                       "backtrace_all_threads": [
                         "Attaching to core /opt/vespa/var/crash/processing/5c987afb-347a-49ee-a0c5-bef56bbddeb0/dump_java.core.813 from executable /usr/bin/java, please wait...",
                         "Debugger attached successfully.",
                         " - com.yahoo.jdisc.core.TimeoutManagerImpl$ManagerTask.run() @bci=3, line=123 (Interpreted frame)",
                         " - java.lang.Thread.run() @bci=11, line=833 (Interpreted frame)"
                       ],
                       "bin_path": "/usr/bin/java",
                       "coredump_path": "/data/vespa/processed-coredumps/h7641a/5c987afb-347a-49ee-a0c5-bef56bbddeb0/dump_java.core.813",
                       "cpu_microcode_version": "0x1000065",
                       "created": 12345678,
                       "decryption_token": "987def",
                       "docker_image": "us-central1-docker.pkg.dev/vespa-external-cd/vespa-cloud/vespa/cloud-tenant-rhel8:8.68.8",
                       "kernel_version": "4.18.0-372.26.1.el8_6.x86_64",
                       "type": "OOM",
                       "vespa_version": "8.68.8"
                     }""",
                     JsonTestHelper.normalize(uncheck(() -> mapper.writeValueAsString(bodyJsonPojoCaptor.getValue()))));
    }

    @Test
    void reportFails() {
        var response = new StandardConfigServerResponse();
        response.errorCode = "503";
        response.message = "error detail";
        when(configServerApi.post(any(), any(), any())).thenReturn(response);

        assertThrows(ConfigServerException.class,
                     () -> cores.report(hostname, "abcde-1234", metadata),
                     "Failed to report core dump at Optional[/data/vespa/processed-coredumps/h7641a/5c987afb-347a-49ee-a0c5-bef56bbddeb0/dump_java.core.813]: error detail 503");

        var pathCaptor = ArgumentCaptor.forClass(String.class);
        var bodyJsonPojoCaptor = ArgumentCaptor.forClass(Object.class);
        verify(configServerApi).post(pathCaptor.capture(), bodyJsonPojoCaptor.capture(), any());
    }

    @Test
    void serialization() {
        Path path = fileSystem.getPath("/foo.json");
        ReportCoreDumpRequest request = new ReportCoreDumpRequest().fillFrom(metadata);
        request.save(path);
        assertEquals("""
                     {
                       "backtrace": [
                         "Example",
                         "of",
                         "backtrace"
                       ],
                       "backtrace_all_threads": [
                         "Attaching to core /opt/vespa/var/crash/processing/5c987afb-347a-49ee-a0c5-bef56bbddeb0/dump_java.core.813 from executable /usr/bin/java, please wait...",
                         "Debugger attached successfully.",
                         " - com.yahoo.jdisc.core.TimeoutManagerImpl$ManagerTask.run() @bci=3, line=123 (Interpreted frame)",
                         " - java.lang.Thread.run() @bci=11, line=833 (Interpreted frame)"
                       ],
                       "bin_path": "/usr/bin/java",
                       "coredump_path": "/data/vespa/processed-coredumps/h7641a/5c987afb-347a-49ee-a0c5-bef56bbddeb0/dump_java.core.813",
                       "cpu_microcode_version": "0x1000065",
                       "created": 12345678,
                       "decryption_token": "987def",
                       "docker_image": "us-central1-docker.pkg.dev/vespa-external-cd/vespa-cloud/vespa/cloud-tenant-rhel8:8.68.8",
                       "kernel_version": "4.18.0-372.26.1.el8_6.x86_64",
                       "type": "OOM",
                       "vespa_version": "8.68.8"
                     }""",
                     JsonTestHelper.normalize(new UnixPath(path).readUtf8File()));

        Optional<ReportCoreDumpRequest> loaded = ReportCoreDumpRequest.load(path);
        assertTrue(loaded.isPresent());
        var meta = new CoreDumpMetadata();
        loaded.get().populateMetadata(meta, fileSystem);
        assertEquals(metadata, meta);
    }
}
