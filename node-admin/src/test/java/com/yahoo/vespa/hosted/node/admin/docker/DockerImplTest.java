// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.ObjectMapperProvider;
import com.spotify.docker.client.messages.ExecState;
import com.spotify.docker.client.messages.Image;
import com.yahoo.vespa.defaults.Defaults;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author tonytv
 */
public class DockerImplTest {
    @Test
    public void data_directories_are_mounted_in_from_the_host() {
        List<String> binds = DockerImpl.applicationStorageToMount("my-container");

        String dataDirectory = Defaults.getDefaults().vespaHome() + "logs";
        String directoryOnHost = "/home/docker/container-storage/my-container" + dataDirectory;
        assertThat(binds, hasItem(directoryOnHost + ":" + dataDirectory));
    }

    @Test
    public void locationOfContainerStorageInNodeAdmin() {
        assertEquals(
                "/host/home/docker/container-storage/docker1-1",
                DockerImpl.applicationStoragePathForNodeAdmin("docker1-1").toString());
    }

    @Test
    public void repeatedPollsOfSameImageAreNotScheduled() throws Exception {
        final DockerClient dockerClient = mock(DockerClient.class);
        final CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocationOnMock -> {
            latch.await();
            return null;
        }).when(dockerClient).pull(any(String.class));
        final Docker docker = new DockerImpl(dockerClient);
        final DockerImage dockerImage = new DockerImage("test-image");

        final CompletableFuture<DockerImage> asyncPollFuture1 = docker.pullImageAsync(dockerImage);

        assertThat(asyncPollFuture1.isDone(), is(false));

        final CompletableFuture<DockerImage> asyncPollFuture2 = docker.pullImageAsync(dockerImage);

        assertThat(asyncPollFuture2, is(sameInstance(asyncPollFuture1)));

        latch.countDown();  // This should make the future complete shortly.
        asyncPollFuture1.get(5, TimeUnit.SECONDS);

        // Now that the previous poll is completed, a new poll may be scheduled.
        final CompletableFuture<DockerImage> asyncPollFuture3 = docker.pullImageAsync(dockerImage);

