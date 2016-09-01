// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.RemoteApiVersion;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory;
import com.google.common.base.Joiner;
import com.google.common.io.CharStreams;
import com.google.inject.Inject;
import com.yahoo.nodeadmin.docker.DockerConfig;
import com.yahoo.vespa.applicationmodel.HostName;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;

import com.yahoo.vespa.hosted.node.admin.nodeagent.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;
import com.yahoo.vespa.hosted.node.maintenance.Maintainer;

import javax.annotation.concurrent.GuardedBy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * @author stiankri
 */
public class DockerImpl implements Docker {
    private static final PrefixLogger NODE_ADMIN_LOGGER = PrefixLogger.getNodeAdminLogger(DockerImpl.class);

    private static final int SECONDS_TO_WAIT_BEFORE_KILLING = 10;
    private static final String FRAMEWORK_CONTAINER_PREFIX = "/";
    private static final Pattern VESPA_VERSION_PATTERN = Pattern.compile("^(\\S*)$", Pattern.MULTILINE);

    private static final String LABEL_NAME_MANAGEDBY = "com.yahoo.vespa.managedby";
    private static final String LABEL_VALUE_MANAGEDBY = "node-admin";
    private static final Map<String, String> CONTAINER_LABELS = new HashMap<>();

    private static final int DOCKER_MAX_PER_ROUTE_CONNECTIONS = 10;
    private static final int DOCKER_MAX_TOTAL_CONNECTIONS = 100;
    private static final int DOCKER_CONNECT_TIMEOUT_MILLIS = (int) TimeUnit.SECONDS.toMillis(100);
    private static final int DOCKER_READ_TIMEOUT_MILLIS = (int) TimeUnit.MINUTES.toMillis(30);
    static final String DOCKER_CUSTOM_IP6_NETWORK_NAME = "habla";

    static {
        CONTAINER_LABELS.put(LABEL_NAME_MANAGEDBY, LABEL_VALUE_MANAGEDBY);
    }

    private static final List<String> DIRECTORIES_TO_MOUNT = Arrays.asList(
            getDefaults().underVespaHome("logs"),
            getDefaults().underVespaHome("var/cache"),
            getDefaults().underVespaHome("var/crash"),
            getDefaults().underVespaHome("var/db/jdisc"),
            getDefaults().underVespaHome("var/db/vespa"),
            getDefaults().underVespaHome("var/jdisc_container"),
            getDefaults().underVespaHome("var/jdisc_core"),
            getDefaults().underVespaHome("var/logstash-forwarder"),
            getDefaults().underVespaHome("var/maven"),
            getDefaults().underVespaHome("var/scoreboards"),
            getDefaults().underVespaHome("var/service"),
            getDefaults().underVespaHome("var/share"),
            getDefaults().underVespaHome("var/spool"),
            getDefaults().underVespaHome("var/vespa"),
            getDefaults().underVespaHome("var/yca"),
            getDefaults().underVespaHome("var/ycore++"),
            getDefaults().underVespaHome("var/zookeeper"));

    final DockerClient docker;

    private final Object monitor = new Object();

    @GuardedBy("monitor")
    private final Map<DockerImage, CompletableFuture<DockerImage>> scheduledPulls = new HashMap<>();

    DockerImpl(final DockerClient dockerClient) {
        this.docker = dockerClient;
    }

