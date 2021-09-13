package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeState;
import com.yahoo.vespa.hosted.node.admin.container.ContainerOperations;
import com.yahoo.vespa.hosted.node.admin.integration.NodeRepoMock;
import com.yahoo.vespa.hosted.node.admin.maintenance.sync.SyncClient;
import com.yahoo.vespa.hosted.node.admin.maintenance.sync.SyncFileInfo;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContextImpl;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandResult;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author bjorncs
 */
class VespaServiceDumperImplTest {

    private static final String HOSTNAME = "host-1.domain.tld";

    @Test
    void creates_valid_dump_id_from_dump_request() {
        long nowMillis = Instant.now().toEpochMilli();
        ServiceDumpReport request = new ServiceDumpReport(
                nowMillis, null, null, null, null, "default/container.3", null, null, List.of(JvmDumpProducer.NAME), null);
        String dumpId = VespaServiceDumperImpl.createDumpId(request);
        assertEquals("default-container-3-" + nowMillis, dumpId);
    }

    @Test
    public void generates_dump_from_request() throws IOException {
        // Create temporary directory in container
        FileSystem fileSystem = TestFileSystem.create();
        Path tmpDirectory = fileSystem.getPath("/home/docker/container-storage/host-1/opt/vespa/tmp");
        Files.createDirectories(tmpDirectory);

        // Setup mocks
        ContainerOperations operations = createContainerMock(tmpDirectory);
        SyncClient syncClient = createSyncClientMock();
        NodeRepoMock nodeRepository = new NodeRepoMock();
        ManualClock clock = new ManualClock(Instant.ofEpochMilli(1600001000000L));
        ServiceDumpReport request = new ServiceDumpReport(
                1600000000000L, null, null, null, null, "default/container.1", null, null, List.of(JvmDumpProducer.NAME), null);
        NodeSpec initialSpec = NodeSpec.Builder
                .testSpec(HOSTNAME, NodeState.active)
                .report(ServiceDumpReport.REPORT_ID, request.toJsonNode())
                .archiveUri(URI.create("s3://uri-1/tenant1/"))
                .build();
        nodeRepository.updateNodeSpec(initialSpec);

        // Create dumper and invoke tested method
        VespaServiceDumper reporter = new VespaServiceDumperImpl(operations, syncClient, nodeRepository, clock);
        NodeAgentContextImpl context = new NodeAgentContextImpl.Builder(initialSpec)
                .fileSystem(fileSystem)
                .build();
        reporter.processServiceDumpRequest(context);

        // Verify
        String expectedJson =
                "{\"createdMillis\":1600000000000,\"startedAt\":1600001000000,\"completedAt\":1600001000000," +
                        "\"location\":\"s3://uri-1/tenant1/service-dump/default-container-1-1600000000000/\"," +
                        "\"configId\":\"default/container.1\",\"artifacts\":[\"jvm-dump\"]}";
        assertReportEquals(nodeRepository, expectedJson);
        verify(operations).executeCommandInContainerAsRoot(
                context, "/opt/vespa/bin/vespa-jvm-dumper", "default/container.1", "/opt/vespa/tmp/vespa-service-dump/jvm-dump");
        List<URI> expectedUris = List.of(
                URI.create("s3://uri-1/tenant1/service-dump/default-container-1-1600000000000/jvm-dump/heap.bin.zst"),
                URI.create("s3://uri-1/tenant1/service-dump/default-container-1-1600000000000/jvm-dump/jstack"));
        assertSyncedFiles(context, syncClient, expectedUris);

    }

    private static void assertReportEquals(NodeRepoMock nodeRepository, String expectedJson) {
        ServiceDumpReport report = nodeRepository.getNode(HOSTNAME).reports()
                .getReport(ServiceDumpReport.REPORT_ID, ServiceDumpReport.class).get();
        String actualJson = report.toJson();
        assertEquals(expectedJson, actualJson);
    }

    @SuppressWarnings("unchecked")
    private static void assertSyncedFiles(NodeAgentContextImpl context, SyncClient client, List<URI> expectedDestinations) {
        ArgumentCaptor<List<SyncFileInfo>> filesCaptor = ArgumentCaptor.forClass(List.class);
        verify(client).sync(eq(context), filesCaptor.capture(), eq(Integer.MAX_VALUE));
        List<SyncFileInfo> actualFiles = filesCaptor.getValue();
        List<URI> actualFilenames = actualFiles.stream()
                .map(SyncFileInfo::destination)
                .sorted()
                .collect(Collectors.toList());
        assertEquals(expectedDestinations, actualFilenames);
    }

    private static ContainerOperations createContainerMock(Path tmpDirectory) {
        ContainerOperations operations = mock(ContainerOperations.class);
        when(operations.executeCommandInContainerAsRoot(any(), any()))
                .thenAnswer(invocation -> {
                    // Create dummy files to simulate vespa-jvm-dumper
                    Files.createFile(tmpDirectory.resolve("vespa-service-dump/" + JvmDumpProducer.NAME + "/heap.bin"));
                    Files.createFile(tmpDirectory.resolve("vespa-service-dump/" + JvmDumpProducer.NAME + "/jstack"));
                    return new CommandResult(null, 0, "result");
                });
        return operations;
    }

    private SyncClient createSyncClientMock() {
        SyncClient client = mock(SyncClient.class);
        when(client.sync(any(), any(), anyInt()))
                .thenReturn(true);
        return client;
    }
}