        assertThat(asyncPollFuture3, is(not(sameInstance(asyncPollFuture1))));
    }

    @Test
    public void testExecuteCompletes() throws Exception {
        final DockerClient dockerClient = mock(DockerClient.class);
        final String containerId = "container-id";
        final String[] command = new String[] {"/bin/ls", "-l"};
        final String execId = "exec-id";
        when(dockerClient.execCreate(
                eq(containerId),
                eq(command),
                anyVararg(),
                anyVararg())).thenReturn(execId);

        final LogStream logStream = mock(LogStream.class);
        when(dockerClient.execStart(execId)).thenReturn(logStream);

        final ExecState execState = mock(ExecState.class);
        when(dockerClient.execInspect(execId)).thenReturn(execState);

        when(execState.running()).thenReturn(false);
        final int exitCode = 3;
        when(execState.exitCode()).thenReturn(exitCode);

        final String commandOutput = "command output";
        when(logStream.readFully()).thenReturn(commandOutput);

        final Docker docker = new DockerImpl(dockerClient);
        final ProcessResult result = docker.executeInContainer(new ContainerName(containerId), command);
        assertThat(result.getExitStatus(), is(exitCode));
        assertThat(result.getOutput(), is(commandOutput));
    }

    @Test
    public void noImagesMeansNoUnusedImages() throws Exception {
        ImageGcTester
                .withExistingImages()
                .expectUnusedImages();
    }

    @Test
    public void singleImageWithoutContainersIsUnused() throws Exception {
        ImageGcTester
                .withExistingImages(new ImageBuilder("image-1"))
                .expectUnusedImages("image-1");
    }

    @Test
    public void singleImageWithContainerIsUsed() throws Exception {
        ImageGcTester
                .withExistingImages(ImageBuilder.forId("image-1"))
                .andExistingContainers(ContainerBuilder.forId("container-1").withImage("image-1"))
                .expectUnusedImages();
    }

    @Test
    public void onlyLeafImageIsUnused() throws Exception {
        ImageGcTester
                .withExistingImages(
                        ImageBuilder.forId("parent-image"),
                        ImageBuilder.forId("leaf-image").withParentId("parent-image"))
                .expectUnusedImages("leaf-image");
    }

    @Test
    public void multipleUnusedImagesAreIdentified() throws Exception {
        ImageGcTester
                .withExistingImages(
                        ImageBuilder.forId("image-1"),
                        ImageBuilder.forId("image-2"))
                .expectUnusedImages("image-1", "image-2");
    }

    @Test
    public void multipleUnusedLeavesAreIdentified() throws Exception {
        ImageGcTester
                .withExistingImages(
                        ImageBuilder.forId("parent-image"),
                        ImageBuilder.forId("image-1").withParentId("parent-image"),
                        ImageBuilder.forId("image-2").withParentId("parent-image"))
                .expectUnusedImages("image-1", "image-2");
    }

    @Test
    public void unusedLeafWithUsedSiblingIsIdentified() throws Exception {
        ImageGcTester
                .withExistingImages(
                        ImageBuilder.forId("parent-image"),
                        ImageBuilder.forId("image-1").withParentId("parent-image"),
                        ImageBuilder.forId("image-2").withParentId("parent-image"))
                .andExistingContainers(ContainerBuilder.forId("vespa-node-1").withImage("image-1"))
                .expectUnusedImages("image-2");
    }

    @Test
    public void containerCanReferToImageByTag() throws Exception {
        ImageGcTester
                .withExistingImages(ImageBuilder.forId("image-1").withTag("vespa-6"))
                .andExistingContainers(ContainerBuilder.forId("vespa-node-1").withImage("vespa-6"))
                .expectUnusedImages();
    }

    @Test
    public void taggedImageWithNoContainersIsUnused() throws Exception {
        ImageGcTester
                .withExistingImages(ImageBuilder.forId("image-1").withTag("vespa-6"))
                .expectUnusedImages("image-1");
    }

    private static class ImageGcTester {
        private final List<Image> existingImages;
        private List<com.spotify.docker.client.messages.Container> existingContainers = Collections.emptyList();

        private ImageGcTester(final List<Image> images) {
            this.existingImages = images;
        }

        public static ImageGcTester withExistingImages(final ImageBuilder... images) {
            final List<Image> existingImages = Arrays.stream(images)
                    .map(ImageBuilder::toImage)
                    .collect(Collectors.toList());
            return new ImageGcTester(existingImages);
        }

        public ImageGcTester andExistingContainers(final ContainerBuilder... containers) {
            this.existingContainers = Arrays.stream(containers)
                    .map(ContainerBuilder::toContainer)
                    .collect(Collectors.toList());
            return this;
        }

        public void expectUnusedImages(final String... imageIds) throws Exception {
            final DockerClient dockerClient = mock(DockerClient.class);
            final Docker docker = new DockerImpl(dockerClient);
            when(dockerClient.listImages(anyVararg())).thenReturn(existingImages);
            when(dockerClient.listContainers(anyVararg())).thenReturn(existingContainers);
            final Set<DockerImage> expectedUnusedImages = Arrays.stream(imageIds)
                    .map(DockerImage::new)
                    .collect(Collectors.toSet());
            assertThat(
                    docker.getUnusedDockerImages(),
                    is(expectedUnusedImages));

        }
    }

    /**
     * Serializes object to a JSON string using Jackson, then deserializes it to an instance of toClass
     * (again using Jackson). This can be used to create Jackson classes with no public constructors.
     * @throws IllegalArgumentException if Jackson fails to serialize or deserialize.
     */
    private static <T> T createFrom(Class<T> toClass, Object object) throws IllegalArgumentException {
        final String serialized;
        try {
            serialized = new ObjectMapper().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize object " + object + " to "
                    + toClass + " with Jackson: " + e, e);
        }
        try {
            return new ObjectMapperProvider().getContext(null).readValue(serialized, toClass);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to convert " + serialized + " to "
                    + toClass + " with Jackson: " + e, e);
        }
    }

    // Workaround for Image class that can't be instantiated directly in Java (instantiate via Jackson instead).
    private static class ImageBuilder {
        // Json property names must match exactly the property names in the Image class.
        @JsonProperty("Id")
        private final String id;

        @JsonProperty("ParentId")
        private String parentId;

        @JsonProperty("RepoTags")
        private final List<String> repoTags = new LinkedList<>();

        private ImageBuilder(String id) { this.id = id; }

        public static ImageBuilder forId(String id) { return new ImageBuilder(id); }
        public ImageBuilder withParentId(String parentId) { this.parentId = parentId; return this; }
        public ImageBuilder withTag(String tag) { this.repoTags.add(tag); return this; }

        public Image toImage() { return createFrom(Image.class, this); }
    }

    // Workaround for Container class that can't be instantiated directly in Java (instantiate via Jackson instead).
    private static class ContainerBuilder {
        // Json property names must match exactly the property names in the Container class.
        @JsonProperty("Id")
        private final String id;

        @JsonProperty("Image")
        private String image;

        private ContainerBuilder(String id) { this.id = id; }
        private static ContainerBuilder forId(final String id) { return new ContainerBuilder(id); }
        public ContainerBuilder withImage(String image) { this.image = image; return this; }

        public com.spotify.docker.client.messages.Container toContainer() {
            return createFrom(com.spotify.docker.client.messages.Container.class, this);
        }
    }
}
