// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.node.admin.util.PathResolver;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author dybis
 */
public class StorageMaintainerTest {
    private final ManualClock clock = new ManualClock();
    private final Environment environment = new Environment.Builder()
            .pathResolver(new PathResolver()).build();
    private final Docker docker = mock(Docker.class);
    private final StorageMaintainer storageMaintainer = new StorageMaintainer(docker,
            new MetricReceiverWrapper(MetricReceiver.nullImplementation), environment, clock);

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testDiskUsed() throws IOException, InterruptedException {
        int writeSize = 10000;
        writeNBytesToFile(folder.newFile(), writeSize);

        long usedBytes = storageMaintainer.getDiscUsedInBytes(folder.getRoot().toPath());
        if (usedBytes * 4 < writeSize || usedBytes > writeSize * 4)
            fail("Used bytes is " + usedBytes + ", but wrote " + writeSize + " bytes, not even close.");
    }

    @Test
    public void testMaintenanceThrottlingAfterSuccessfulMaintenance() {
        String hostname = "node-123.us-north-3.test.yahoo.com";
        ContainerName containerName = ContainerName.fromHostname(hostname);
        ContainerNodeSpec nodeSpec = new ContainerNodeSpec.Builder()
                .hostname(hostname)
                .nodeState(Node.State.ready)
                .nodeType("tenants")
                .nodeFlavor("docker").build();

        when(docker.executeInContainerAsRoot(any(), anyVararg())).thenReturn(new ProcessResult(0, "", ""));
        storageMaintainer.removeOldFilesFromNode(containerName);
        verify(docker, times(1)).executeInContainerAsRoot(any(), anyVararg());
        // Will not actually run maintenance job until an hour passes
        storageMaintainer.removeOldFilesFromNode(containerName);
        verify(docker, times(1)).executeInContainerAsRoot(any(), anyVararg());

        // Coredump handler has its own throttler
        storageMaintainer.handleCoreDumpsForContainer(containerName, nodeSpec, environment);
        verify(docker, times(2)).executeInContainerAsRoot(any(), anyVararg());


        clock.advance(Duration.ofMinutes(61));
        storageMaintainer.removeOldFilesFromNode(containerName);
        verify(docker, times(3)).executeInContainerAsRoot(any(), anyVararg());

        storageMaintainer.handleCoreDumpsForContainer(containerName, nodeSpec, environment);
        verify(docker, times(4)).executeInContainerAsRoot(any(), anyVararg());

        storageMaintainer.handleCoreDumpsForContainer(containerName, nodeSpec, environment);
        verify(docker, times(4)).executeInContainerAsRoot(any(), anyVararg());


        // archiveNodeData is unthrottled and it should reset previous times
        storageMaintainer.archiveNodeData(containerName);
        verify(docker, times(5)).executeInContainerAsRoot(any(), anyVararg());
        storageMaintainer.archiveNodeData(containerName);
        verify(docker, times(6)).executeInContainerAsRoot(any(), anyVararg());

        storageMaintainer.handleCoreDumpsForContainer(containerName, nodeSpec, environment);
        verify(docker, times(7)).executeInContainerAsRoot(any(), anyVararg());
    }

    @Test
    public void testMaintenanceThrottlingAfterFailedMaintenance() {
        String hostname = "node-123.us-north-3.test.yahoo.com";
        ContainerName containerName = ContainerName.fromHostname(hostname);
        ContainerNodeSpec nodeSpec = new ContainerNodeSpec.Builder()
                .hostname(hostname)
                .nodeState(Node.State.ready)
                .nodeType("tenants")
                .nodeFlavor("docker").build();

        when(docker.executeInContainerAsRoot(any(), anyVararg()))
                .thenThrow(new RuntimeException("Something went wrong"))
                .thenReturn(new ProcessResult(0, "", ""));
        try {
            storageMaintainer.removeOldFilesFromNode(containerName);
            fail("Maintenance job should've failed!");
        } catch (RuntimeException ignored) { }
        verify(docker, times(1)).executeInContainerAsRoot(any(), anyVararg());

        // Maintenance job failed, we should be able to immediately re-run it
        storageMaintainer.removeOldFilesFromNode(containerName);
        verify(docker, times(2)).executeInContainerAsRoot(any(), anyVararg());
    }

    private static void writeNBytesToFile(File file, int nBytes) throws IOException {
        Files.write(file.toPath(), new byte[nBytes]);
    }
}
