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
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.RemoteApiVersion;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory;
import com.google.inject.Inject;
import com.yahoo.collections.Pair;
import com.yahoo.log.LogLevel;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.defaults.Defaults;

import javax.annotation.concurrent.GuardedBy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static com.yahoo.vespa.hosted.dockerapi.DockerNetworkCreator.NetworkAddressInterface;


public class DockerImpl implements Docker {
    private static final Logger logger = Logger.getLogger(DockerImpl.class.getName());

    private static final String LABEL_NAME_MANAGEDBY = "com.yahoo.vespa.managedby";
    private static final String LABEL_VALUE_MANAGEDBY = "node-admin";

    private static final int SECONDS_TO_WAIT_BEFORE_KILLING = 10;
    private static final String FRAMEWORK_CONTAINER_PREFIX = "/";

    private static final int DOCKER_MAX_PER_ROUTE_CONNECTIONS = 10;
    private static final int DOCKER_MAX_TOTAL_CONNECTIONS = 100;
    private static final int DOCKER_CONNECT_TIMEOUT_MILLIS = (int) TimeUnit.SECONDS.toMillis(100);
    private static final int DOCKER_READ_TIMEOUT_MILLIS = (int) TimeUnit.MINUTES.toMillis(30);

    public static final String DOCKER_CUSTOM_MACVLAN_NETWORK_NAME = "vespa-macvlan";

    public final Object monitor = new Object();
    @GuardedBy("monitor")
    private final Map<DockerImage, CompletableFuture<DockerImage>> scheduledPulls = new HashMap<>();

    final DockerClient dockerClient;

    DockerImpl(final DockerClient dockerClient) {
        this.dockerClient = dockerClient;
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
            logger.log(LogLevel.ERROR, "Failed when trying to figure out remote API version of docker, using 1.23", e);
            remoteApiVersion = RemoteApiVersion.VERSION_1_23;
        }

        this.dockerClient = DockerClientImpl.getInstance(
                buildDockerClientConfig(config)
                .withApiVersion(remoteApiVersion)
                .build())
                .withDockerCmdExecFactory(dockerFactory);

        try {
            setupDockerNetworkIfNeeded();
        } catch (Exception e) {
            throw new RuntimeException("Could not setup docker network", e);
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
                .withGateway(DockerNetworkCreator.getDefaultGateway(isIPv6).getHostAddress()));

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
    public CompletableFuture<DockerImage> pullImageAsync(final DockerImage image) {
        final CompletableFuture<DockerImage> completionListener;
        synchronized (monitor) {
            if (scheduledPulls.containsKey(image)) {
                return scheduledPulls.get(image);
            }
            completionListener = new CompletableFuture<>();
            scheduledPulls.put(image, completionListener);
        }
        //dockerClient.pullImageCmd(image.asString()).exec(new ImagePullCallback(image));
        // TODO: Need to call out to a command-line tool due to conflicting jackson dependencies between
        // docker-java and pre-installed bundles in jdisc container
        pullImageWithCommandTool(image, new ImagePullCallback(image), completionListener);
        return completionListener;
    }

