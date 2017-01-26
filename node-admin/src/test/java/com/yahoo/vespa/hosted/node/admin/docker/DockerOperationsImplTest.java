// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class DockerOperationsImplTest {
    private final Environment environment = new Environment.Builder().build();
    private final Docker docker = mock(Docker.class);
    private final DockerOperationsImpl dockerOperations = new DockerOperationsImpl(docker, environment,
            new MetricReceiverWrapper(MetricReceiver.nullImplementation));

    @Test
    public void processResultFromNodeProgramWhenSuccess() throws Exception {
        final ContainerName containerName = new ContainerName("container-name");
        final ProcessResult actualResult = new ProcessResult(0, "output", "errors");
        final String programPath = "/bin/command";
        final String[] command = new String[] {programPath, "arg"};

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

    @Test(expected=RuntimeException.class)
    public void processResultFromNodeProgramWhenNonZeroExitCode() throws Exception {
        final ContainerName containerName = new ContainerName("container-name");
        final ProcessResult actualResult = new ProcessResult(3, "output", "errors");
        final String programPath = "/bin/command";
        final String[] command = new String[] {programPath, "arg"};

        when(docker.executeInContainerAsRoot(any(), anyVararg()))
                .thenReturn(actualResult); // output from node program

        dockerOperations.executeCommandInContainer(containerName, command);
    }

    @Test
    public void vespaVersionIsParsed() {
        assertThat(DockerOperationsImpl.parseVespaVersion("5.119.53"), CoreMatchers.is(Optional.of("5.119.53")));
    }

    @Test
    public void vespaVersionIsParsedWithSpacesAndNewlines() {
        assertThat(DockerOperationsImpl.parseVespaVersion("5.119.53\n"), CoreMatchers.is(Optional.of("5.119.53")));
        assertThat(DockerOperationsImpl.parseVespaVersion(" 5.119.53 \n"), CoreMatchers.is(Optional.of("5.119.53")));
        assertThat(DockerOperationsImpl.parseVespaVersion("\n 5.119.53 \n"), CoreMatchers.is(Optional.of("5.119.53")));
    }

    @Test
    public void vespaVersionIsParsedWithIrregularVersionScheme() {
        assertThat(DockerOperationsImpl.parseVespaVersion("7.2"), CoreMatchers.is(Optional.of("7.2")));
        assertThat(DockerOperationsImpl.parseVespaVersion("8.0-beta"), CoreMatchers.is(Optional.of("8.0-beta")));
        assertThat(DockerOperationsImpl.parseVespaVersion("foo"), CoreMatchers.is(Optional.of("foo")));
        assertThat(DockerOperationsImpl.parseVespaVersion("119"), CoreMatchers.is(Optional.of("119")));
    }

    @Test
    public void vespaVersionIsNotParsedFromNull() {
        assertThat(DockerOperationsImpl.parseVespaVersion(null), CoreMatchers.is(Optional.empty()));
    }

    @Test
    public void vespaVersionIsNotParsedFromEmptyString() {
        assertThat(DockerOperationsImpl.parseVespaVersion(""), CoreMatchers.is(Optional.empty()));
    }

    @Test
    public void vespaVersionIsNotParsedFromUnexpectedContent() {
        assertThat(DockerOperationsImpl.parseVespaVersion("No such command 'vespanodectl'"), CoreMatchers.is(Optional.empty()));
    }

    @Test
    public void runsCommandInNetworkNamespace() {
        ContainerName container = makeContainer("container-42", 42);
        List<String> capturedArgs = new ArrayList<>();
        DockerOperationsImpl dockerOperations = new DockerOperationsImpl(docker, environment,
                new MetricReceiverWrapper(MetricReceiver.nullImplementation), (args) -> {
            capturedArgs.addAll(args);
            return "";
        });

        dockerOperations.executeCommandInNetworkNamespace(container, new String[]{"iptables", "-nvL"});

        assertEquals(Arrays.asList(
                "sudo",
                "-n",
                "nsenter",
                "--net=/host/proc/42/ns/net",
                "--",
                "iptables",
                "-nvL"), capturedArgs);
    }

    private ContainerName makeContainer(String hostname, int pid) {
        ContainerName containerName = new ContainerName(hostname);
        Docker.ContainerInfo containerInfo = mock(Docker.ContainerInfo.class);
        when(containerInfo.getPid()).thenReturn(Optional.of(pid));
        when(docker.inspectContainer(eq(containerName))).thenReturn(Optional.of(containerInfo));
        return containerName;
    }
}
