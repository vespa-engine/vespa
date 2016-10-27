// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.DockerException;
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
import com.yahoo.vespa.hosted.dockerapi.metrics.GaugeWrapper;
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
import static com.yahoo.vespa.hosted.dockerapi.DockerNetworkCreator.NetworkAddressInterface;


public class DockerImpl implements Docker {
    private static final Logger logger = Logger.getLogger(DockerImpl.class.getName());

    public static final String LABEL_NAME_MANAGEDBY = "com.yahoo.vespa.managedby";
    public static final String LABEL_VALUE_MANAGEDBY = "node-admin";

    private final int SECONDS_TO_WAIT_BEFORE_KILLING;
    private static final String FRAMEWORK_CONTAINER_PREFIX = "/";

    public static final String DOCKER_CUSTOM_MACVLAN_NETWORK_NAME = "vespa-macvlan";

    public final Object monitor = new Object();
    @GuardedBy("monitor")
    private final Map<DockerImage, CompletableFuture<DockerImage>> scheduledPulls = new HashMap<>();

    // Exposed for testing.
    DockerClient dockerClient;

    private GaugeWrapper numberOfRunningContainersGauge;
    private CounterWrapper numberOfDockerDaemonFails;

    private Optional<DockerImageGarbageCollector> dockerImageGC = Optional.empty();

    // For testing
    DockerImpl(final DockerClient dockerClient) {
        this.dockerClient = dockerClient;
        this.SECONDS_TO_WAIT_BEFORE_KILLING = 10;
    }

    DockerImpl(
            final DockerConfig config,
            boolean fallbackTo123OnErrors,
            boolean trySetupNetwork,
            MetricReceiverWrapper metricReceiverWrapper) {
        if (config.imageGCEnabled()) {
            Duration minAgeToDelete = Duration.ofMinutes(config.imageGCMinTimeToLiveMinutes());
            dockerImageGC = Optional.of(new DockerImageGarbageCollector(minAgeToDelete));
        }

        SECONDS_TO_WAIT_BEFORE_KILLING = config.secondsToWaitBeforeKillingContainer();

        initDockerConnection(config, fallbackTo123OnErrors);
        setMetrics(metricReceiverWrapper);

        if (trySetupNetwork) {
            try {
                setupDockerNetworkIfNeeded();
            } catch (Exception e) {
                throw new RuntimeException("Could not setup docker network", e);
            }
        }
    }

    @Inject
    public DockerImpl(final DockerConfig config, MetricReceiverWrapper metricReceiver) {
        this(
                config,
                true, /* fallback to 1.23 on errors */
                true, /* try setup network */
                metricReceiver);
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

    private void setupDockerNetworkIfNeeded() throws IOException, InterruptedException {
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
        dockerClient.copyArchiveToContainerCmd(destinationContainer.asString())
                .withHostResource(sourcePath).withRemotePath(destinationPath).exec();
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

        dockerClient.pullImageCmd(image.asString()).exec(new ImagePullCallback(image));
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
            throw new RuntimeException("Failed to list image name: '" + dockerImage + "'", e);
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
            throw new RuntimeException("Failed to connect container to network", e);
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
            throw new RuntimeException("Container " + containerName.asString()
                    + " failed to execute " + Arrays.toString(args), e);
        }
    }

    @Override
    public ContainerInfo inspectContainer(ContainerName containerName) {
        try {
            InspectContainerResponse containerInfo = dockerClient.inspectContainerCmd(containerName.asString()).exec();
            return new ContainerInfoImpl(containerName, containerInfo);
        } catch (DockerException e) {
            numberOfDockerDaemonFails.add();
            throw new RuntimeException("Failed to get container info", e);
        }
    }

    public ContainerStats getContainerStats(ContainerName containerName) {
        try {
             DockerStatsCallback statsCallback = dockerClient.statsCmd(containerName.asString()).exec(new DockerStatsCallback());
             statsCallback.awaitCompletion(10, TimeUnit.SECONDS);

            return new ContainerStatsImpl(statsCallback.stats.getNetworks(), statsCallback.stats.getCpuStats(),
                    statsCallback.stats.getMemoryStats(), statsCallback.stats.getBlkioStats());
        } catch (DockerException | InterruptedException e) {
            numberOfDockerDaemonFails.add();
            throw new RuntimeException("Failed to get container stats", e);
        }
    }

    @Override
    public void startContainer(ContainerName containerName) {
        Optional<com.github.dockerjava.api.model.Container> dockerContainer = getContainerFromName(containerName);
        if (dockerContainer.isPresent()) {
            try {
                dockerClient.startContainerCmd(dockerContainer.get().getId()).exec();
                numberOfRunningContainersGauge.sample(getAllManagedContainers().size());
            } catch (DockerException e) {
                numberOfDockerDaemonFails.add();
                throw new RuntimeException("Failed to start container", e);
            }
        }
    }

