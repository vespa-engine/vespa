// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.vespa.hosted.node.admin.docker.ContainerName;
import com.yahoo.vespa.hosted.node.admin.docker.Docker;
import com.yahoo.vespa.hosted.node.admin.docker.ProcessResult;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class DockerOperationsTest {
    private final Docker docker = mock(Docker.class);
    private final DockerOperations dockerOperations = new DockerOperations(docker);

    @Test
    public void absenceOfNodeProgramIsSuccess() throws Exception {
        final ContainerName containerName = new ContainerName("container-name");
        final String programPath = "/bin/command";

        when(docker.executeInContainer(any(), anyVararg())).thenReturn(new ProcessResult(3, "output", "errors"));

        Optional<ProcessResult> result = dockerOperations.executeOptionalProgram(
                containerName,
                programPath,
                "arg1",
                "arg2");

        String[] nodeProgramExistsCommand = dockerOperations.programExistsCommand(programPath);
        assertThat(nodeProgramExistsCommand.length, is(4));

        verify(docker, times(1)).executeInContainer(
                eq(containerName),
                // Mockito fails if we put the array here instead...
                eq(nodeProgramExistsCommand[0]),
                eq(nodeProgramExistsCommand[1]),
                eq(nodeProgramExistsCommand[2]),
                eq(nodeProgramExistsCommand[3]));
        assertThat(result.isPresent(), is(false));
    }

    @Test
    public void processResultFromNodeProgramWhenPresent() throws Exception {
        final ContainerName containerName = new ContainerName("container-name");
        final ProcessResult actualResult = new ProcessResult(3, "output", "errors");
        final String programPath = "/bin/command";
        final String[] command = new String[] {programPath, "arg"};

        when(docker.executeInContainer(any(), anyVararg()))
                .thenReturn(new ProcessResult(0, "", "")) // node program exists
                .thenReturn(actualResult); // output from node program

        Optional<ProcessResult> result = dockerOperations.executeOptionalProgram(
                containerName,
                command);

        String[] nodeProgramExistsCommand = dockerOperations.programExistsCommand(programPath);
        assertThat(nodeProgramExistsCommand.length, is(4));

        final InOrder inOrder = inOrder(docker);
        inOrder.verify(docker, times(1)).executeInContainer(
                eq(containerName),
                // Mockito fails if we put the array here instead...
                eq(nodeProgramExistsCommand[0]),
                eq(nodeProgramExistsCommand[1]),
                eq(nodeProgramExistsCommand[2]),
                eq(nodeProgramExistsCommand[3]));
        inOrder.verify(docker, times(1)).executeInContainer(
                eq(containerName),
                eq(command[0]),
                eq(command[1]));

        assertThat(result.isPresent(), is(true));
        assertThat(result.get(), is(actualResult));
    }
}