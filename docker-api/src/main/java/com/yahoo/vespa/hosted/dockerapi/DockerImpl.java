// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.DockerException;
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
import com.yahoo.net.HostName;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.dockerapi.DockerNetworkCreator.NetworkAddressInterface;


public class DockerImpl implements Docker {
    private static final Logger logger = Logger.getLogger(DockerImpl.class.getName());

    public static final String DOCKER_CUSTOM_MACVLAN_NETWORK_NAME = "vespa-macvlan";
    static final String LABEL_NAME_MANAGEDBY = "com.yahoo.vespa.managedby";

    private final int SECONDS_TO_WAIT_BEFORE_KILLING;
    private static final String FRAMEWORK_CONTAINER_PREFIX = "/";
    private Optional<DockerImageGarbageCollector> dockerImageGC = Optional.empty();
    private CounterWrapper numberOfDockerDaemonFails;

    private final Object monitor = new Object();
    @GuardedBy("monitor")
    private final Map<DockerImage, CompletableFuture<DockerImage>> scheduledPulls = new HashMap<>();

    // Exposed for testing.
    final DockerClient dockerClient;

    // For testing
    DockerImpl(final DockerClient dockerClient) {
        this.dockerClient = dockerClient;
        this.SECONDS_TO_WAIT_BEFORE_KILLING = 10;
    }

    DockerImpl(
            final DockerConfig config,
            boolean fallbackTo123OnErrors,
            MetricReceiverWrapper metricReceiverWrapper) {
        SECONDS_TO_WAIT_BEFORE_KILLING = config.secondsToWaitBeforeKillingContainer();

        dockerClient = initDockerConnection(config, fallbackTo123OnErrors);
        setMetrics(metricReceiverWrapper);
    }