    @Override
    public void stopContainer(final ContainerName containerName) {
        Optional<com.github.dockerjava.api.model.Container> dockerContainer = getContainerFromName(containerName);
        if (dockerContainer.isPresent()) {
            try {
                dockerClient.stopContainerCmd(dockerContainer.get().getId()).withTimeout(SECONDS_TO_WAIT_BEFORE_KILLING).exec();
            } catch (DockerException e) {
                numberOfDockerDaemonFails.add();
                throw new RuntimeException("Failed to stop container", e);
            }
        }
    }

    @Override
    public void deleteContainer(ContainerName containerName) {
        Optional<com.github.dockerjava.api.model.Container> dockerContainer = getContainerFromName(containerName);
        if (dockerContainer.isPresent()) {
            dockerImageGC.ifPresent(imageGC -> imageGC.updateLastUsedTimeFor(dockerContainer.get().getImageId()));

            try {
                dockerClient.removeContainerCmd(dockerContainer.get().getId()).exec();
                numberOfRunningContainersGauge.sample(getAllManagedContainers().size());
            } catch (DockerException e) {
                numberOfDockerDaemonFails.add();
                throw new RuntimeException("Failed to delete container", e);
            }
        }
    }

    List<Container> getAllContainers(boolean managedOnly) {
        try {
            return dockerClient.listContainersCmd().withShowAll(true).exec().stream()
                    .filter(container -> !managedOnly || isManaged(container))
                    .map(this::asContainer)
                    .collect(Collectors.toList());
        } catch (DockerException e) {
            numberOfDockerDaemonFails.add();
            throw new RuntimeException("Could not retrieve all containers", e);
        }
    }

    @Override
    public List<Container> getAllManagedContainers() {
        return getAllContainers(true);
    }

    @Override
    public Optional<Container> getContainer(String hostname) {
        return getAllContainers(false).stream()
                .filter(c -> Objects.equals(hostname, c.hostname))
                .findFirst();
    }

    private Container asContainer(com.github.dockerjava.api.model.Container dockerClientContainer) {
        try {
            final InspectContainerResponse response = dockerClient.inspectContainerCmd(dockerClientContainer.getId()).exec();
            return new Container(
                    response.getConfig().getHostName(),
                    new DockerImage(dockerClientContainer.getImage()),
                    new ContainerName(decode(response.getName())),
                    response.getState().getRunning());
        } catch (DockerException e) {
            numberOfDockerDaemonFails.add();
            //TODO: do proper exception handling
            throw new RuntimeException("Failed talking to docker daemon", e);
        }
    }


    private Optional<com.github.dockerjava.api.model.Container> getContainerFromName(final ContainerName containerName) {
        try {
            return dockerClient.listContainersCmd().withShowAll(true).exec().stream()
                    .filter(container -> matchName(container, containerName.asString()))
                    .findFirst();
        } catch (DockerException e) {
            throw new RuntimeException("Failed to get container from name", e);
        }
    }

    private boolean isManaged(final com.github.dockerjava.api.model.Container container) {
        final Map<String, String> labels = container.getLabels();
        if (labels == null) {
            return false;
        }

        return LABEL_VALUE_MANAGEDBY.equals(labels.get(LABEL_NAME_MANAGEDBY));
    }

    private boolean matchName(com.github.dockerjava.api.model.Container container, String targetName) {
        return Arrays.stream(container.getNames()).anyMatch(encodedName -> decode(encodedName).equals(targetName));
    }

    private String decode(String encodedContainerName) {
        return encodedContainerName.substring(FRAMEWORK_CONTAINER_PREFIX.length());
    }

    @Override
    public void deleteImage(final DockerImage dockerImage) {
        try {
            dockerClient.removeImageCmd(dockerImage.asString()).exec();
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

        List<Image> images = dockerClient.listImagesCmd().withShowAll(true).exec();
        List<com.github.dockerjava.api.model.Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();

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
        private Statistics stats;

        @Override
        public void onNext(Statistics stats) {
            if (stats != null) {
                this.stats = stats;
                onComplete();
            }
        }
    }

    private void initDockerConnection(final DockerConfig config, boolean fallbackTo123orErrors) {
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
        dockerClient = DockerClientImpl.getInstance(
                buildDockerClientConfig(config)
                        .withApiVersion(remoteApiVersion)
                        .build())
                .withDockerCmdExecFactory(dockerFactory);
    }

    private void setMetrics(MetricReceiverWrapper metricReceiver) {
        Dimensions dimensions = new Dimensions.Builder()
                .add("host", HostName.getLocalhost())
                .add("role", "docker").build();

        numberOfRunningContainersGauge = metricReceiver.declareGauge(dimensions, "containers.running");
        numberOfDockerDaemonFails = metricReceiver.declareCounter(dimensions, "daemon.api_fails");

        // Some containers could already be running, count them and initialize to that value
        numberOfRunningContainersGauge.sample(getAllManagedContainers().size());
    }
}
