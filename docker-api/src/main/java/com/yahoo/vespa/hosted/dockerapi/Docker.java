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
        CreateContainerCommand withVolume(String path, String volumePath);
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

    interface ContainerStats {
        Map<String, Object> getNetworks();
        Map<String, Object> getCpuStats();
        Map<String, Object> getMemoryStats();
        Map<String, Object> getBlkioStats();
    }

    default boolean networkNPTed() {
        return false;
    }

    Optional<ContainerStats> getContainerStats(ContainerName containerName);

    void createContainer(CreateContainerCommand createContainerCommand);

    void startContainer(ContainerName containerName);

    void stopContainer(ContainerName containerName);

    void deleteContainer(ContainerName containerName);

    void connectContainerToNetwork(ContainerName containerName, String networkName);

    List<Container> getAllContainersManagedBy(String manager);

    List<ContainerName> listAllContainersManagedBy(String manager);

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

    String getGlobalIPv6Address(ContainerName name);

    /**
     * If set, the supplier will we called every time before a pull/push request is made to get the credentials
     */
    void setDockerRegistryCredentialsSupplier(DockerRegistryCredentialsSupplier dockerRegistryCredentialsSupplier);

}
