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
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.RemoteApiVersion;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory;
import com.google.inject.Inject;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.applicationmodel.HostName;

import javax.annotation.concurrent.GuardedBy;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
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

    private static final String DOCKER_CUSTOM_IP6_NETWORK_NAME = "habla";

    private final Object monitor = new Object();
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
             remoteApiVersion = RemoteApiVersion.parseConfig(DockerClientImpl.getInstance()
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

       // DockerClientImpl.getInstance().infoCmd().exec().getServerVersion();
        this.dockerClient = DockerClientImpl.getInstance(new DefaultDockerClientConfig.Builder()
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
    public StartContainerCommand createStartContainerCommand(DockerImage image, ContainerName name, HostName hostName) {
        return new StartContainerCommandImpl(dockerClient, image, name, hostName)
                .withLabel(LABEL_NAME_MANAGEDBY, LABEL_VALUE_MANAGEDBY);
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

    @Override
    public Set<DockerImage> getUnusedDockerImages() {
        // Description of concepts and relationships:
        // - a docker image has an id, and refers to its parent image (if any) by image id.
        // - a docker image may, in addition to id,  have multiple tags, but each tag identifies exactly one image.
        // - a docker container refers to its image (exactly one) either by image id or by image tag.
        // What this method does to find images considered unused, is build a tree of dependencies
        // (e.g. container->tag->image->image) and identify image nodes whose only children (if any) are leaf tags.
        // In other words, an image node with no children, or only tag children having no children themselves is unused.
        // An image node with an image child is considered used.
        // An image node with a container child is considered used.
        // An image node with a tag child with a container child is considered used.
        try {
            final Map<String, DockerObject> objects = new HashMap<>();
            final Map<String, String> dependencies = new HashMap<>();

            // Populate maps with images (including tags) and their dependencies (parents).
            for (Image image : dockerClient.listImagesCmd().withShowAll(true).exec()) {
                objects.put(image.getId(), new DockerObject(image.getId(), DockerObjectType.IMAGE));
                if (image.getParentId() != null && !image.getParentId().isEmpty()) {
                    dependencies.put(image.getId(), image.getParentId());
                }
                for (String tag : image.getRepoTags()) {
                    objects.put(tag, new DockerObject(tag, DockerObjectType.IMAGE_TAG));
                    dependencies.put(tag, image.getId());
                }
            }

            // Populate maps with containers and their dependency to the image they run on.
            for (com.github.dockerjava.api.model.Container container : dockerClient.listContainersCmd().withShowAll(true).exec()) {
                objects.put(container.getId(), new DockerObject(container.getId(), DockerObjectType.CONTAINER));
                dependencies.put(container.getId(), container.getImage());
            }

            // Now update every object with its dependencies.
            dependencies.forEach((fromId, toId) -> {
                Optional.ofNullable(objects.get(toId))
                        .ifPresent(obj -> obj.addDependee(objects.get(fromId)));
            });

            // Find images that are not in use (i.e. leafs not used by any containers).
            return objects.values().stream()
                    .filter(dockerObject -> dockerObject.type == DockerObjectType.IMAGE)
                    .filter(dockerObject -> !dockerObject.isInUse())
                    .map(obj -> obj.id)
                    .map(DockerImage::new)
                    .collect(Collectors.toSet());
        } catch (DockerException e) {
            throw new RuntimeException("Unexpected exception", e);
        }
    }

    // Helper enum for calculating which images are unused.
    private enum DockerObjectType {
        IMAGE_TAG, IMAGE, CONTAINER
    }

    // Helper class for calculating which images are unused.
    private static class DockerObject {
        public final String id;
        public final DockerObjectType type;
        private final List<DockerObject> dependees = new LinkedList<>();

        public DockerObject(final String id, final DockerObjectType type) {
            this.id = id;
            this.type = type;
        }

        public boolean isInUse() {
            if (type == DockerObjectType.CONTAINER) {
                return true;
            }

            if (dependees.isEmpty()) {
                return false;
            }

            if (type == DockerObjectType.IMAGE) {
                if (dependees.stream().anyMatch(obj -> obj.type == DockerObjectType.IMAGE)) {
                    return true;
                }
            }

            return dependees.stream().anyMatch(DockerObject::isInUse);
        }

        public void addDependee(final DockerObject dockerObject) {
            dependees.add(dockerObject);
        }

        @Override
        public String toString() {
            return "DockerObject {"
                    + " id=" + id
                    + " type=" + type.name().toLowerCase()
                    + " inUse=" + isInUse()
                    + " dependees=" + dependees.stream().map(obj -> obj.id).collect(Collectors.toList())
                    + " }";
        }
    }

    private class ImagePullCallback extends PullImageResultCallback {
        private final DockerImage dockerImage;

        ImagePullCallback(DockerImage dockerImage) {
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