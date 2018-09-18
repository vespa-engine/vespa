// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * API to simplify the com.github.dockerjava API for clients,
 * and to avoid OSGi exporting those classes.
 */
public interface Docker {
    /**
     * Must be called before any other method. May be called more than once.
     */
    void start();

    interface CreateContainerCommand {
        CreateContainerCommand withLabel(String name, String value);
        CreateContainerCommand withEnvironment(String name, String value);

        /**
         * Mounts a directory on host inside the docker container.
         *
         * <p>Bind mount content will be <b>private</b> to this container (and host) only.
         *
         * <p>When using this method and selinux is enabled (/usr/sbin/sestatus), starting
         * multiple containers which mount host's /foo directory into the container, will make
         * /foo's content visible/readable/writable only inside the container which was last
         * started and on the host. All the other containers will get "Permission denied".
         *
         * <p>Use {@link #withSharedVolume(String, String)} to mount a given host directory
         * into multiple containers.
         */
        CreateContainerCommand withVolume(String path, String volumePath);

        /**
         * Mounts a directory on host inside the docker container.
         *
         * <p>The bind mount content will be <b>shared</b> among multiple containers.
         *
         * @see #withVolume(String, String)
         */
        CreateContainerCommand withSharedVolume(String path, String volumePath);
        CreateContainerCommand withNetworkMode(String mode);
        CreateContainerCommand withIpAddress(InetAddress address);
        CreateContainerCommand withUlimit(String name, int softLimit, int hardLimit);
        CreateContainerCommand withEntrypoint(String... entrypoint);
        CreateContainerCommand withManagedBy(String manager);
        CreateContainerCommand withAddCapability(String capabilityName);
        CreateContainerCommand withDropCapability(String capabilityName);
        CreateContainerCommand withPrivileged(boolean privileged);

        void create();
    }

    CreateContainerCommand createContainerCommand(
            DockerImage dockerImage,
            ContainerResources containerResources,
            ContainerName containerName,
            String hostName);

    default boolean networkNATed() {
        return false;
    }

    Optional<ContainerStats> getContainerStats(ContainerName containerName);

    void createContainer(CreateContainerCommand createContainerCommand);

    void startContainer(ContainerName containerName);

    void stopContainer(ContainerName containerName);

    void deleteContainer(ContainerName containerName);

    void connectContainerToNetwork(ContainerName containerName, String networkName);

    List<Container> getAllContainersManagedBy(String manager);

    Optional<Container> getContainer(ContainerName containerName);

    /**
     * Checks if the image is currently being pulled or is already pulled, if not, starts an async
     * pull of the image
     *
     * @param image Docker image to pull
     * @return true iff image being pulled, false otherwise
     */
    boolean pullImageAsyncIfNeeded(DockerImage image);

    void deleteImage(DockerImage dockerImage);

    /**
     * Deletes the local images that are currently not in use by any container and not recently used.
     */
    void deleteUnusedDockerImages();

    /**
     * Execute a command in docker container as $VESPA_USER. Will block until the command is finished.
     *
     * @param containerName The name of the container
     * @param command The command with arguments to run.
     *
     * @return exitcodes, stdout and stderr in the ProcessResult
     */
    ProcessResult executeInContainer(ContainerName containerName, String... command);

    /**
     * Execute a command in docker container as "root". Will block until the command is finished.
     *
     * @param containerName The name of the container
     * @param command The command with arguments to run.
     *
     * @return exitcodes, stdout and stderr in the ProcessResult
     */
    ProcessResult executeInContainerAsRoot(ContainerName containerName, String... command);

    /**
     * Execute a command in docker container as "root"
     * The timeout will not kill the process spawned.
     *
     * @param containerName The name of the container
     * @param timeoutSeconds Timeout for the process to finish in seconds or without timeout if empty
     * @param command The command with arguments to run.
     *
     * @return exitcodes, stdout and stderr in the ProcessResult
     */
    ProcessResult executeInContainerAsRoot(ContainerName containerName, Long timeoutSeconds, String... command);
}
