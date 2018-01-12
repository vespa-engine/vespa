// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Network;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.RemoteApiVersion;
import com.github.dockerjava.core.async.ResultCallbackTemplate;
import com.github.dockerjava.core.command.BuildImageResultCallback;
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
import java.io.File;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
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

    private final int SECONDS_TO_WAIT_BEFORE_KILLING;
    private final boolean fallbackTo123OnErrors;
    private static final String FRAMEWORK_CONTAINER_PREFIX = "/";
    private final DockerConfig config;
    private final boolean inProduction;
    private Optional<DockerImageGarbageCollector> dockerImageGC = Optional.empty();
    private CounterWrapper numberOfDockerDaemonFails;
    private boolean started = false;

    private final Object monitor = new Object();
    @GuardedBy("monitor")
    private final Set<DockerImage> scheduledPulls = new HashSet<>();

    // Exposed for testing.
    DockerClient dockerClient;

    @Inject
    public DockerImpl(final DockerConfig config, MetricReceiverWrapper metricReceiver) {
        this(config,
                true, /* fallback to 1.23 on errors */
                metricReceiver,
                !config.isRunningLocally());
    }

    private DockerImpl(final DockerConfig config,
                       boolean fallbackTo123OnErrors,
                       MetricReceiverWrapper metricReceiverWrapper,
                       boolean inProduction) {
        this.config = config;
        this.fallbackTo123OnErrors = fallbackTo123OnErrors;
        this.inProduction = inProduction;
        if (config == null) {
            this.SECONDS_TO_WAIT_BEFORE_KILLING = 10;
        } else {
            SECONDS_TO_WAIT_BEFORE_KILLING = config.secondsToWaitBeforeKillingContainer();
        }
        if (metricReceiverWrapper != null) {
            setMetrics(metricReceiverWrapper);
        }
    }

    // For testing
    DockerImpl(final DockerClient dockerClient) {
        this(null, false, null, false);
        this.dockerClient = dockerClient;
    }

    // For testing
    DockerImpl(final DockerConfig config,
               boolean fallbackTo123OnErrors,
               MetricReceiverWrapper metricReceiverWrapper) {
        this(config, fallbackTo123OnErrors, metricReceiverWrapper, false);
    }

    @Override
    public void start() {
        if (started) return;
        started = true;

        if (config != null) {
            if (dockerClient == null) {
                dockerClient = initDockerConnection();
            }
            if (inProduction) {
                Duration minAgeToDelete = Duration.ofMinutes(config.imageGCMinTimeToLiveMinutes());
                dockerImageGC = Optional.of(new DockerImageGarbageCollector(minAgeToDelete));


                if (!config.networkNATted()) {
                    try {
                        setupDockerNetworkIfNeeded();
                    } catch (Exception e) {
                        throw new DockerException("Could not setup docker network", e);
                    }
                }
            }
        }
    }

    @Override
    public boolean networkNATted() {
        return config.networkNATted();
    }

    static DefaultDockerClientConfig.Builder buildDockerClientConfig(DockerConfig config) {
        DefaultDockerClientConfig.Builder dockerConfigBuilder = new DefaultDockerClientConfig.Builder()
                .withDockerHost(config.uri());

        if (URI.create(config.uri()).getScheme().equals("tcp") && !config.caCertPath().isEmpty()) {
            // In current version of docker-java (3.0.2), withDockerTlsVerify() only effect is when using it together
            // with withDockerCertPath(), where setting withDockerTlsVerify() must be set to true, otherwise the
            // cert path parameter will be ignored.
            // withDockerTlsVerify() has no effect when used with withCustomSslConfig()
            dockerConfigBuilder.withCustomSslConfig(new VespaSSLConfig(config));
        }

        return dockerConfigBuilder;
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
    public void copyArchiveToContainer(String sourcePath, ContainerName destinationContainer, String destinationPath) {
        try {
            dockerClient.copyArchiveToContainerCmd(destinationContainer.asString())
                    .withHostResource(sourcePath).withRemotePath(destinationPath).exec();
        } catch (RuntimeException e) {
            numberOfDockerDaemonFails.add();
            throw new DockerException("Failed to copy container " + sourcePath + " to " +
                    destinationContainer + ":" + destinationPath, e);
        }
    }

    @Override
    public boolean pullImageAsyncIfNeeded(final DockerImage image) {
        try {
            synchronized (monitor) {
                if (scheduledPulls.contains(image)) return true;
                if (imageIsDownloaded(image)) return false;

                scheduledPulls.add(image);
                dockerClient.pullImageCmd(image.asString()).exec(new ImagePullCallback(image));
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
        return new CreateContainerCommandImpl(dockerClient, image, containerResources, name, hostName);
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
            dockerClient.stopContainerCmd(containerName.asString()).withTimeout(SECONDS_TO_WAIT_BEFORE_KILLING).exec();
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
    public void buildImage(File dockerfile, DockerImage image) {
        try {
            dockerClient.buildImageCmd(dockerfile).withTags(Collections.singleton(image.asString()))
                    .exec(new BuildImageResultCallback()).awaitImageId();
        } catch (RuntimeException e) {
            numberOfDockerDaemonFails.add();
            throw new DockerException("Failed to build image " + image.asString(), e);
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
            throw new DockerClientException("Could not download image: " + dockerImage);
        }


        @Override
        public void onComplete() {
            Optional<InspectImageResponse> image = inspectImage(dockerImage);
            if (image.isPresent()) { // Download successful, update image GC with the newly downloaded image
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

    private DockerClient initDockerConnection() {
        JerseyDockerCmdExecFactory dockerFactory = new JerseyDockerCmdExecFactory()
                .withMaxPerRouteConnections(config.maxPerRouteConnections())
                .withMaxTotalConnections(config.maxTotalConnections())
                .withConnectTimeout(config.connectTimeoutMillis())
                .withReadTimeout(config.readTimeoutMillis());
        RemoteApiVersion remoteApiVersion;
        try {
            remoteApiVersion = RemoteApiVersion.parseConfig(DockerClientImpl.getInstance(
                    buildDockerClientConfig(config).build())
                    .withDockerCmdExecFactory(dockerFactory).versionCmd().exec().getApiVersion());
            logger.info("Found version of remote docker API: " + remoteApiVersion);
            // From version 1.24 a field was removed which causes trouble with the current docker java code.
            // When this is fixed, we can remove this and do not specify version.
            if (remoteApiVersion.isGreaterOrEqual(RemoteApiVersion.VERSION_1_24)) {
                remoteApiVersion = RemoteApiVersion.VERSION_1_23;
                logger.info("Found version 1.24 or newer of remote API, using 1.23.");
            }
        } catch (Exception e) {
            if (!fallbackTo123OnErrors) {
                throw e;
            }
            logger.log(LogLevel.ERROR, "Failed when trying to figure out remote API version of docker, using 1.23", e);
            remoteApiVersion = RemoteApiVersion.VERSION_1_23;
        }

        return DockerClientImpl.getInstance(
                buildDockerClientConfig(config)
                        .withApiVersion(remoteApiVersion)
                        .build())
                .withDockerCmdExecFactory(dockerFactory);
    }

    void setMetrics(MetricReceiverWrapper metricReceiver) {
        Dimensions dimensions = new Dimensions.Builder().add("role", "docker").build();
        numberOfDockerDaemonFails = metricReceiver.declareCounter(MetricReceiverWrapper.APPLICATION_DOCKER, dimensions, "daemon.api_fails");
    }
}
