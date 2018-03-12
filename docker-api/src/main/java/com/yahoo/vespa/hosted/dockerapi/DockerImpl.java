// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Network;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.async.ResultCallbackTemplate;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory;
import com.google.inject.Inject;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.dockerapi.metrics.CounterWrapper;
import com.yahoo.vespa.hosted.dockerapi.metrics.Dimensions;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;

import javax.annotation.concurrent.GuardedBy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import static com.yahoo.vespa.hosted.dockerapi.DockerNetworkCreator.NetworkAddressInterface;

public class DockerImpl implements Docker {
    private static final Logger logger = Logger.getLogger(DockerImpl.class.getName());

    public static final String DOCKER_CUSTOM_MACVLAN_NETWORK_NAME = "vespa-macvlan";
    static final String LABEL_NAME_MANAGEDBY = "com.yahoo.vespa.managedby";
    private static final String FRAMEWORK_CONTAINER_PREFIX = "/";

    private final DockerConfig config;
    private final Optional<DockerImageGarbageCollector> dockerImageGC;
    private final int secondsToWaitBeforeKilling;
    private CounterWrapper numberOfDockerDaemonFails;
    private boolean started = false;

    private final Object monitor = new Object();
    @GuardedBy("monitor")
    private final Set<DockerImage> scheduledPulls = new HashSet<>();

    private volatile Optional<DockerRegistryCredentialsSupplier> dockerRegistryCredentialsSupplier = Optional.empty();

    private DockerClient dockerClient;

    @Inject
    public DockerImpl(DockerConfig config, MetricReceiverWrapper metricReceiverWrapper) {
        this.config = config;

        secondsToWaitBeforeKilling = Optional.ofNullable(config)
                .map(DockerConfig::secondsToWaitBeforeKillingContainer)
                .orElse(10);

        dockerImageGC = Optional.ofNullable(config)
                .map(DockerConfig::imageGCMinTimeToLiveMinutes)
                .map(Duration::ofMinutes)
                .map(DockerImageGarbageCollector::new);

        Optional.ofNullable(metricReceiverWrapper).ifPresent(this::setMetrics);
    }

    // For testing
    DockerImpl(final DockerClient dockerClient) {
        this(null, null);
        this.dockerClient = dockerClient;
    }

    @Override
    public void start() {
        if (started) return;
        started = true;

        if (config != null) {
            dockerClient = createDockerClient(config);

            if (!config.networkNATed()) {
                try {
                    setupDockerNetworkIfNeeded();
                } catch (Exception e) {
                    throw new DockerException("Could not setup docker network", e);
                }
            }
        }
    }

    @Override
    public boolean networkNPTed() {
        return config.networkNATed();
    }

    private void setupDockerNetworkIfNeeded() throws IOException {
        if (!dockerClient.listNetworksCmd().withNameFilter(DOCKER_CUSTOM_MACVLAN_NETWORK_NAME).exec().isEmpty()) return;

        // Use IPv6 address if there is a mix of IP4 and IPv6 by taking the longest address.
        List<InetAddress> hostAddresses = Arrays.asList(InetAddress.getAllByName(com.yahoo.net.HostName.getLocalhost()));
        InetAddress hostAddress = Collections.max(hostAddresses,
                (o1, o2) -> o1.getAddress().length - o2.getAddress().length);

        NetworkAddressInterface networkAddressInterface = DockerNetworkCreator.getInterfaceForAddress(hostAddress);
        boolean isIPv6 = networkAddressInterface.interfaceAddress.getAddress() instanceof Inet6Address;

        Network.Ipam ipam = new Network.Ipam().withConfig(new Network.Ipam.Config()
                .withSubnet(hostAddress.getHostAddress() + "/" + networkAddressInterface.interfaceAddress.getNetworkPrefixLength())
                .withGateway(DockerNetworkCreator.getDefaultGatewayLinux(isIPv6).getHostAddress()));

        Map<String, String> dockerNetworkOptions = new HashMap<>();
        dockerNetworkOptions.put("parent", networkAddressInterface.networkInterface.getDisplayName());
        dockerNetworkOptions.put("macvlan_mode", "bridge");

        dockerClient.createNetworkCmd()
                .withName(DOCKER_CUSTOM_MACVLAN_NETWORK_NAME)
                .withDriver("macvlan")
                .withEnableIpv6(isIPv6)
                .withIpam(ipam)
                .withOptions(dockerNetworkOptions)
                .exec();
    }

