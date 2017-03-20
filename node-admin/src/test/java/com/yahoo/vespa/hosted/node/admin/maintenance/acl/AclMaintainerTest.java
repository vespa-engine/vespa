package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.ContainerAclSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.integrationTests.CallOrderVerifier;
import com.yahoo.vespa.hosted.node.admin.integrationTests.NodeRepoMock;
import org.junit.Before;
import org.junit.Test;
import org.mockito.verification.VerificationMode;

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
import static org.mockito.Mockito.times;
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
    public void does_not_configure_acl_if_unchanged() {
        Container container = makeContainer("container-1");
        List<ContainerAclSpec> aclSpecs = makeAclSpecs(3, container.name);
        nodeRepository.addContainerAclSpecs(NODE_ADMIN_HOSTNAME, aclSpecs);
        // Run twice
        aclMaintainer.run();
        aclMaintainer.run();
        assertAclsApplied(container.name, aclSpecs, times(1));
    }

    @Test
    public void reconfigures_acl_when_container_pid_changes() {
        Container container = makeContainer("container-1");
        List<ContainerAclSpec> aclSpecs = makeAclSpecs(3, container.name);
        nodeRepository.addContainerAclSpecs(NODE_ADMIN_HOSTNAME, aclSpecs);

        aclMaintainer.run();
        assertAclsApplied(container.name, aclSpecs);

        // Container is restarted and PID changes
        makeContainer(container.name.asString(), Container.State.RUNNING, 43);
        aclMaintainer.run();

        assertAclsApplied(container.name, aclSpecs, times(2));
    }

    @Test
    public void does_not_configure_acl_for_stopped_container() {
        Container stoppedContainer = makeContainer("container-1", Container.State.EXITED, 0);
        List<ContainerAclSpec> aclSpecs = makeAclSpecs(1, stoppedContainer.name);
        nodeRepository.addContainerAclSpecs(NODE_ADMIN_HOSTNAME, aclSpecs);
        aclMaintainer.run();
        assertAclsApplied(stoppedContainer.name, aclSpecs, never());
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
        assertAclsApplied(containerName, containerAclSpecs, times(1));
    }

    private void assertAclsApplied(ContainerName containerName, List<ContainerAclSpec> containerAclSpecs,
                                   VerificationMode verificationMode) {
        verify(dockerOperations, verificationMode).executeCommandInNetworkNamespace(
                eq(containerName),
                aryEq(new String[]{"ip6tables", "-F", "INPUT"})
        );
        verify(dockerOperations, verificationMode).executeCommandInNetworkNamespace(
                eq(containerName),
                aryEq(new String[]{"ip6tables", "-P", "INPUT", "DROP"})
        );
        verify(dockerOperations, verificationMode).executeCommandInNetworkNamespace(
                eq(containerName),
                aryEq(new String[]{"ip6tables", "-P", "FORWARD", "DROP"})
        );
        verify(dockerOperations, verificationMode).executeCommandInNetworkNamespace(
                eq(containerName),
                aryEq(new String[]{"ip6tables", "-P", "OUTPUT", "ACCEPT"})
        );
        verify(dockerOperations, verificationMode).executeCommandInNetworkNamespace(
                eq(containerName),
                aryEq(new String[]{"ip6tables", "-A", "INPUT", "-m", "state", "--state", "RELATED,ESTABLISHED", "-j",
                        "ACCEPT"})
        );
        verify(dockerOperations, verificationMode).executeCommandInNetworkNamespace(
                eq(containerName),
                aryEq(new String[]{"ip6tables", "-A", "INPUT", "-i", "lo", "-j", "ACCEPT"})
        );
        verify(dockerOperations, verificationMode).executeCommandInNetworkNamespace(
                eq(containerName),
                aryEq(new String[]{"ip6tables", "-A", "INPUT", "-p", "ipv6-icmp", "-j", "ACCEPT"})
        );
        containerAclSpecs.forEach(aclSpec -> verify(dockerOperations, verificationMode).executeCommandInNetworkNamespace(
                eq(containerName),
                aryEq(new String[]{"ip6tables", "-A", "INPUT", "-s", aclSpec.ipAddress() + "/128", "-j", "ACCEPT"})
        ));
        verify(dockerOperations, verificationMode).executeCommandInNetworkNamespace(
                eq(containerName),
                aryEq(new String[]{"ip6tables", "-A", "INPUT", "-j", "REJECT"})
        );
    }

    private Container makeContainer(String hostname) {
        return makeContainer(hostname, Container.State.RUNNING, 42);
    }

    private Container makeContainer(String hostname, Container.State state, int pid) {
        final ContainerName containerName = new ContainerName(hostname);
        final Container container = new Container(hostname, new DockerImage("mock"),
                containerName, state, pid);
        when(dockerOperations.getContainer(eq(containerName))).thenReturn(Optional.of(container));
        return container;
    }

    private static List<ContainerAclSpec> makeAclSpecs(int count, ContainerName containerName) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> new ContainerAclSpec("node-" + i, "::" + i,
                        containerName))
                .collect(Collectors.toList());
    }

}