    private void pullImageWithCommandTool(DockerImage image, ImagePullCallback callback, CompletableFuture<DockerImage> completionListener) {
        String jarFile = Defaults.getDefaults().vespaHome() + "lib/jars/docker-tools-jar-with-dependencies.jar";
        Pair<Integer, String> result = null;
        try {
            result = new ProcessExecuter().exec(String.format(
                    "java -cp %s com.yahoo.vespa.hosted.dockerapi.tool.PullImageCommand pull-image %s",
                    jarFile,
                    image.asString()));
        } catch (IOException e) {
            logger.log(LogLevel.ERROR, "Failed pulling image " + image.asString(), e);
            callback.onError(e);
        }
        if (result != null && result.getFirst() != 0) {
            logger.log(LogLevel.WARNING, "Failed pulling image " + image.asString() +
                    ", exit code " + result.getFirst() + ", output: " + result.getSecond());
        } else {
            logger.log(LogLevel.INFO, "Successfully pulled image " + image.asString());
        }
        callback.onComplete();
        completionListener.complete(image);
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
            List<Image> images = dockerClient.listImagesCmd().withShowAll(true).exec();
            return images.stream().
                    flatMap(image -> Arrays.stream(image.getRepoTags())).
                    anyMatch(tag -> tag.equals(dockerImage.asString()));
        } catch (DockerException e) {
            throw new RuntimeException("Failed to list image name: '" + dockerImage + "'", e);
        }
    }

    @Override
    public CreateContainerCommand createContainerCommand(DockerImage image, ContainerName name, HostName hostName) {
        return new CreateContainerCommandImpl(dockerClient, image, name, hostName)
                .withLabel(LABEL_NAME_MANAGEDBY, LABEL_VALUE_MANAGEDBY);
    }

    @Override
    public void connectContainerToNetwork(ContainerName containerName, String networkName) {
        dockerClient.connectToNetworkCmd()
                .withContainerId(containerName.asString())
                .withNetworkId(networkName).exec();
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
            throw new RuntimeException("Container " + containerName.asString()
                    + " failed to execute " + Arrays.toString(args), e);
        }
    }

    @Override
    public ContainerInfo inspectContainer(ContainerName containerName) {
        InspectContainerResponse containerInfo = dockerClient.inspectContainerCmd(containerName.asString()).exec();
        return new ContainerInfoImpl(containerName, containerInfo);
    }

    @Override
    public void startContainer(ContainerName containerName) {
        Optional<com.github.dockerjava.api.model.Container> dockerContainer = getContainerFromName(containerName, true);
        if (dockerContainer.isPresent()) {
            try {
                dockerClient.startContainerCmd(dockerContainer.get().getId()).exec();
            } catch (DockerException e) {
                throw new RuntimeException("Failed to start container", e);
            }
        }
    }


    @Override
    public void stopContainer(final ContainerName containerName) {
        Optional<com.github.dockerjava.api.model.Container> dockerContainer = getContainerFromName(containerName, true);
        if (dockerContainer.isPresent()) {
            try {
                dockerClient.stopContainerCmd(dockerContainer.get().getId()).withTimeout(SECONDS_TO_WAIT_BEFORE_KILLING).exec();
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
                dockerClient.removeContainerCmd(dockerContainer.get().getId()).exec();
            } catch (DockerException e) {
                throw new RuntimeException("Failed to delete container", e);
            }
        }
    }

    @Override
    public List<Container> getAllManagedContainers() {
        try {
            return dockerClient.listContainersCmd().withShowAll(true).exec().stream()
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
            final InspectContainerResponse response = dockerClient.inspectContainerCmd(dockerClientContainer.getId()).exec();
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
            return dockerClient.listContainersCmd().withShowAll(alsoGetStoppedContainers).exec().stream()
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
        dockerClient.removeImageCmd(dockerImage.asString()).exec();
    }

    private Map<String, Image> filterOutImagesUsedByContainers(
            Map<String, Image> dockerImagesByImageId, List<com.github.dockerjava.api.model.Container> containerList) {
        Map<String, Image> filteredDockerImagesByImageId = new HashMap<>(dockerImagesByImageId);

        for (com.github.dockerjava.api.model.Container container : containerList) {
            String imageToSpare = container.getImageId();
            do {
                // May be null if two images have have the same parent, the first image will remove the parent, the
                // second will get null.
                Image sparedImage = filteredDockerImagesByImageId.remove(imageToSpare);
                imageToSpare = sparedImage == null ? "" : sparedImage.getParentId();
            } while (!imageToSpare.isEmpty());
        }

        return filteredDockerImagesByImageId;
    }

    private Map<String, Image> filterOutExceptImages(Map<String, Image> dockerImagesByImageId, Set<DockerImage> except) {
        Map<String, Image> dockerImageByImageTags = new HashMap<>();
        // Transform map of image ID:image to map of image tag:image (for each tag the image has)
        for (Map.Entry<String, Image> entry : dockerImagesByImageId.entrySet()) {
            String[] repoTags = entry.getValue().getRepoTags();
            // If no tags present, fall back to image ID
            if (repoTags == null) {
                dockerImageByImageTags.put(entry.getKey(), entry.getValue());
            } else {
                for (String tag : repoTags) {
                    dockerImageByImageTags.put(tag, entry.getValue());
                }
            }
        }

        // Remove images we want to keep from the map of unused images, also recursively keep the parents of images we want to keep
        except.forEach(image -> {
            String imageToSpare = image.asString();
            do {
                Image sparedImage = dockerImageByImageTags.remove(imageToSpare);
                imageToSpare = sparedImage == null ? "" : sparedImage.getParentId();
            } while (!imageToSpare.isEmpty());
        });
        return dockerImageByImageTags;
    }

    /**
     * Generates lists of images that are safe to delete, in the order that is safe to delete them (children before
     * parents). The function starts with a map of all images and the filters out images that are used now or will be
     * used in near future (through the use of except set).
     *
     * @param except set of image tags to keep, regardless whether they are being used right now or not.
     * @return List of image tags of unused images, if unused image has no tag, will return image ID instead.
     */
    List<DockerImage> getUnusedDockerImages(Set<DockerImage> except) {
        List<Image> images = dockerClient.listImagesCmd().withShowAll(true).exec();
        Map<String, Image> dockerImageByImageId = images.stream().collect(Collectors.toMap(Image::getId, img -> img));

        List<com.github.dockerjava.api.model.Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
        Map<String, Image> unusedImagesByContainers = filterOutImagesUsedByContainers(dockerImageByImageId, containers);
        Map<String, Image> unusedImagesByExcept = filterOutExceptImages(unusedImagesByContainers, except);

        List<String> unusedImages = unusedImagesByExcept.keySet().stream().collect(Collectors.toList());
        // unusedImages now contains all the unused images, all we need to do now is to order them in a way that is
        // safe to delete with. The order is:
        Collections.sort(unusedImages, (o1, o2) -> {
            Image image1 = unusedImagesByExcept.get(o1);
            Image image2 = unusedImagesByExcept.get(o2);

            // If image2 is parent of image1, image1 comes before image2
            if (Objects.equals(image1.getParentId(), image2.getId())) return -1;
            // If image1 is parent of image2, image2 comes before image1
            else if (Objects.equals(image2.getParentId(), image1.getId())) return 1;
            // Otherwise, sort lexicographically by image name (For testing)
            else return o1.compareTo(o2);
        });

        return unusedImages.stream().map(DockerImage::new).collect(Collectors.toList());
    }

    @Override
    public void deleteUnusedDockerImages(Set<DockerImage> except) {
        try {
            getUnusedDockerImages(except).stream().forEach(this::deleteImage);
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