    @Inject
    public DockerImpl(final DockerConfig config) {
        JerseyDockerCmdExecFactory dockerFactory =  new JerseyDockerCmdExecFactory()
                .withMaxPerRouteConnections(DOCKER_MAX_PER_ROUTE_CONNECTIONS)
                .withMaxTotalConnections(DOCKER_MAX_TOTAL_CONNECTIONS)
                .withConnectTimeout(DOCKER_CONNECT_TIMEOUT_MILLIS)
                .withReadTimeout(DOCKER_READ_TIMEOUT_MILLIS);

        RemoteApiVersion remoteApiVersion;
        try {
             remoteApiVersion = RemoteApiVersion.parseConfig(DockerClientImpl.getInstance()
                     .withDockerCmdExecFactory(dockerFactory).versionCmd().exec().getApiVersion());
            NODE_ADMIN_LOGGER.info("Found version of remote docker API: "+ remoteApiVersion);
            // From version 1.24 a field was removed which causes trouble with the current docker java code.
            // When this is fixed, we can remove this and do not specify version.
            if (remoteApiVersion.isGreaterOrEqual(RemoteApiVersion.VERSION_1_24)) {
                remoteApiVersion = RemoteApiVersion.VERSION_1_23;
                NODE_ADMIN_LOGGER.info("Found version 1.24 or newer of remote API, using 1.23.");
            }
        } catch (Exception e) {
            NODE_ADMIN_LOGGER.error("Failed when trying to figure out remote API version of docker, using 1.23", e);
            remoteApiVersion = RemoteApiVersion.VERSION_1_23;
        }

       // DockerClientImpl.getInstance().infoCmd().exec().getServerVersion();
        this.docker = DockerClientImpl.getInstance(new DefaultDockerClientConfig.Builder()
                .withDockerHost(config.uri())
                .withApiVersion(remoteApiVersion)
                .build())
                .withDockerCmdExecFactory(dockerFactory);
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
        docker.pullImageCmd(image.asString()).exec(new ImagePullCallback(image));
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
        try {
            List<Image> images = docker.listImagesCmd().withShowAll(true).exec();
            return images.stream().
                    flatMap(image -> Arrays.stream(image.getRepoTags())).
                    anyMatch(tag -> tag.equals(dockerImage.asString()));
        } catch (DockerException e) {
            throw new RuntimeException("Failed to list image name: '" + dockerImage + "'", e);
        }
    }

    @Override
    public void startContainer(
            final DockerImage dockerImage,
            final HostName hostName,
            final ContainerName containerName,
            final InetAddress nodeInetAddress,
            double minCpuCores,
            double minDiskAvailableGb,
            double minMainMemoryAvailableGb) {
        try {
            String nodeIpAddress = nodeInetAddress.getHostAddress();
            final boolean isRunningIPv6 = nodeInetAddress instanceof Inet6Address;
            final double GIGA = Math.pow(2.0, 30.0);

            NODE_ADMIN_LOGGER.info("--- Starting up " + hostName.s() + " with node IP: " + nodeIpAddress +
                    ", running IPv6: " + isRunningIPv6);

            CreateContainerCmd containerConfigBuilder = docker.createContainerCmd(dockerImage.asString())
                    .withName(containerName.asString())
                    .withLabels(CONTAINER_LABELS)
                    .withEnv(new String[]{"CONFIG_SERVER_ADDRESS=" + Joiner.on(',').join(Environment.getConfigServerHosts())})
                    .withHostName(hostName.s())
                    .withBinds(applicationStorageToMount(containerName));

            // TODO: Enforce disk constraints
            // TODO: Consider if CPU shares or quoata should be set. For now we are just assuming they are
            // nicely controlled by docker.
            if (minMainMemoryAvailableGb > 0.00001) {
                containerConfigBuilder.withMemory((long) (GIGA * minMainMemoryAvailableGb));
            }

            //If container's IP address is IPv6, use our custom network mode and let docker handle networking.
            //If container's IP address is IPv4, set up networking manually as before. In the future docker should
            //set up the IPv4 network as well.
            if (isRunningIPv6) {
                containerConfigBuilder.withNetworkMode(DOCKER_CUSTOM_IP6_NETWORK_NAME).withIpv6Address(nodeIpAddress);
            } else {
                containerConfigBuilder.withNetworkMode("none");
            }

            CreateContainerResponse response = containerConfigBuilder.exec();
            docker.startContainerCmd(response.getId()).exec();

            if (! isRunningIPv6) {
                setupContainerIPv4Networking(containerName, hostName);
            }
        } catch (IOException | DockerException e) {
            throw new RuntimeException("Failed to start container " + containerName.asString(), e);
        }
    }

    @Override
    public String getVespaVersion(final ContainerName containerName) {
        ProcessResult result = executeInContainer(containerName, DockerOperations.GET_VESPA_VERSION_COMMAND);
        if (!result.isSuccess()) {
            throw new RuntimeException("Container " + containerName.asString() + ": Command "
                    + Arrays.toString(DockerOperations.GET_VESPA_VERSION_COMMAND) + " failed: " + result);
        }
        return parseVespaVersion(result.getOutput())
                .orElseThrow(() -> new RuntimeException(
                        "Container " + containerName.asString() + ": Failed to parse vespa version from "
                                + result.getOutput()));
    }

