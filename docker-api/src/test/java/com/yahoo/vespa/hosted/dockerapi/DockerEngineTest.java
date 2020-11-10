// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectExecCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.dockerapi.metrics.DimensionMetrics;
import com.yahoo.vespa.hosted.dockerapi.metrics.Metrics;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tony Vaagenes
 */
public class DockerEngineTest {

    private final DockerClient dockerClient = mock(DockerClient.class);
    private final Metrics metrics = new Metrics();
    private final ManualClock clock = new ManualClock();
    private final DockerEngine docker = new DockerEngine(dockerClient, metrics, clock);

    @Test
    public void testExecuteCompletes() {
        final String containerId = "container-id";
        final String[] command = new String[] {"/bin/ls", "-l"};
        final String execId = "exec-id";
        final int exitCode = 3;

        final ExecCreateCmdResponse response = mock(ExecCreateCmdResponse.class);
        when(response.getId()).thenReturn(execId);

        final ExecCreateCmd execCreateCmd = mock(ExecCreateCmd.class);
        when(dockerClient.execCreateCmd(any(String.class))).thenReturn(execCreateCmd);
        when(execCreateCmd.withCmd(ArgumentMatchers.<String>any())).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStdout(any(Boolean.class))).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStderr(any(Boolean.class))).thenReturn(execCreateCmd);
        when(execCreateCmd.withUser(any(String.class))).thenReturn(execCreateCmd);
        when(execCreateCmd.exec()).thenReturn(response);

        final ExecStartCmd execStartCmd = mock(ExecStartCmd.class);
        when(dockerClient.execStartCmd(any(String.class))).thenReturn(execStartCmd);
        when(execStartCmd.exec(any(ExecStartResultCallback.class))).thenReturn(mock(ExecStartResultCallback.class));

        final InspectExecCmd inspectExecCmd = mock(InspectExecCmd.class);
        final InspectExecResponse state = mock(InspectExecResponse.class);
        when(dockerClient.inspectExecCmd(any(String.class))).thenReturn(inspectExecCmd);
        when(inspectExecCmd.exec()).thenReturn(state);
        when(state.isRunning()).thenReturn(false);
        when(state.getExitCode()).thenReturn(exitCode);

        final ProcessResult result = docker.executeInContainerAsUser(
                new ContainerName(containerId), "root", OptionalLong.empty(), command);
        assertEquals(exitCode, result.getExitStatus());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void pullImageAsyncIfNeededSuccessfully() {
        final DockerImage image = DockerImage.fromString("registry.example.com/test:1.2.3");

        InspectImageResponse inspectImageResponse = mock(InspectImageResponse.class);
        when(inspectImageResponse.getId()).thenReturn(image.asString());

        InspectImageCmd imageInspectCmd = mock(InspectImageCmd.class);
        when(imageInspectCmd.exec())
                .thenThrow(new NotFoundException("Image not found"))
                .thenReturn(inspectImageResponse);

        ArgumentCaptor<ResultCallback> resultCallback = ArgumentCaptor.forClass(ResultCallback.class);
        PullImageCmd pullImageCmd = mock(PullImageCmd.class);
        when(pullImageCmd.exec(resultCallback.capture())).thenReturn(null);

        when(dockerClient.inspectImageCmd(image.asString())).thenReturn(imageInspectCmd);
        when(dockerClient.pullImageCmd(eq(image.asString()))).thenReturn(pullImageCmd);

        assertTrue("Should return true, we just scheduled the pull", docker.pullImageAsyncIfNeeded(image, RegistryCredentials.none));
        assertTrue("Should return true, the pull is still ongoing", docker.pullImageAsyncIfNeeded(image, RegistryCredentials.none));

        assertTrue(docker.imageIsDownloaded(image));
        clock.advance(Duration.ofMinutes(10));
        resultCallback.getValue().onComplete();
        assertPullDuration(Duration.ofMinutes(10), image.asString());
        assertFalse(docker.pullImageAsyncIfNeeded(image, RegistryCredentials.none));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void pullImageAsyncIfNeededWithError() {
        final DockerImage image = DockerImage.fromString("registry.example.com/test:1.2.3");

        InspectImageCmd imageInspectCmd = mock(InspectImageCmd.class);
        when(imageInspectCmd.exec()).thenThrow(new NotFoundException("Image not found"));

        ArgumentCaptor<ResultCallback> resultCallback = ArgumentCaptor.forClass(ResultCallback.class);
        PullImageCmd pullImageCmd = mock(PullImageCmd.class);
        when(pullImageCmd.exec(resultCallback.capture())).thenReturn(null);

        when(dockerClient.inspectImageCmd(image.asString())).thenReturn(imageInspectCmd);
        when(dockerClient.pullImageCmd(eq(image.asString()))).thenReturn(pullImageCmd);

        assertTrue("Should return true, we just scheduled the pull", docker.pullImageAsyncIfNeeded(image, RegistryCredentials.none));
        assertTrue("Should return true, the pull is still ongoing", docker.pullImageAsyncIfNeeded(image, RegistryCredentials.none));

        try {
            resultCallback.getValue().onComplete();
        } catch (Exception ignored) { }

        assertFalse(docker.imageIsDownloaded(image));
        assertTrue("Should return true, new pull scheduled", docker.pullImageAsyncIfNeeded(image, RegistryCredentials.none));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void pullImageAsyncRescheduleOnCredentialsChange() {
        DockerImage image = DockerImage.fromString("registry.example.com/test:1.2.3");

        InspectImageResponse inspectImageResponse = mock(InspectImageResponse.class);
        when(inspectImageResponse.getId()).thenReturn(image.asString());

        InspectImageCmd imageInspectCmd = mock(InspectImageCmd.class);
        when(imageInspectCmd.exec())
                .thenThrow(new NotFoundException("Image not found"))
                .thenThrow(new NotFoundException("Unauthorized"))
                .thenReturn(inspectImageResponse);
        when(dockerClient.inspectImageCmd(image.asString())).thenReturn(imageInspectCmd);

        ArgumentCaptor<ResultCallback> resultCallback = ArgumentCaptor.forClass(ResultCallback.class);
        PullImageCmd pullCmd = mock(PullImageCmd.class);
        when(pullCmd.exec(resultCallback.capture())).thenReturn(null);
        when(dockerClient.pullImageCmd(eq(image.asString()))).thenReturn(pullCmd);

        assertTrue("Pull triggered", docker.pullImageAsyncIfNeeded(image, RegistryCredentials.none));
        verify(pullCmd, times(1)).exec(any());

        assertTrue("Pull is already triggered", docker.pullImageAsyncIfNeeded(image, RegistryCredentials.none));
        verify(pullCmd, times(1)).exec(any());

        RegistryCredentials newCredentials = new RegistryCredentials("foo", "bar", "registry.example.com");
        when(pullCmd.withAuthConfig(any())).thenReturn(pullCmd);
        assertTrue("Credentials change and pull is re-triggered", docker.pullImageAsyncIfNeeded(image, newCredentials));
        AuthConfig authConfig = new AuthConfig().withUsername(newCredentials.username())
                                                .withPassword(newCredentials.password())
                                                .withRegistryAddress(newCredentials.registryAddress());
        verify(pullCmd, times(1)).withAuthConfig(eq(authConfig));

        resultCallback.getValue().onComplete();
        assertFalse("Image downloaded", docker.pullImageAsyncIfNeeded(image, newCredentials));
    }

    private void assertPullDuration(Duration duration, String image) {
        Optional<DimensionMetrics> byImage = metrics.getDefaultMetrics().stream()
                                                         .filter(metrics -> image.equals(metrics.getDimensions().asMap().get("image")))
                                                         .findFirst();
        assertTrue("Found metric for image=" + image, byImage.isPresent());
        Number durationInSecs = byImage.get().getMetrics().get("docker.imagePullDurationSecs");
        assertNotNull(durationInSecs);
        assertEquals(duration, Duration.ofSeconds(durationInSecs.longValue()));
    }

}
