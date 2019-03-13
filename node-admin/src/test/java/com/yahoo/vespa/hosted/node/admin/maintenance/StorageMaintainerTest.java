// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.node.admin.component.ZoneId;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeMembership;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeOwner;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeState;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContextImpl;
import com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.hosted.node.admin.task.util.process.TestTerminal;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.yolean.Exceptions.uncheck;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * @author dybis
 */
@RunWith(Enclosed.class)
public class StorageMaintainerTest {
    private static final DockerOperations docker = mock(DockerOperations.class);

    public static class SecretAgentCheckTests {
        private final StorageMaintainer storageMaintainer = new StorageMaintainer(null, docker, null, null);

        @Test
        public void tenant() {
            Path path = executeAs(NodeType.tenant);

            assertChecks(path, "athenz-certificate-expiry", "host-life",
                    "system-coredumps-processing", "vespa", "vespa-health");

            // All dimensions for vespa metrics should be set by metricsproxy
            assertCheckEnds(path.resolve("vespa.yaml"),
                    "  args:\n" +
                    "    - all\n");

            // For non vespa metrics, we need to set all the dimensions ourselves
            assertCheckEnds(path.resolve("host-life.yaml"),
                    "tags:\n" +
                    "    namespace: Vespa\n" +
                    "    role: tenants\n" +
                    "    zone: prod.us-north-1\n" +
                    "    vespaVersion: 6.305.12\n" +
                    "    flavor: d-2-8-50\n" +
                    "    canonicalFlavor: d-2-8-50\n" +
                    "    state: active\n" +
                    "    parentHostname: host123.test.domain.tld\n" +
                    "    tenantName: tenant\n" +
                    "    app: application.instance\n" +
                    "    applicationName: application\n" +
                    "    instanceName: instance\n" +
                    "    applicationId: tenant.application.instance\n" +
                    "    clustertype: clusterType\n" +
                    "    clusterid: clusterId\n");
        }

        @Test
        public void proxy() {
            Path path = executeAs(NodeType.proxy);

            assertChecks(path, "athenz-certificate-expiry", "host-life", "routing-configage",
                    "ssl-status", "system-coredumps-processing", "vespa", "vespa-health");

            // All dimensions for vespa metrics should be set by the source
            assertCheckEnds(path.resolve("vespa.yaml"),
                    "  args:\n" +
                    "    - all\n");

            // For non vespa metrics, we need to set all the dimensions ourselves
            assertCheckEnds(path.resolve("host-life.yaml"),
                    "tags:\n" +
                    "    namespace: Vespa\n" +
                    "    role: routing\n" +
                    "    zone: prod.us-north-1\n" +
                    "    vespaVersion: 6.305.12\n" +
                    "    flavor: d-2-8-50\n" +
                    "    canonicalFlavor: d-2-8-50\n" +
                    "    state: active\n" +
                    "    parentHostname: host123.test.domain.tld\n" +
                    "    tenantName: tenant\n" +
                    "    app: application.instance\n" +
                    "    applicationName: application\n" +
                    "    instanceName: instance\n" +
                    "    applicationId: tenant.application.instance\n" +
                    "    clustertype: clusterType\n" +
                    "    clusterid: clusterId\n");
        }

        @Test
        public void configserver() {
            Path path = executeAs(NodeType.config);

            assertChecks(path, "athenz-certificate-expiry", "configserver", "configserver-logd", "host-life",
                         "system-coredumps-processing", "zkbackupage");

            assertCheckEnds(path.resolve("configserver.yaml"),
                    "  tags:\n" +
                    "    namespace: Vespa\n" +
                    "    role: configserver\n" +
                    "    zone: prod.us-north-1\n" +
                    "    vespaVersion: 6.305.12\n");
        }

