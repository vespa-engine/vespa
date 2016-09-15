// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi.tool;

import com.github.dockerjava.api.command.DockerCmdExecFactory;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.RemoteApiVersion;
import com.github.dockerjava.core.command.PullImageResultCallback;

import com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory;

import io.airlift.airline.Arguments;
import io.airlift.airline.Cli;
import io.airlift.airline.Command;
import io.airlift.airline.Help;
import io.airlift.airline.ParseArgumentsUnexpectedException;
import io.airlift.airline.ParseOptionMissingException;

import java.lang.InterruptedException;
import java.lang.String;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Command to pull a Docker image from NodeAdmin. Needed due to issues with dependencies
 * in Node Admin where the command to pull images with docker-java does not work
 */
public class PullImageCommand {
    static final String dockerDaemonUriSeenFromNodeAdmin = "unix:///host/var/run/docker.sock";

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        Cli.CliBuilder<Runnable> builder = Cli.<Runnable>builder("pull-image")
                                              .withDescription("tool for Node Admin to pull a Docker image from a Docker repo")
                                              .withDefaultCommand(Help.class)
                                              .withCommands(PullImage.class);
        Cli<Runnable> gitParser = builder.build();
        try {
            gitParser.parse(args).run();
        } catch (ParseArgumentsUnexpectedException | ParseOptionMissingException e) {
            System.err.println(e.getMessage());
            gitParser.parse("help").run();
        }
    }

    @Command(name = "pull-image", description = "Pulls a Docker image")
    public static class PullImage implements Runnable {

        @Arguments(description = "Docker image to pull")
        public String image;

        public void run() {
            System.out.println("\nPulling " + image);
            final CompletableFuture<String> pullResult = pullImage(image);
            try {
                pullResult.get(30, TimeUnit.MINUTES);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                System.out.println("Failed to pull " + image + ": " + e);
                System.out.println(e.getMessage());
                System.exit(1);
            }
        }

        private CompletableFuture<String> pullImage(String image) {
            DockerClient dockerClient = createDockerClient();
            final CompletableFuture<String> completionListener = new CompletableFuture<>();
            dockerClient.pullImageCmd(image).exec(new ImagePullCallback(image, completionListener));
            return completionListener;
        }

        private class ImagePullCallback extends PullImageResultCallback {
            private final String dockerImage;
            private final CompletableFuture<String> completableFuture;

            private ImagePullCallback(String dockerImage, CompletableFuture<String> completableFuture) {
                this.dockerImage = dockerImage;
                this.completableFuture = completableFuture;
            }

            @Override
            public void onError(Throwable throwable) {
                System.out.println("Failed pulling " + dockerImage);
                completableFuture.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                System.out.println("Finished pulling " + dockerImage);
                completableFuture.complete(dockerImage);
            }
        }

        private DockerClient createDockerClient() {
            DockerCmdExecFactory dockerFactory = new JerseyDockerCmdExecFactory();
            RemoteApiVersion remoteApiVersion;
            DefaultDockerClientConfig.Builder dockerConfigBuilder = new DefaultDockerClientConfig.Builder()
                    .withDockerHost(dockerDaemonUriSeenFromNodeAdmin);
            DockerClientConfig dockerClientConfig;
            try {
                dockerClientConfig = dockerConfigBuilder.build();
                remoteApiVersion = RemoteApiVersion.parseConfig(DockerClientImpl.getInstance(dockerClientConfig)
                                                                                .withDockerCmdExecFactory(dockerFactory)
                                                                                .versionCmd()
                                                                                .exec()
                                                                                .getApiVersion());
                // From version 1.24 a field was removed which causes trouble with the current docker java code.
                // When this is fixed, we can remove this and do not specify version.
                if (remoteApiVersion.isGreaterOrEqual(RemoteApiVersion.VERSION_1_24)) {
                    remoteApiVersion = RemoteApiVersion.VERSION_1_23;
                }
            } catch (Exception e) {
                remoteApiVersion = RemoteApiVersion.VERSION_1_23;
            }

            return DockerClientImpl.getInstance(
                    dockerConfigBuilder.withApiVersion(remoteApiVersion).build())
                                   .withDockerCmdExecFactory(dockerFactory);
        }
    }

}