    // Returns empty if vespa version cannot be parsed.
    static Optional<String> parseVespaVersion(final String rawVespaVersion) {
        if (rawVespaVersion == null) return Optional.empty();

        final Matcher matcher = VESPA_VERSION_PATTERN.matcher(rawVespaVersion.trim());
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    @Override
    public ProcessResult executeInContainer(ContainerName containerName, String... args) {
        assert args.length >= 1;
        try {
            final ExecCreateCmdResponse response = docker.execCreateCmd(containerName.asString())
                    .withCmd(args)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ByteArrayOutputStream errors = new ByteArrayOutputStream();
            ExecStartCmd execStartCmd = docker.execStartCmd(response.getId());
            execStartCmd.exec(new ExecStartResultCallback(output, errors)).awaitCompletion();

            final InspectExecResponse state = docker.inspectExecCmd(execStartCmd.getExecId()).exec();
            assert !state.isRunning();
            Integer exitCode = state.getExitCode();
            assert exitCode != null;

            return new ProcessResult(exitCode, new String(output.toByteArray()), new String(errors.toByteArray()));
        } catch (DockerException | InterruptedException e) {
            throw new RuntimeException("Container " + containerName.asString()
                    + " failed to execute " + Arrays.toString(args), e);
        }
    }

    private void setupContainerIPv4Networking(ContainerName containerName,
                                              HostName hostName) throws UnknownHostException {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(DockerImpl.class, containerName);

        InspectContainerResponse containerInfo = docker.inspectContainerCmd(containerName.asString()).exec();
        InspectContainerResponse.ContainerState state = containerInfo.getState();
        Integer containerPid = -1;
        if (state.getRunning()) {
            containerPid = state.getPid();
            if (containerPid == null) {
                throw new RuntimeException("PID of running container for host " + hostName + " is null");
            }
        }

        InetAddress inetAddress = InetAddress.getByName(hostName.s());
        String ipAddress = inetAddress.getHostAddress();

        final List<String> command = new LinkedList<>();
        command.add("sudo");
        command.add(getDefaults().underVespaHome("libexec/vespa/node-admin/configure-container-networking.py"));

        Environment.NetworkType networkType = Environment.networkType();
        if (networkType != Environment.NetworkType.normal) {
            command.add("--" + networkType);
        }
        command.add(containerPid.toString());
        command.add(ipAddress);

        for (int retry = 0; retry < 30; ++retry) {
            try {
                runCommand(command);
                logger.info("Done setting up network");
                return;
            } catch (Exception e) {
                final int sleepSecs = 3;
                logger.warning("Failed to configure network with command " + command
                        + ", will retry in " + sleepSecs + " seconds", e);
                try {
                    Thread.sleep(sleepSecs * 1000);
                } catch (InterruptedException e1) {
                    logger.warning("Sleep interrupted", e1);
                }
            }
        }
    }

    private void runCommand(final List<String> command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        String output = CharStreams.toString(new InputStreamReader(process.getInputStream()));
        int resultCode = process.waitFor();
        if (resultCode != 0) {
            throw new Exception("Command " + Joiner.on(' ').join(command) + " failed: " + output);
        }
    }

    static List<Bind> applicationStorageToMount(ContainerName containerName) {
        return Stream.concat(
                        Stream.of("/etc/hosts:/etc/hosts"),
                        DIRECTORIES_TO_MOUNT.stream().map(directory ->
                                Maintainer.pathInHostFromPathInNode(containerName, directory).toString() +
                        ":" + directory))
                .map(Bind::parse)
                .collect(Collectors.toList());
    }

    @Override
    public void stopContainer(final ContainerName containerName) {
        Optional<com.github.dockerjava.api.model.Container> dockerContainer = getContainerFromName(containerName, true);
        if (dockerContainer.isPresent()) {
            try {
                docker.stopContainerCmd(dockerContainer.get().getId()).withTimeout(SECONDS_TO_WAIT_BEFORE_KILLING).exec();
            } catch (DockerException e) {
                throw new RuntimeException("Failed to stop container", e);
            }
        }
    }

    @Override
    public void deleteContainer(ContainerName containerName) {
        Optional<com.github.dockerjava.api.model.Container> dockerContainer = getContainerFromName(containerName, true);
        if (dockerContainer.isPresent()) {
            try {
                docker.removeContainerCmd(dockerContainer.get().getId()).exec();
            } catch (DockerException e) {
                throw new RuntimeException("Failed to delete container", e);
            }
        }
    }

    @Override
    public List<Container> getAllManagedContainers() {
        try {
            return docker.listContainersCmd().withShowAll(true).exec().stream()
                    .filter(this::isManaged)
                    .flatMap(this::asContainer)
                    .collect(Collectors.toList());
        } catch (DockerException e) {
            throw new RuntimeException("Could not retrieve all container", e);
        }
    }

    @Override
    public Optional<Container> getContainer(HostName hostname) {
        // TODO Don't rely on getAllManagedContainers
        return getAllManagedContainers().stream()
                .filter(c -> Objects.equals(hostname, c.hostname))
                .findFirst();
    }

    private Stream<Container> asContainer(com.github.dockerjava.api.model.Container dockerClientContainer) {
        try {
            final InspectContainerResponse response = docker.inspectContainerCmd(dockerClientContainer.getId()).exec();
            return Stream.of(new Container(
                    new HostName(response.getConfig().getHostName()),
                    new DockerImage(dockerClientContainer.getImage()),
                    new ContainerName(decode(response.getName())),
                    response.getState().getRunning()));
        } catch (DockerException e) {
            //TODO: do proper exception handling
            throw new RuntimeException("Failed talking to docker daemon", e);
        }
    }


    private Optional<com.github.dockerjava.api.model.Container> getContainerFromName(
            final ContainerName containerName, final boolean alsoGetStoppedContainers) {
        try {
            return docker.listContainersCmd().withShowAll(alsoGetStoppedContainers).exec().stream()
                    .filter(this::isManaged)
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
            NODE_ADMIN_LOGGER.info("Deleting docker image " + dockerImage);
            docker.removeImageCmd(dockerImage.asString()).exec();
        } catch (DockerException e) {
            NODE_ADMIN_LOGGER.warning("Could not delete docker image " + dockerImage, e);
        }
    }

    private Map<String, Image> filterOutImagesUsedByContainers(List<com.github.dockerjava.api.model.Container> containerList,
                                                              Map<String, Image> dockerImagesByImageId) {
        Map<String, Image> filteredDockerImagesByImageId = new HashMap<>(dockerImagesByImageId);

        for (com.github.dockerjava.api.model.Container container : containerList) {
            filteredDockerImagesByImageId.remove(container.getImageId());
        }

        return filteredDockerImagesByImageId;
    }

    private Map<String, Image> filterOutParentImages(Map<String, Image> dockerImagesByImageId) {
        Map<String, Image> filteredDockerImagesByImageId = new HashMap<>(dockerImagesByImageId);

        for (Image image : dockerImagesByImageId.values()) {
            if (image.getParentId() != null && !image.getParentId().isEmpty()) {
                filteredDockerImagesByImageId.remove(image.getParentId());
            }
        }

        return filteredDockerImagesByImageId;
    }

    @Override
    public Set<DockerImage> getUnusedDockerImages() {
        // This methods finds a set of images that are unused by any other container or image, and thus can be safely
        // deleted. Images that are parent to some other image are considered as used to ensure that their children
        // are deleted before them. In the next call of this function, those parents will be childless and become unused.
        try {
            List<Image> images = docker.listImagesCmd().withShowAll(true).exec();
            Map<String, Image> dockerImageByImageId = images.stream().collect(Collectors.toMap(Image::getId, img -> img));

            List<com.github.dockerjava.api.model.Container> containers = docker.listContainersCmd().withShowAll(true).exec();
            Map<String, Image> unusedImagesByContainers = filterOutImagesUsedByContainers(containers, dockerImageByImageId);
            Map<String, Image> unusedImages = filterOutParentImages(unusedImagesByContainers);

            // If an image is referred to by multiple tags, we must delete the image by calling delete for each of the
            // tags used by the image, the deletion of the final tag will also delete the image itself. For images
            // having only one tag, it is safest to delete my image ID since the tag may be empty.
            return unusedImages.values().stream()
                    .map(img -> img.getRepoTags().length > 1 ? Arrays.asList(img.getRepoTags()) : Collections.singletonList(img.getId()))
                    .flatMap(List::stream)
                    .map(DockerImage::new)
                    .collect(Collectors.toSet());
        } catch (DockerException e) {
            throw new RuntimeException("Unexpected exception", e);
        }
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
            if (imageIsDownloaded(dockerImage)) {
                removeScheduledPoll(dockerImage).complete(dockerImage);
            } else {
                removeScheduledPoll(dockerImage).completeExceptionally(
                        new DockerClientException("Could not download image: " + dockerImage));
            }
        }
    }
}