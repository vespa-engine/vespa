package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.ContainerAclSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.integrationTests.CallOrderVerifier;
import com.yahoo.vespa.hosted.node.admin.integrationTests.NodeRepoMock;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.AclMaintainer;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AclMaintainerTest {

    private static final String NODE_ADMIN_HOSTNAME = "node-admin";

    private AclMaintainer aclMaintainer;
    private DockerOperations dockerOperations;
    private NodeRepoMock nodeRepository;

    @Before
    public void before() {
        this.dockerOperations = mock(DockerOperations.class);
        this.nodeRepository = new NodeRepoMock(new CallOrderVerifier());
        this.aclMaintainer = new AclMaintainer(dockerOperations, nodeRepository, () -> NODE_ADMIN_HOSTNAME);
    }

    @Test
    public void configures_container_acl() {
        Container container = makeContainer("container-1");
        List<ContainerAclSpec> aclSpecs = makeAclSpecs(3, container.name);
        nodeRepository.addContainerAclSpecs(NODE_ADMIN_HOSTNAME, aclSpecs);
        aclMaintainer.run();
        assertAclsApplied(container.name, aclSpecs);
    }

    @Test
    public void does_not_configure_acl_for_stopped_container() {
        Container stoppedContainer = makeContainer("container-1", false);
        nodeRepository.addContainerAclSpecs(NODE_ADMIN_HOSTNAME, makeAclSpecs(1, stoppedContainer.name));
        aclMaintainer.run();
        verify(dockerOperations, never()).executeCommandInNetworkNamespace(any(), any());
    }

    @Test
    public void rollback_is_attempted_when_applying_acl_fail() {
        Container container = makeContainer("container-1");
        nodeRepository.addContainerAclSpecs(NODE_ADMIN_HOSTNAME, makeAclSpecs(1, container.name));

        doThrow(new RuntimeException("iptables command failed"))
                .doNothing()
                .when(dockerOperations)
                .executeCommandInNetworkNamespace(any(), any());

        aclMaintainer.run();

        verify(dockerOperations).executeCommandInNetworkNamespace(
                eq(container.name),
                aryEq(new String[]{"ip6tables", "-P", "INPUT", "ACCEPT"})
        );
    }

    private void assertAclsApplied(ContainerName containerName, List<ContainerAclSpec> containerAclSpecs) {
        verify(dockerOperations).executeCommandInNetworkNamespace(
                eq(containerName),
                aryEq(new String[]{"ip6tables", "-F", "INPUT"})
        );
        verify(dockerOperations).executeCommandInNetworkNamespace(
                eq(containerName),
                aryEq(new String[]{"ip6tables", "-A", "INPUT", "-m", "state", "--state", "RELATED,ESTABLISHED", "-j",
                        "ACCEPT"})
        );
        verify(dockerOperations).executeCommandInNetworkNamespace(
                eq(containerName),
                aryEq(new String[]{"ip6tables", "-A", "INPUT", "-p", "icmpv6", "-j", "ACCEPT"})
        );
        containerAclSpecs.forEach(aclSpec -> verify(dockerOperations).executeCommandInNetworkNamespace(
                eq(containerName),
                aryEq(new String[]{"ip6tables", "-A", "INPUT", "-s", aclSpec.ipAddress(), "-j", "ACCEPT"})
        ));
        verify(dockerOperations).executeCommandInNetworkNamespace(
                eq(containerName),
                aryEq(new String[]{"ip6tables", "-P", "INPUT", "DROP"})
        );
    }

    private Container makeContainer(String hostname) {
        return makeContainer(hostname, true);
    }

    private Container makeContainer(String hostname, boolean running) {
        final Container container = new Container(hostname, new DockerImage("mock"),
                new ContainerName(hostname), running);
        when(dockerOperations.getContainer(eq(hostname))).thenReturn(Optional.of(container));
        return container;
    }

    private static List<ContainerAclSpec> makeAclSpecs(int count, ContainerName containerName) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> new ContainerAclSpec("node-" + i, "::" + i,
                        containerName.asString()))
                .collect(Collectors.toList());
    }

}
