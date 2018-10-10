package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.Test;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
public class NodeAgentContextImplTest {
    private final NodeAgentContext context = nodeAgentFromHostname("container-1.domain.tld");


    @Test
    public void path_on_host_from_path_in_node_test() {
        assertEquals(
                "/home/docker/container-1",
                context.pathOnHostFromPathInNode(Paths.get("/")).toString());

        assertEquals(
                "/home/docker/container-1/dev/null",
                context.pathOnHostFromPathInNode(Paths.get("/dev/null")).toString());
    }

    @Test(expected=IllegalArgumentException.class)
    public void path_in_container_must_be_absolute() {
        context.pathOnHostFromPathInNode(Paths.get("some/relative/path"));
    }

    @Test
    public void path_under_vespa_host_in_container_test() {
        assertEquals(
                "/opt/vespa",
                context.pathInNodeUnderVespaHome("").toString());

        assertEquals(
                "/opt/vespa/logs/vespa/vespa.log",
                context.pathInNodeUnderVespaHome("logs/vespa/vespa.log").toString());
    }

    @Test(expected=IllegalArgumentException.class)
    public void path_under_vespa_home_must_be_relative() {
        context.pathInNodeUnderVespaHome("/home");
    }


    public static NodeAgentContext nodeAgentFromHostname(String hostname) {
        FileSystem fileSystem = TestFileSystem.create();
        return nodeAgentFromHostname(fileSystem, hostname);
    }

    public static NodeAgentContext nodeAgentFromHostname(FileSystem fileSystem, String hostname) {
        final Path vespaHomeInContainer = Paths.get("/opt/vespa");
        final Path containerStoragePath = fileSystem.getPath("/home/docker");

        return new NodeAgentContextImpl(hostname, NodeType.tenant, new AthenzService("domain", "service"),
                containerStoragePath, vespaHomeInContainer);
    }
}