        @Test
        public void controller() {
            Path path = executeAs(NodeType.controller);

            assertChecks(path, "athenz-certificate-expiry", "controller", "controller-logd", "host-life",
                         "system-coredumps-processing", "vespa", "vespa-health", "zkbackupage");


            // Do not set namespace for vespa metrics. WHY?
            assertCheckEnds(path.resolve("vespa.yaml"),
                    "  tags:\n" +
                    "    role: controller\n" +
                    "    zone: prod.us-north-1\n" +
                    "    vespaVersion: 6.305.12\n");

            assertCheckEnds(path.resolve("controller.yaml"),
                    "  tags:\n" +
                    "    namespace: Vespa\n" +
                    "    role: controller\n" +
                    "    zone: prod.us-north-1\n" +
                    "    vespaVersion: 6.305.12\n");
        }

        private Path executeAs(NodeType nodeType) {
            NodeSpec nodeSpec = new NodeSpec.Builder()
                    .hostname("host123-5.test.domain.tld")
                    .nodeType(nodeType)
                    .state(NodeState.active)
                    .parentHostname("host123.test.domain.tld")
                    .owner(new NodeOwner("tenant", "application", "instance"))
                    .membership(new NodeMembership("clusterType", "clusterId", null, 0, false))
                    .vespaVersion(Version.fromString("6.305.12"))
                    .flavor("d-2-8-50")
                    .canonicalFlavor("d-2-8-50")
                    .build();
            NodeAgentContext context = new NodeAgentContextImpl.Builder(nodeSpec)
                    .fileSystem(TestFileSystem.create())
                    .zoneId(new ZoneId(SystemName.dev, Environment.prod, RegionName.from("us-north-1"))).build();
            Path path = context.pathOnHostFromPathInNode("/etc/yamas-agent");
            uncheck(() -> Files.createDirectories(path));
            storageMaintainer.writeMetricsConfig(context);
            return path;
        }

        private void assertCheckEnds(Path checkPath, String contentsEnd) {
            String contents = new UnixPath(checkPath).readUtf8File();
            assertTrue(contents, contents.endsWith(contentsEnd));
        }

        private void assertChecks(Path checksPath, String... checkNames) {
            List<String> expectedChecks = Stream.of(checkNames).sorted().collect(Collectors.toList());
            List<String> actualChecks = FileFinder.files(checksPath).stream()
                    .map(FileFinder.FileAttributes::filename)
                    .map(filename -> filename.replaceAll("\\.yaml$", ""))
                    .sorted()
                    .collect(Collectors.toList());
            assertEquals(expectedChecks, actualChecks);
        }
    }

    public static class DiskUsageTests {

        private final TestTerminal terminal = new TestTerminal();

        @Test
        public void testDiskUsed() throws IOException {
            StorageMaintainer storageMaintainer = new StorageMaintainer(terminal, docker, null, null);
            FileSystem fileSystem = TestFileSystem.create();
            NodeAgentContext context = new NodeAgentContextImpl.Builder("host-1.domain.tld").fileSystem(fileSystem).build();
            Files.createDirectories(context.pathOnHostFromPathInNode("/"));

            terminal.expectCommand("du -xsk /home/docker/host-1 2>&1", 0, "321\t/home/docker/host-1/");
            assertEquals(Optional.of(328_704L), storageMaintainer.getDiskUsageFor(context));

            // Value should still be cached, no new execution against the terminal
            assertEquals(Optional.of(328_704L), storageMaintainer.getDiskUsageFor(context));
        }

        @Test
        public void testNonExistingDiskUsed() {
            StorageMaintainer storageMaintainer = new StorageMaintainer(terminal, docker, null, null);
            long usedBytes = storageMaintainer.getDiskUsedInBytes(null, Paths.get("/fake/path"));
            assertEquals(0L, usedBytes);
        }

        @After
        public void after() {
            terminal.verifyAllCommandsExecuted();
        }
    }

