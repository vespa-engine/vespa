// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.UpdateContainerCmd;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.async.ResultCallbackTemplate;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory;
import com.google.inject.Inject;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.exception.ContainerNotFoundException;
import com.yahoo.vespa.hosted.dockerapi.exception.DockerException;
import com.yahoo.vespa.hosted.dockerapi.exception.DockerExecTimeoutException;
import com.yahoo.vespa.hosted.dockerapi.metrics.Counter;
import com.yahoo.vespa.hosted.dockerapi.metrics.Dimensions;
import com.yahoo.vespa.hosted.dockerapi.metrics.Gauge;
import com.yahoo.vespa.hosted.dockerapi.metrics.Metrics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DockerEngine implements ContainerEngine {

    private static final Logger logger = Logger.getLogger(DockerEngine.class.getName());

    static final String LABEL_NAME_MANAGEDBY = "com.yahoo.vespa.managedby";
    private static final String FRAMEWORK_CONTAINER_PREFIX = "/";
    private static final Duration WAIT_BEFORE_KILLING = Duration.ofSeconds(10);

    private final Object monitor = new Object();
    private final Set<DockerImage> scheduledPulls = new HashSet<>();

    private final DockerClient dockerClient;
    private final DockerImageGarbageCollector dockerImageGC;
    private final Metrics metrics;
    private final Counter numberOfDockerApiFails;
    private final Clock clock;

    @Inject
    public DockerEngine(Metrics metrics) {
        this(createDockerClient(), metrics, Clock.systemUTC());
    }

    DockerEngine(DockerClient dockerClient, Metrics metrics, Clock clock) {
        this.dockerClient = dockerClient;
        this.dockerImageGC = new DockerImageGarbageCollector(this);
        this.metrics = metrics;
        this.clock = clock;

        numberOfDockerApiFails = metrics.declareCounter("docker.api_fails");
    }

    @Override
    public boolean pullImageAsyncIfNeeded(DockerImage image, RegistryCredentials registryCredentials) {
        try {
            synchronized (monitor) {
                if (scheduledPulls.contains(image)) return true;
                if (imageIsDownloaded(image)) return false;

                scheduledPulls.add(image);

                logger.log(Level.INFO, "Starting download of " + image.asString());
                PullImageCmd pullCmd = dockerClient.pullImageCmd(image.asString());
                if (!registryCredentials.equals(RegistryCredentials.none)) {
                    logger.log(Level.INFO, "Authenticating with " + registryCredentials.registryAddress());
                    AuthConfig authConfig = new AuthConfig().withUsername(registryCredentials.username())
                                                            .withPassword(registryCredentials.password())
                                                            .withRegistryAddress(registryCredentials.registryAddress());
                    pullCmd = pullCmd.withAuthConfig(authConfig);
                }
                pullCmd.exec(new ImagePullCallback(image));
                return true;
            }
        } catch (RuntimeException e) {
            numberOfDockerApiFails.increment();
            throw new DockerException("Failed to pull image '" + image.asString() + "'", e);
        }
    }

    private void removeScheduledPoll(DockerImage image) {
        synchronized (monitor) {
            scheduledPulls.remove(image);
        }
    }

    /**
     * Check if a given image is already in the local registry
     */
    boolean imageIsDownloaded(DockerImage dockerImage) {
        return inspectImage(dockerImage).isPresent();
    }

    private Optional<InspectImageResponse> inspectImage(DockerImage dockerImage) {
        try {
            return Optional.of(dockerClient.inspectImageCmd(dockerImage.asString()).exec());
        } catch (NotFoundException e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            numberOfDockerApiFails.increment();
            throw new DockerException("Failed to inspect image '" + dockerImage.asString() + "'", e);
        }
    }

    @Override
    public CreateContainerCommand createContainerCommand(DockerImage image, ContainerName containerName) {
        return new CreateContainerCommandImpl(dockerClient, image, containerName);
    }


    @Override
    public ProcessResult executeInContainerAsUser(ContainerName containerName, String user, OptionalLong timeoutSeconds, String... command) {
        try {
            ExecCreateCmdResponse response = execCreateCmd(containerName, user, command);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ByteArrayOutputStream errors = new ByteArrayOutputStream();
            ExecStartResultCallback callback = dockerClient.execStartCmd(response.getId())
                    .exec(new ExecStartResultCallback(output, errors));

            if (timeoutSeconds.isPresent()) {
                if (!callback.awaitCompletion(timeoutSeconds.getAsLong(), TimeUnit.SECONDS))
                    throw new DockerExecTimeoutException(String.format(
                            "Command '%s' did not finish within %d seconds.", command[0], timeoutSeconds.getAsLong()));
            } else {
                // Wait for completion no timeout
                callback.awaitCompletion();
            }

            InspectExecResponse state = dockerClient.inspectExecCmd(response.getId()).exec();
            if (state.isRunning())
                throw new DockerException("Command '%s' did not finish within %s seconds.");

            return new ProcessResult(state.getExitCode(), new String(output.toByteArray()), new String(errors.toByteArray()));
        } catch (RuntimeException | InterruptedException e) {
            numberOfDockerApiFails.increment();
            throw new DockerException("Container '" + containerName.asString()
                    + "' failed to execute " + Arrays.toString(command), e);
        }
    }

    private ExecCreateCmdResponse execCreateCmd(ContainerName containerName, String user, String... command) {
        try {
            return dockerClient.execCreateCmd(containerName.asString())
                    .withCmd(command)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withUser(user)
                    .exec();
        } catch (NotFoundException e) {
            throw new ContainerNotFoundException(containerName);
        }
    }

    private Optional<InspectContainerResponse> inspectContainerCmd(String container) {
        try {
            return Optional.of(dockerClient.inspectContainerCmd(container).exec());
        } catch (NotFoundException ignored) {
            return Optional.empty();
        } catch (RuntimeException e) {
            numberOfDockerApiFails.increment();
            throw new DockerException("Failed to get info for container '" + container + "'", e);
        }
    }

    @Override
    public Optional<ContainerStats> getContainerStats(ContainerName containerName) {
        try {
            DockerStatsCallback statsCallback = dockerClient.statsCmd(containerName.asString()).exec(new DockerStatsCallback());
            statsCallback.awaitCompletion(5, TimeUnit.SECONDS);
            return statsCallback.stats.map(DockerEngine::containerStatsFrom);
        } catch (NotFoundException ignored) {
            return Optional.empty();
        } catch (RuntimeException | InterruptedException e) {
            numberOfDockerApiFails.increment();
            throw new DockerException("Failed to get stats for container '" + containerName.asString() + "'", e);
        }
    }

    @Override
    public void startContainer(ContainerName containerName) {
        try {
            dockerClient.startContainerCmd(containerName.asString()).exec();
        } catch (NotFoundException e) {
            throw new ContainerNotFoundException(containerName);
        } catch (NotModifiedException ignored) {
            // If is already started, ignore
        } catch (RuntimeException e) {
            numberOfDockerApiFails.increment();
            throw new DockerException("Failed to start container '" + containerName.asString() + "'", e);
        }
    }

    @Override
    public void stopContainer(ContainerName containerName) {
        try {
            dockerClient.stopContainerCmd(containerName.asString()).withTimeout((int) WAIT_BEFORE_KILLING.getSeconds()).exec();
        } catch (NotFoundException e) {
            throw new ContainerNotFoundException(containerName);
        } catch (NotModifiedException ignored) {
            // If is already stopped, ignore
        } catch (RuntimeException e) {
            numberOfDockerApiFails.increment();
            throw new DockerException("Failed to stop container '" + containerName.asString() + "'", e);
        }
    }

    @Override
    public void deleteContainer(ContainerName containerName) {
        try {
            dockerClient.removeContainerCmd(containerName.asString()).exec();
        } catch (NotFoundException e) {
            throw new ContainerNotFoundException(containerName);
        } catch (RuntimeException e) {
            numberOfDockerApiFails.increment();
            throw new DockerException("Failed to delete container '" + containerName.asString() + "'", e);
        }
    }

    @Override
    public void updateContainer(ContainerName containerName, ContainerResources resources) {
        try {
            UpdateContainerCmd updateContainerCmd = dockerClient.updateContainerCmd(containerName.asString())
                    .withCpuShares(resources.cpuShares())
                    .withMemory(resources.memoryBytes())
                    .withMemorySwap(resources.memoryBytes())

                    // Command line argument `--cpus c` is sent over to docker daemon as "NanoCPUs", which is the
                    // value of `c * 1e9`. This however, is just a shorthand for `--cpu-period p` and `--cpu-quota q`
                    // where p = 100000 and q = c * 100000.
                    // See: https://docs.docker.com/config/containers/resource_constraints/#configure-the-default-cfs-scheduler
                    // --cpus requires API 1.25+ on create and 1.29+ on update
                    // NanoCPUs is supported in docker-java as of 3.1.0 on create and not at all on update
                    // TODO: Simplify this to .withNanoCPUs(resources.cpu()) when docker-java supports it
                    .withCpuPeriod(resources.cpuPeriod())
                    .withCpuQuota(resources.cpuQuota());

            updateContainerCmd.exec();
        } catch (NotFoundException e) {
            throw new ContainerNotFoundException(containerName);
        } catch (RuntimeException e) {
            numberOfDockerApiFails.increment();
            throw new DockerException("Failed to update container '" + containerName.asString() + "' to " + resources, e);
        }
    }

    @Override
    public Optional<Container> getContainer(ContainerName containerName) {
        return asContainer(containerName.asString()).findFirst();
    }

    private Stream<Container> asContainer(String container) {
        return inspectContainerCmd(container)
                .map(response -> new Container(
                        new ContainerId(response.getId()),
                        response.getConfig().getHostName(),
                        DockerImage.fromString(response.getConfig().getImage()),
                        containerResourcesFromHostConfig(response.getHostConfig()),
                        toContainerName(response.getName()),
                        Container.State.valueOf(response.getState().getStatus().toUpperCase()),
                        response.getState().getPid()
                ))
                .stream();
    }

    private static ContainerResources containerResourcesFromHostConfig(HostConfig hostConfig) {
        // Docker keeps an internal state of what the period and quota are: in cgroups, the quota is always set
        // (default is 100000), but docker will report it as 0 unless explicitly set by the user.
        // This may lead to a state where the quota is set, but period is 0 (accord to docker), which will
        // mess up the calculation below. This can only happen if someone sets it manually, since this class
        // will always set both quota and period.
        final double cpus = hostConfig.getCpuQuota() > 0 ?
                (double) hostConfig.getCpuQuota() / hostConfig.getCpuPeriod() : 0;
        return new ContainerResources(cpus, hostConfig.getCpuShares(), hostConfig.getMemory());
    }

    private boolean isManagedBy(com.github.dockerjava.api.model.Container container, String manager) {
        final Map<String, String> labels = container.getLabels();
        return labels != null && manager.equals(labels.get(LABEL_NAME_MANAGEDBY));
    }

    private ContainerName toContainerName(String encodedContainerName) {
        return new ContainerName(encodedContainerName.substring(FRAMEWORK_CONTAINER_PREFIX.length()));
    }

    @Override
    public boolean noManagedContainersRunning(String manager) {
        return listAllContainers().stream()
                .filter(container -> isManagedBy(container, manager))
                .noneMatch(container -> "running".equalsIgnoreCase(container.getState()));
    }

    @Override
    public List<ContainerName> listManagedContainers(String manager) {
        return listAllContainers().stream()
                .filter(container -> isManagedBy(container, manager))
                .map(container -> toContainerName(container.getNames()[0]))
                .collect(Collectors.toList());
    }

    List<com.github.dockerjava.api.model.Container> listAllContainers() {
        try {
            return dockerClient.listContainersCmd().withShowAll(true).exec();
        } catch (RuntimeException e) {
            numberOfDockerApiFails.increment();
            throw new DockerException("Failed to list all containers", e);
        }
    }

    List<Image> listAllImages() {
        try {
            return dockerClient.listImagesCmd().withShowAll(true).exec();
        } catch (RuntimeException e) {
            numberOfDockerApiFails.increment();
            throw new DockerException("Failed to list all images", e);
        }
    }

    void deleteImage(String imageReference) {
        try {
            dockerClient.removeImageCmd(imageReference).exec();
        } catch (NotFoundException ignored) {
            // Image was already deleted, ignore
        } catch (RuntimeException e) {
            numberOfDockerApiFails.increment();
            throw new DockerException("Failed to delete image by reference '" + imageReference + "'", e);
        }
    }

    @Override
    public boolean deleteUnusedDockerImages(List<DockerImage> excludes, Duration minImageAgeToDelete) {
        List<String> excludedRefs = excludes.stream().map(DockerImage::asString).collect(Collectors.toList());
        return dockerImageGC.deleteUnusedDockerImages(excludedRefs, minImageAgeToDelete);
    }

    private class ImagePullCallback extends PullImageResultCallback {

        private final DockerImage dockerImage;
        private final Instant startedAt;

        private ImagePullCallback(DockerImage dockerImage) {
            this.dockerImage = dockerImage;
            this.startedAt = clock.instant();
        }

        @Override
        public void onError(Throwable throwable) {
            removeScheduledPoll(dockerImage);
            logger.log(Level.SEVERE, "Could not download image " + dockerImage.asString(), throwable);
        }

        @Override
        public void onComplete() {
            if (imageIsDownloaded(dockerImage)) {
                logger.log(Level.INFO, "Download completed: " + dockerImage.asString());
                removeScheduledPoll(dockerImage);
            } else {
                numberOfDockerApiFails.increment();
                throw new DockerClientException("Could not download image: " + dockerImage);
            }
            sampleDuration();
        }

        private void sampleDuration() {
            Gauge gauge = metrics.declareGauge("docker.imagePullDurationSecs",
                                               new Dimensions(Map.of("image", dockerImage.asString())));
            Duration pullDuration = Duration.between(startedAt, clock.instant());
            gauge.sample(pullDuration.getSeconds());
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

    private static DockerClient createDockerClient() {
        JerseyDockerCmdExecFactory dockerFactory = new JerseyDockerCmdExecFactory()
                .withMaxPerRouteConnections(10)
                .withMaxTotalConnections(100)
                .withConnectTimeout((int) Duration.ofSeconds(100).toMillis())
                .withReadTimeout((int) Duration.ofMinutes(30).toMillis());

        DockerClientConfig dockerClientConfig = new DefaultDockerClientConfig.Builder()
                .withDockerHost("unix:///var/run/docker.sock")
                .build();

        return DockerClientImpl.getInstance(dockerClientConfig)
                .withDockerCmdExecFactory(dockerFactory);
    }

    private static ContainerStats containerStatsFrom(Statistics statistics) {
        return new ContainerStats(Optional.ofNullable(statistics.getNetworks()).orElseGet(Map::of)
                                          .entrySet().stream()
                                          .collect(Collectors.toMap(
                                                  Map.Entry::getKey,
                                                  e -> new ContainerStats.NetworkStats(e.getValue().getRxBytes(), e.getValue().getRxDropped(),
                                                                                       e.getValue().getRxErrors(), e.getValue().getTxBytes(),
                                                                                       e.getValue().getTxDropped(), e.getValue().getTxErrors()),
                                                  (u, v) -> {
                                                      throw new IllegalStateException();
                                                  },
                                                  TreeMap::new)),
                                  new ContainerStats.MemoryStats(statistics.getMemoryStats().getStats().getCache(),
                                                                 statistics.getMemoryStats().getUsage(),
                                                                 statistics.getMemoryStats().getLimit()),
                                  new ContainerStats.CpuStats(statistics.getCpuStats().getCpuUsage().getPercpuUsage().size(),
                                                              statistics.getCpuStats().getSystemCpuUsage(),
                                                              statistics.getCpuStats().getCpuUsage().getTotalUsage(),
                                                              statistics.getCpuStats().getCpuUsage().getUsageInKernelmode(),
                                                              statistics.getCpuStats().getThrottlingData().getThrottledTime(),
                                                              statistics.getCpuStats().getThrottlingData().getPeriods(),
                                                              statistics.getCpuStats().getThrottlingData().getThrottledPeriods()));
    }

    // For testing only, create ContainerStats from JSON returned by docker daemon stats API
    public static ContainerStats statsFromJson(String json) {
        try {
            Statistics statistics = new ObjectMapper().readValue(json, Statistics.class);
            return containerStatsFrom(statistics);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