    @Inject
    public DockerImpl(final DockerConfig config, MetricReceiverWrapper metricReceiver) {
        this(
                config,
                true, /* fallback to 1.23 on errors */
                metricReceiver);

        if (! config.isRunningLocally()) {
            Duration minAgeToDelete = Duration.ofMinutes(config.imageGCMinTimeToLiveMinutes());
            dockerImageGC = Optional.of(new DockerImageGarbageCollector(minAgeToDelete));

            try {
                setupDockerNetworkIfNeeded();
            } catch (Exception e) {
                throw new RuntimeException("Could not setup docker network", e);
            }
        }
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
        if (! dockerClient.listNetworksCmd().withNameFilter(DOCKER_CUSTOM_MACVLAN_NETWORK_NAME).exec().isEmpty()) return;

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
        } catch (DockerException e) {
            numberOfDockerDaemonFails.add();
            throw new RuntimeException("Failed to copy container " + sourcePath + " to " +
                    destinationPath + ":" + destinationPath, e);
        }
    }

    @Override
    public CompletableFuture<DockerImage> pullImageAsync(final DockerImage image) {
        final CompletableFuture<DockerImage> completionListener;
        synchronized (monitor) {
            if (scheduledPulls.containsKey(image)) {
                return scheduledPulls.get(image);
            }
            completionListener = new CompletableFuture<>();
            scheduledPulls.put(image, completionListener);
        }

        try {
            dockerClient.pullImageCmd(image.asString()).exec(new ImagePullCallback(image));
        } catch (DockerException e) {
            numberOfDockerDaemonFails.add();
            throw new RuntimeException("Failed to pull image '" + image.asString() + "'", e);
        }

        return completionListener;
    }

    private CompletableFuture<DockerImage> removeScheduledPoll(final DockerImage image) {
        synchronized (monitor) {
            return scheduledPulls.remove(image);
        }
    }

    /**
     * Check if a given image is already in the local registry
     */
    @Override
    public boolean imageIsDownloaded(final DockerImage dockerImage) {
        return inspectImage(dockerImage).isPresent();
    }

    private Optional<Image> inspectImage(DockerImage dockerImage) {
        try {
            return dockerClient.listImagesCmd().withShowAll(true)
                    .withImageNameFilter(dockerImage.asString()).exec().stream().findFirst();
        } catch (DockerException e) {
            numberOfDockerDaemonFails.add();
            throw new RuntimeException("Failed to inspect image '" + dockerImage.asString() + "'", e);
        }
    }

    @Override
    public CreateContainerCommand createContainerCommand(DockerImage image, ContainerName name, String hostName) {
        return new CreateContainerCommandImpl(dockerClient, image, name, hostName);
    }

    @Override
    public void connectContainerToNetwork(ContainerName containerName, String networkName) {
        try {
            dockerClient.connectToNetworkCmd()
                    .withContainerId(containerName.asString())
                    .withNetworkId(networkName).exec();
        } catch (DockerException e) {
            numberOfDockerDaemonFails.add();
            throw new RuntimeException("Failed to connect container '" + containerName.asString() +
                    "' to network '" + networkName + "'", e);
        }
    }

    @Override
    public ProcessResult executeInContainer(ContainerName containerName, String... args) {
        assert args.length >= 1;
        try {
            final ExecCreateCmdResponse response = dockerClient.execCreateCmd(containerName.asString())
                    .withCmd(args)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ByteArrayOutputStream errors = new ByteArrayOutputStream();
            ExecStartCmd execStartCmd = dockerClient.execStartCmd(response.getId());
            execStartCmd.exec(new ExecStartResultCallback(output, errors)).awaitCompletion();

            final InspectExecResponse state = dockerClient.inspectExecCmd(execStartCmd.getExecId()).exec();
            assert !state.isRunning();
            Integer exitCode = state.getExitCode();
            assert exitCode != null;

            return new ProcessResult(exitCode, new String(output.toByteArray()), new String(errors.toByteArray()));
        } catch (DockerException | InterruptedException e) {
            numberOfDockerDaemonFails.add();
            throw new RuntimeException("Container '" + containerName.asString()
                    + "' failed to execute " + Arrays.toString(args), e);
        }
    }

    private Optional<InspectContainerResponse> inspectContainerCmd(String container) {
        try {
            return Optional.of(dockerClient.inspectContainerCmd(container).exec());
        } catch (NotFoundException ignored) {
            return Optional.empty();
        } catch (DockerException e) {
            numberOfDockerDaemonFails.add();
            throw new RuntimeException("Failed to get info for container '" + container + "'", e);
        }
    }

    @Override
    public Optional<ContainerInfo> inspectContainer(ContainerName containerName) {
        return inspectContainerCmd(containerName.asString())
                .map(response -> new ContainerInfoImpl(containerName, response));
    }

    @Override
    public Optional<ContainerStats> getContainerStats(ContainerName containerName) {
        try {
            DockerStatsCallback statsCallback = dockerClient.statsCmd(containerName.asString()).exec(new DockerStatsCallback());
            statsCallback.awaitCompletion(10, TimeUnit.SECONDS);

            return statsCallback.stats.map(stats -> new ContainerStatsImpl(
                    stats.getNetworks(), stats.getCpuStats(), stats.getMemoryStats(), stats.getBlkioStats()));
        } catch (NotFoundException ignored) {
            return Optional.empty();
        } catch (DockerException | InterruptedException e) {
            numberOfDockerDaemonFails.add();
            throw new RuntimeException("Failed to get stats for container '" + containerName.asString() + "'", e);
        }
    }

    @Override
    public void startContainer(ContainerName containerName) {
        try {
            dockerClient.startContainerCmd(containerName.asString()).exec();
        } catch (NotModifiedException ignored) {
            // If is already started, ignore
        } catch (DockerException e) {
            numberOfDockerDaemonFails.add();
            throw new RuntimeException("Failed to start container '" + containerName.asString() + "'", e);
        }
    }

    @Override
    public void stopContainer(final ContainerName containerName) {
        try {
            dockerClient.stopContainerCmd(containerName.asString()).withTimeout(SECONDS_TO_WAIT_BEFORE_KILLING).exec();
        } catch (NotModifiedException ignored) {
            // If is already stopped, ignore
        } catch (DockerException e) {
            numberOfDockerDaemonFails.add();
            throw new RuntimeException("Failed to stop container '" + containerName.asString() + "'", e);
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
        } catch (DockerException e) {
            numberOfDockerDaemonFails.add();
            throw new RuntimeException("Failed to delete container '" + containerName.asString() + "'", e);
        }
    }

    @Override
    public List<Container> getAllContainersManagedBy(String manager) {
        return listAllContainers().stream()
                .filter(container -> isManagedBy(container, manager))
                .flatMap(this::asContainer)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Container> getContainer(String hostname) {
        return listAllContainers().stream()
                .flatMap(this::asContainer)
                .filter(c -> Objects.equals(hostname, c.hostname))
                .findFirst();
    }

    private Stream<Container> asContainer(com.github.dockerjava.api.model.Container dockerClientContainer) {
        return inspectContainerCmd(dockerClientContainer.getId())
                .map(response ->
                        new Container(
                                response.getConfig().getHostName(),
                                new DockerImage(response.getConfig().getImage()),
                                new ContainerName(decode(response.getName())),
                                response.getState().getRunning()))
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
        } catch (DockerException e) {
            numberOfDockerDaemonFails.add();
            throw new RuntimeException("Failed to list all containers", e);
        }
    }

    private List<Image> listAllImages() {
        try {
            return dockerClient.listImagesCmd().withShowAll(true).exec();
        } catch (DockerException e) {
            numberOfDockerDaemonFails.add();
            throw new RuntimeException("Failed to list all images", e);
        }
    }

    @Override
    public void deleteImage(final DockerImage dockerImage) {
        try {
            dockerClient.removeImageCmd(dockerImage.asString()).exec();
        } catch (NotFoundException ignored) {
            // Image was already deleted, ignore
        } catch (DockerException e) {
            numberOfDockerDaemonFails.add();
            throw new RuntimeException("Failed to delete docker image " + dockerImage.asString(), e);
        }
    }

    @Override
    public void buildImage(File dockerfile, DockerImage image) {
        try {
            dockerClient.buildImageCmd(dockerfile).withTag(image.asString())
                    .exec(new BuildImageResultCallback()).awaitImageId();
        } catch (DockerException e) {
            numberOfDockerDaemonFails.add();
            throw new RuntimeException("Failed to build image " + image.asString(), e);
        }
    }

    @Override
    public void deleteUnusedDockerImages() {
        if (! dockerImageGC.isPresent()) return;

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
            removeScheduledPoll(dockerImage).completeExceptionally(throwable);
        }


        @Override
        public void onComplete() {
            Optional<Image> image = inspectImage(dockerImage);
            if (image.isPresent()) { // Download successful, update image GC with the newly downloaded image
                dockerImageGC.ifPresent(imageGC -> imageGC.updateLastUsedTimeFor(image.get().getId()));
                removeScheduledPoll(dockerImage).complete(dockerImage);
            } else {
                removeScheduledPoll(dockerImage).completeExceptionally(
                        new DockerClientException("Could not download image: " + dockerImage));
            }
        }
    }

    private class DockerStatsCallback extends ResultCallbackTemplate<DockerStatsCallback, Statistics> {
        private Optional<Statistics> stats = Optional.empty();

        @Override
        public void onNext(Statistics stats) {
            if (stats != null) {
                this.stats = Optional.of(stats);
                onComplete();
            }
        }
    }

    private DockerClient initDockerConnection(final DockerConfig config, boolean fallbackTo123orErrors) {
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
            logger.info("Found version of remote docker API: "+ remoteApiVersion);
            // From version 1.24 a field was removed which causes trouble with the current docker java code.
            // When this is fixed, we can remove this and do not specify version.
            if (remoteApiVersion.isGreaterOrEqual(RemoteApiVersion.VERSION_1_24)) {
                remoteApiVersion = RemoteApiVersion.VERSION_1_23;
                logger.info("Found version 1.24 or newer of remote API, using 1.23.");
            }
        } catch (Exception e) {
            if (! fallbackTo123orErrors) {
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

    private void setMetrics(MetricReceiverWrapper metricReceiver) {
        Dimensions dimensions = new Dimensions.Builder()
                .add("host", HostName.getLocalhost())
                .add("role", "docker").build();

        numberOfDockerDaemonFails = metricReceiver.declareCounter(dimensions, "daemon.api_fails");
    }
}
