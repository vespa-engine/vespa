// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

import com.yahoo.config.provision.DockerImage;
import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.container.image.Image;
import com.yahoo.vespa.hosted.node.admin.nodeagent.ContainerData;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixUser;
import com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerPath;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandResult;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

/**
 * @author mpolden
 */
public class ContainerEngineMock implements ContainerEngine {

    private final Map<ContainerName, Container> containers = new ConcurrentHashMap<>();
    private final Map<String, ImageDownload> images = new ConcurrentHashMap<>();
    private boolean asyncImageDownload = false;

    public ContainerEngineMock asyncImageDownload(boolean enabled) {
        this.asyncImageDownload = enabled;
        return this;
    }

    public ContainerEngineMock completeDownloadOf(DockerImage image) {
        String imageId = image.asString();
        ImageDownload download;
        while ((download = images.get(imageId)) == null);
        download.complete();
        return this;
    }

    public ContainerEngineMock setImages(List<Image> images) {
        this.images.clear();
        for (var image : images) {
            ImageDownload imageDownload = new ImageDownload(image);
            imageDownload.complete();
            this.images.put(image.id(), imageDownload);
        }
        return this;
    }

    public ContainerEngineMock addContainers(List<Container> containers) {
        for (var container : containers) {
            if (this.containers.containsKey(container.name())) {
                throw new IllegalArgumentException("Container " + container.name() + " already exists");
            }
            this.containers.put(container.name(), container);
        }
        return this;
    }

    public ContainerEngineMock addContainer(Container container) {
        return addContainers(List.of(container));
    }

    @Override
    public ContainerData createContainer(NodeAgentContext context, ContainerResources containerResources) {
        addContainer(createContainer(context, PartialContainer.State.created, containerResources));
        return new ContainerData() {
            @Override
            public void addFile(ContainerPath path, String data) {
                throw new UnsupportedOperationException("addFile not implemented");
            }

            @Override
            public void addFile(ContainerPath path, String data, String permissions) {
                throw new UnsupportedOperationException("addFile not implemented");
            }

            @Override
            public void addDirectory(ContainerPath path, String... permissions) {
                throw new UnsupportedOperationException("addDirectory not implemented");
            }

            @Override
            public void addSymlink(ContainerPath symlink, Path target) {
                throw new UnsupportedOperationException("addSymlink not implemented");
            }

            @Override
            public void converge(NodeAgentContext context) {
                throw new UnsupportedOperationException("converge not implemented");
            }
        };
    }

    @Override
    public void startContainer(NodeAgentContext context) {
        Container container = requireContainer(context.containerName(), PartialContainer.State.created);
        Container newContainer = createContainer(context, PartialContainer.State.running, container.resources());
        containers.put(newContainer.name(), newContainer);
    }

    @Override
    public void removeContainer(TaskContext context, PartialContainer container) {
        requireContainer(container.name());
        containers.remove(container.name());
    }

    @Override
    public void updateContainer(NodeAgentContext context, ContainerId containerId, ContainerResources containerResources) {
        Container container = requireContainer(context.containerName());
        containers.put(container.name(), new Container(containerId, container.name(), container.createdAt(), container.state(),
                                                       container.imageId(), container.image(),
                                                       container.labels(), container.pid(),
                                                       container.conmonPid(), container.hostname(),
                                                       containerResources, container.networks(),
                                                       container.managed()));
    }

    @Override
    public Optional<Container> getContainer(NodeAgentContext context) {
        return Optional.ofNullable(containers.get(context.containerName()));
    }

    @Override
    public List<PartialContainer> listContainers(TaskContext context) {
        return List.copyOf(containers.values());
    }

    @Override
    public String networkInterface(NodeAgentContext context) {
        return "eth0";
    }

    @Override
    public CommandResult execute(NodeAgentContext context, UnixUser user, Duration timeout, String... command) {
        return new CommandResult(null, 0, "");
    }

    @Override
    public CommandResult executeInNetworkNamespace(NodeAgentContext context, String... command) {
        return new CommandResult(null, 0, "");
    }

    @Override
    public void pullImage(TaskContext context, DockerImage image, RegistryCredentials registryCredentials) {
        String imageId = image.asString();
        ImageDownload imageDownload = images.computeIfAbsent(imageId, (ignored) -> new ImageDownload(new Image(imageId, List.of(imageId))));
        if (!asyncImageDownload) {
            imageDownload.complete();
        }
        imageDownload.awaitCompletion();
    }

    @Override
    public boolean hasImage(TaskContext context, DockerImage image) {
        ImageDownload download = images.get(image.asString());
        return download != null && download.isComplete();
    }

    @Override
    public void removeImage(TaskContext context, String id) {
        images.remove(id);
    }

    @Override
    public List<Image> listImages(TaskContext context) {
        return images.values().stream()
                     .filter(ImageDownload::isComplete)
                     .map(ImageDownload::image)
                     .collect(Collectors.toUnmodifiableList());
    }

    private Container requireContainer(ContainerName name) {
        return requireContainer(name, null);
    }

    private Container requireContainer(ContainerName name, PartialContainer.State wantedState) {
        Container container = containers.get(name);
        if (container == null) throw new IllegalArgumentException("No such container: " + name);
        if (wantedState != null && container.state() != wantedState) throw new IllegalArgumentException("Container is " + container.state() + ", wanted " + wantedState);
        return container;
    }

    public Container createContainer(NodeAgentContext context, PartialContainer.State state, ContainerResources containerResources) {
        return new Container(new ContainerId("id-of-" + context.containerName()),
                             context.containerName(),
                             Instant.EPOCH,
                             state,
                             "image-id",
                             context.node().wantedDockerImage().get(),
                             Map.of(),
                             41,
                             42,
                             context.hostname().value(),
                             containerResources,
                             List.of(),
                             true);
    }

    private static class ImageDownload {

        private final Image image;
        private final CountDownLatch done = new CountDownLatch(1);

        ImageDownload(Image image) {
            this.image = Objects.requireNonNull(image);
        }

        Image image() {
            return image;
        }

        boolean isComplete() {
            return done.getCount() == 0;
        }

        void complete() {
            done.countDown();
        }

        void awaitCompletion() {
            try {
                done.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

    }

}