    public static class ArchiveContainerDataTests {
        @Test
        public void archive_container_data_test() throws IOException {
            // Create some files in containers
            FileSystem fileSystem = TestFileSystem.create();
            NodeAgentContext context1 = createNodeAgentContextAndContainerStorage(fileSystem, "container-1");
            createNodeAgentContextAndContainerStorage(fileSystem, "container-2");

            Path pathToArchiveDir = fileSystem.getPath("/home/docker/container-archive");
            Files.createDirectories(pathToArchiveDir);

            Path containerStorageRoot = context1.pathOnHostFromPathInNode("/").getParent();
            Set<String> containerStorageRootContentsBeforeArchive = FileFinder.from(containerStorageRoot)
                    .maxDepth(1)
                    .stream()
                    .map(FileFinder.FileAttributes::filename)
                    .collect(Collectors.toSet());
            assertEquals(ImmutableSet.of("container-archive", "container-1", "container-2"), containerStorageRootContentsBeforeArchive);


            // Archive container-1
            StorageMaintainer storageMaintainer = new StorageMaintainer(null, docker, null, pathToArchiveDir);
            storageMaintainer.archiveNodeStorage(context1);

            // container-1 should be gone from container-storage
            Set<String> containerStorageRootContentsAfterArchive = FileFinder.from(containerStorageRoot)
                    .maxDepth(1)
                    .stream()
                    .map(FileFinder.FileAttributes::filename)
                    .collect(Collectors.toSet());
            assertEquals(ImmutableSet.of("container-archive", "container-2"), containerStorageRootContentsAfterArchive);

            // container archive directory should contain exactly 1 directory - the one we just archived
            List<FileFinder.FileAttributes> containerArchiveContentsAfterArchive = FileFinder.from(pathToArchiveDir).maxDepth(1).list();
            assertEquals(1, containerArchiveContentsAfterArchive.size());
            Path archivedContainerStoragePath = containerArchiveContentsAfterArchive.get(0).path();
            assertTrue(archivedContainerStoragePath.getFileName().toString().matches("container-1_[0-9]{14}"));
            Set<String> archivedContainerStorageContents = FileFinder.files(archivedContainerStoragePath)
                    .stream()
                    .map(fileAttributes -> archivedContainerStoragePath.relativize(fileAttributes.path()).toString())
                    .collect(Collectors.toSet());
            assertEquals(ImmutableSet.of("opt/vespa/logs/vespa/vespa.log", "opt/vespa/logs/vespa/zookeeper.log"), archivedContainerStorageContents);
        }

        private NodeAgentContext createNodeAgentContextAndContainerStorage(FileSystem fileSystem, String containerName) throws IOException {
            NodeAgentContext context = new NodeAgentContextImpl.Builder(containerName + ".domain.tld")
                    .fileSystem(fileSystem).build();

            Path containerVespaHomeOnHost = context.pathOnHostFromPathInNode(context.pathInNodeUnderVespaHome(""));
            Files.createDirectories(context.pathOnHostFromPathInNode("/etc/something"));
            Files.createFile(context.pathOnHostFromPathInNode("/etc/something/conf"));

            Files.createDirectories(containerVespaHomeOnHost.resolve("logs/vespa"));
            Files.createFile(containerVespaHomeOnHost.resolve("logs/vespa/vespa.log"));
            Files.createFile(containerVespaHomeOnHost.resolve("logs/vespa/zookeeper.log"));

            Files.createDirectories(containerVespaHomeOnHost.resolve("var/db"));
            Files.createFile(containerVespaHomeOnHost.resolve("var/db/some-file"));

            Path containerRootOnHost = context.pathOnHostFromPathInNode("/");
            Set<String> actualContents = FileFinder.files(containerRootOnHost)
                    .stream()
                    .map(fileAttributes -> containerRootOnHost.relativize(fileAttributes.path()).toString())
                    .collect(Collectors.toSet());
            Set<String> expectedContents = new HashSet<>(Arrays.asList(
                    "etc/something/conf",
                    "opt/vespa/logs/vespa/vespa.log",
                    "opt/vespa/logs/vespa/zookeeper.log",
                    "opt/vespa/var/db/some-file"));
            assertEquals(expectedContents, actualContents);
            return context;
        }
    }
}