    @Override
    public boolean pullImageAsyncIfNeeded(final DockerImage image) {
        try {
            synchronized (monitor) {
                if (scheduledPulls.contains(image)) return true;
                if (imageIsDownloaded(image)) return false;

                scheduledPulls.add(image);
                PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image.asString());

                dockerRegistryCredentialsSupplier
                        .flatMap(credentialsSupplier -> credentialsSupplier.getCredentials(image))
                        .map(credentials -> new AuthConfig()
                                .withRegistryAddress(credentials.registry.toString())
                                .withUsername(credentials.username)
                                .withPassword(credentials.password))
                        .ifPresent(pullImageCmd::withAuthConfig);

                logger.log(LogLevel.INFO, "Starting download of " + image.asString());

                pullImageCmd.exec(new ImagePullCallback(image));
                return true;
            }
        } catch (RuntimeException e) {
            numberOfDockerDaemonFails.add();
            throw new DockerException("Failed to pull image '" + image.asString() + "'", e);
        }
    }

    private void removeScheduledPoll(final DockerImage image) {
        synchronized (monitor) {
            scheduledPulls.remove(image);
        }
    }

    /**
     * Check if a given image is already in the local registry
     */
    boolean imageIsDownloaded(final DockerImage dockerImage) {
        return inspectImage(dockerImage).isPresent();
    }

    private Optional<InspectImageResponse> inspectImage(DockerImage dockerImage) {
        try {
            return Optional.of(dockerClient.inspectImageCmd(dockerImage.asString()).exec());
        } catch (NotFoundException e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            numberOfDockerDaemonFails.add();
            throw new DockerException("Failed to inspect image '" + dockerImage.asString() + "'", e);
        }
    }

    @Override
    public CreateContainerCommand createContainerCommand(DockerImage image, ContainerResources containerResources,
                                                         ContainerName name, String hostName) {
        return new CreateContainerCommandImpl(dockerClient, image, containerResources, name, hostName)
                .withPrivileged(config.runContainersInPrivileged());
    }

    @Override
    public void connectContainerToNetwork(ContainerName containerName, String networkName) {
        try {
            dockerClient.connectToNetworkCmd()
                    .withContainerId(containerName.asString())
                    .withNetworkId(networkName).exec();
        } catch (RuntimeException e) {
            numberOfDockerDaemonFails.add();
            throw new DockerException("Failed to connect container '" + containerName.asString() +
                    "' to network '" + networkName + "'", e);
        }
    }

    @Override
    public ProcessResult executeInContainer(ContainerName containerName, String... args) {
        return executeInContainerAsUser(containerName, getDefaults().vespaUser(), Optional.empty(), args);
    }

    @Override
    public ProcessResult executeInContainerAsRoot(ContainerName containerName, String... args) {
        return executeInContainerAsUser(containerName, "root", Optional.empty(), args);
    }

    @Override
    public ProcessResult executeInContainerAsRoot(ContainerName containerName, Long timeoutSeconds, String... args) {
        return executeInContainerAsUser(containerName, "root", Optional.of(timeoutSeconds), args);
    }

    /**
     * Execute command in container as user, "user" can be "username", "username:group", "uid" or "uid:gid"
     */
    private ProcessResult executeInContainerAsUser(ContainerName containerName, String user, Optional<Long> timeoutSeconds, String... command) {
        try {
            final ExecCreateCmdResponse response = dockerClient.execCreateCmd(containerName.asString())
                    .withCmd(command)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withUser(user)
                    .exec();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ByteArrayOutputStream errors = new ByteArrayOutputStream();
            ExecStartCmd execStartCmd = dockerClient.execStartCmd(response.getId());
            ExecStartResultCallback callback = execStartCmd.exec(new ExecStartResultCallback(output, errors));

            if (timeoutSeconds.isPresent()) {
                if (!callback.awaitCompletion(timeoutSeconds.get(), TimeUnit.SECONDS)) {
                    throw new DockerExecTimeoutException(String.format("Command '%s' did not finish within %s seconds.", command[0], timeoutSeconds));
                }
            } else {
                // Wait for completion no timeout
                callback.awaitCompletion();
            }

            final InspectExecResponse state = dockerClient.inspectExecCmd(execStartCmd.getExecId()).exec();
            assert !state.isRunning();
            Integer exitCode = state.getExitCode();
            assert exitCode != null;

            return new ProcessResult(exitCode, new String(output.toByteArray()), new String(errors.toByteArray()));
        } catch (RuntimeException | InterruptedException e) {
            numberOfDockerDaemonFails.add();
            throw new DockerException("Container '" + containerName.asString()
                    + "' failed to execute " + Arrays.toString(command), e);
        }
    }

    private Optional<InspectContainerResponse> inspectContainerCmd(String container) {
        try {
            return Optional.of(dockerClient.inspectContainerCmd(container).exec());
        } catch (NotFoundException ignored) {
            return Optional.empty();
        } catch (RuntimeException e) {
            numberOfDockerDaemonFails.add();
            throw new DockerException("Failed to get info for container '" + container + "'", e);
        }
    }

    @Override
    public Optional<ContainerStats> getContainerStats(ContainerName containerName) {
        try {
            DockerStatsCallback statsCallback = dockerClient.statsCmd(containerName.asString()).exec(new DockerStatsCallback());
            statsCallback.awaitCompletion(5, TimeUnit.SECONDS);

            return statsCallback.stats.map(stats -> new ContainerStatsImpl(
                    stats.getNetworks(), stats.getCpuStats(), stats.getMemoryStats(), stats.getBlkioStats()));
        } catch (NotFoundException ignored) {
            return Optional.empty();
        } catch (RuntimeException | InterruptedException e) {
            numberOfDockerDaemonFails.add();
            throw new DockerException("Failed to get stats for container '" + containerName.asString() + "'", e);
        }
    }

    @Override
    public void createContainer(CreateContainerCommand createContainerCommand) {
        try {
            dockerClient.execCreateCmd(createContainerCommand.toString());
        } catch (NotModifiedException ignored) {
            // If is already created, ignore
        } catch (RuntimeException e) {
            numberOfDockerDaemonFails.add();
            throw new DockerException("Failed to create container '" + createContainerCommand.toString() + "'", e);
        }
    }

    @Override
    public void startContainer(ContainerName containerName) {
        try {
            dockerClient.startContainerCmd(containerName.asString()).exec();
        } catch (NotModifiedException ignored) {
            // If is already started, ignore
        } catch (RuntimeException e) {
            numberOfDockerDaemonFails.add();
            throw new DockerException("Failed to start container '" + containerName.asString() + "'", e);
        }
    }

    @Override
    public void stopContainer(final ContainerName containerName) {
        try {
            dockerClient.stopContainerCmd(containerName.asString()).withTimeout(secondsToWaitBeforeKilling).exec();
        } catch (NotModifiedException ignored) {
            // If is already stopped, ignore
        } catch (RuntimeException e) {
            numberOfDockerDaemonFails.add();
            throw new DockerException("Failed to stop container '" + containerName.asString() + "'", e);
        }
    }

    @Override
    public void deleteContainer(ContainerName containerName) {
        try {
            dockerImageGC.ifPresent(imageGC -> {
                Optional<InspectContainerResponse> inspectResponse = inspectContainerCmd(containerName.asString());
                inspectResponse.ifPresent(response -> imageGC.updateLastUsedTimeFor(response.getImageId()));
            });

            dockerClient.removeContainerCmd(containerName.asString()).exec();
        } catch (NotFoundException ignored) {
            // If container doesn't exist ignore
        } catch (RuntimeException e) {
            numberOfDockerDaemonFails.add();
            throw new DockerException("Failed to delete container '" + containerName.asString() + "'", e);
        }
    }

    @Override
    public List<ContainerName> listAllContainersManagedBy(String manager) {
        return listAllContainers().stream()
                .filter(container -> isManagedBy(container, manager))
                .map(container -> new ContainerName(decode(container.getNames()[0])))
                .collect(Collectors.toList());
    }

    @Override
    public List<Container> getAllContainersManagedBy(String manager) {
        return listAllContainers().stream()
                .filter(container -> isManagedBy(container, manager))
                .map(com.github.dockerjava.api.model.Container::getId)
                .flatMap(this::asContainer)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Container> getContainer(ContainerName containerName) {
        return asContainer(containerName.asString()).findFirst();
    }

    @Override
    public String getGlobalIPv6Address(ContainerName name) {
        InspectContainerCmd cmd = dockerClient.inspectContainerCmd(name.asString());
        return cmd.exec().getNetworkSettings().getGlobalIPv6Address();
    }

    @Override
    public void setDockerRegistryCredentialsSupplier(DockerRegistryCredentialsSupplier dockerRegistryCredentialsSupplier) {
        this.dockerRegistryCredentialsSupplier = Optional.of(dockerRegistryCredentialsSupplier);
    }

    private Stream<Container> asContainer(String container) {
        return inspectContainerCmd(container)
                .map(response ->
                        new Container(
                                response.getConfig().getHostName(),
                                new DockerImage(response.getConfig().getImage()),
                                new ContainerResources(response.getHostConfig().getCpuShares(),
                                        response.getHostConfig().getMemory()),
                                new ContainerName(decode(response.getName())),
                                Container.State.valueOf(response.getState().getStatus().toUpperCase()),
                                response.getState().getPid()
                        ))
                .map(Stream::of)
                .orElse(Stream.empty());
    }

    private boolean isManagedBy(final com.github.dockerjava.api.model.Container container, String manager) {
        final Map<String, String> labels = container.getLabels();
        return labels != null && manager.equals(labels.get(LABEL_NAME_MANAGEDBY));
    }

    private String decode(String encodedContainerName) {
        return encodedContainerName.substring(FRAMEWORK_CONTAINER_PREFIX.length());
    }

    private List<com.github.dockerjava.api.model.Container> listAllContainers() {
        try {
            return dockerClient.listContainersCmd().withShowAll(true).exec();
        } catch (RuntimeException e) {
            numberOfDockerDaemonFails.add();
            throw new DockerException("Failed to list all containers", e);
        }
    }

    private List<Image> listAllImages() {
        try {
            return dockerClient.listImagesCmd().withShowAll(true).exec();
        } catch (RuntimeException e) {
            numberOfDockerDaemonFails.add();
            throw new DockerException("Failed to list all images", e);
        }
    }

    @Override
    public void deleteImage(final DockerImage dockerImage) {
        try {
            dockerClient.removeImageCmd(dockerImage.asString()).exec();
        } catch (NotFoundException ignored) {
            // Image was already deleted, ignore
        } catch (RuntimeException e) {
            numberOfDockerDaemonFails.add();
            throw new DockerException("Failed to delete docker image " + dockerImage.asString(), e);
        }
    }

    @Override
    public void deleteUnusedDockerImages() {
        if (!dockerImageGC.isPresent()) return;

        List<Image> images = listAllImages();
        List<com.github.dockerjava.api.model.Container> containers = listAllContainers();

        dockerImageGC.get().getUnusedDockerImages(images, containers).forEach(this::deleteImage);
    }

    private class ImagePullCallback extends PullImageResultCallback {
        private final DockerImage dockerImage;

        private ImagePullCallback(DockerImage dockerImage) {
            this.dockerImage = dockerImage;
        }

        @Override
        public void onError(Throwable throwable) {
            removeScheduledPoll(dockerImage);
            logger.log(LogLevel.ERROR, "Could not download image " + dockerImage.asString(), throwable);
        }


        @Override
        public void onComplete() {
            Optional<InspectImageResponse> image = inspectImage(dockerImage);
            if (image.isPresent()) { // Download successful, update image GC with the newly downloaded image
                logger.log(LogLevel.INFO, "Download completed: " + dockerImage.asString());
                dockerImageGC.ifPresent(imageGC -> imageGC.updateLastUsedTimeFor(image.get().getId()));
                removeScheduledPoll(dockerImage);
            } else {
                throw new DockerClientException("Could not download image: " + dockerImage);
            }
        }
    }

    // docker-java currently (3.0.8) does not support getting docker stats with stream=false, therefore we need
    // to subscribe to the stream and complete as soon we get the first result.
    private class DockerStatsCallback extends ResultCallbackTemplate<DockerStatsCallback, Statistics> {
        private Optional<Statistics> stats = Optional.empty();
        private final CountDownLatch completed = new CountDownLatch(1);

        @Override
        public void onNext(Statistics stats) {
            if (stats != null) {
                this.stats = Optional.of(stats);
                completed.countDown();
                onComplete();
            }
        }

        @Override
        public boolean awaitCompletion(long timeout, TimeUnit timeUnit) throws InterruptedException {
            // For some reason it takes as long to execute onComplete as the awaitCompletion timeout is, therefore
            // we have own awaitCompletion that completes as soon as we get the first result.
            return completed.await(timeout, timeUnit);
        }
    }

    private static DockerClient createDockerClient(DockerConfig config) {
        JerseyDockerCmdExecFactory dockerFactory = new JerseyDockerCmdExecFactory()
                .withMaxPerRouteConnections(config.maxPerRouteConnections())
                .withMaxTotalConnections(config.maxTotalConnections())
                .withConnectTimeout(config.connectTimeoutMillis())
                .withReadTimeout(config.readTimeoutMillis());

        DockerClientConfig dockerClientConfig = new DefaultDockerClientConfig.Builder()
                .withDockerHost(config.uri())
                .build();

        return DockerClientImpl.getInstance(dockerClientConfig)
                .withDockerCmdExecFactory(dockerFactory);
    }

    void setMetrics(MetricReceiverWrapper metricReceiver) {
        Dimensions dimensions = new Dimensions.Builder().add("role", "docker").build();
        numberOfDockerDaemonFails = metricReceiver.declareCounter(MetricReceiverWrapper.APPLICATION_DOCKER, dimensions, "daemon.api_fails");
    }
}
