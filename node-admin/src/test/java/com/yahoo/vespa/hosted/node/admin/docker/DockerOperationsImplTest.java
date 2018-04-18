// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.yahoo.collections.Pair;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.config.ConfigServerConfig;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPAddressesMock;
import org.junit.Test;
import org.mockito.InOrder;

import java.io.IOException;
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class DockerOperationsImplTest {
    private final Environment environment = new Environment.Builder()
            .configServerConfig(new ConfigServerConfig(new ConfigServerConfig.Builder()))
            .region("us-east-1")
            .environment("prod")
            .system("main")
            .cloud("mycloud")
            .build();
    private final Docker docker = mock(Docker.class);
    private final ProcessExecuter processExecuter = mock(ProcessExecuter.class);
    private final IPAddressesMock addressesMock = new IPAddressesMock();
    private final DockerOperationsImpl dockerOperations
            = new DockerOperationsImpl(docker, environment, processExecuter, addressesMock);

    @Test
    public void processResultFromNodeProgramWhenSuccess() throws Exception {
        final ContainerName containerName = new ContainerName("container-name");
        final ProcessResult actualResult = new ProcessResult(0, "output", "errors");
        final String programPath = "/bin/command";
        final String[] command = new String[]{programPath, "arg"};

        when(docker.executeInContainerAsRoot(any(), anyVararg()))
                .thenReturn(actualResult); // output from node program

        ProcessResult result = dockerOperations.executeCommandInContainer(containerName, command);

        final InOrder inOrder = inOrder(docker);
        inOrder.verify(docker, times(1)).executeInContainerAsRoot(
                eq(containerName),
                eq(command[0]),
                eq(command[1]));

        assertThat(result, is(actualResult));
    }

    @Test(expected = RuntimeException.class)
    public void processResultFromNodeProgramWhenNonZeroExitCode() {
        final ContainerName containerName = new ContainerName("container-name");
        final ProcessResult actualResult = new ProcessResult(3, "output", "errors");
        final String programPath = "/bin/command";
        final String[] command = new String[]{programPath, "arg"};

        when(docker.executeInContainerAsRoot(any(), anyVararg()))
                .thenReturn(actualResult); // output from node program

        dockerOperations.executeCommandInContainer(containerName, command);
    }

    @Test
    public void runsCommandInNetworkNamespace() {
        Container container = makeContainer("container-42", Container.State.RUNNING, 42);

        try {
            when(processExecuter.exec(aryEq(new String[]{"sudo", "nsenter", "--net=/host/proc/42/ns/net", "--", "iptables", "-nvL"})))
                    .thenReturn(new Pair<>(0, ""));
        } catch (IOException e) {
            e.printStackTrace();
        }

        dockerOperations.executeCommandInNetworkNamespace(container.name, "iptables", "-nvL");
    }

    private Container makeContainer(String name, Container.State state, int pid) {
        final Container container = new Container(name + ".fqdn", new DockerImage("mock"), null,
                new ContainerName(name), state, pid);
        when(docker.getContainer(eq(container.name))).thenReturn(Optional.of(container));
        return container;
    }
}
