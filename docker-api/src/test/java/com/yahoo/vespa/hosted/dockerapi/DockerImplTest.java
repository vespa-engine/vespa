// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectExecCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import org.junit.Test;
import org.mockito.Matchers;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author tonytv
 */
public class DockerImplTest {
    @Test
    public void testExecuteCompletes() throws Exception {
        final String containerId = "container-id";
        final String[] command = new String[] {"/bin/ls", "-l"};
        final String execId = "exec-id";
        final int exitCode = 3;

        final DockerClient dockerClient = mock(DockerClient.class);

        final ExecCreateCmdResponse response = mock(ExecCreateCmdResponse.class);
        when(response.getId()).thenReturn(execId);

        final ExecCreateCmd execCreateCmd = mock(ExecCreateCmd.class);
        when(dockerClient.execCreateCmd(any(String.class))).thenReturn(execCreateCmd);
        when(execCreateCmd.withCmd(Matchers.<String>anyVararg())).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStdout(any(Boolean.class))).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStderr(any(Boolean.class))).thenReturn(execCreateCmd);
        when(execCreateCmd.exec()).thenReturn(response);

        final ExecStartCmd execStartCmd = mock(ExecStartCmd.class);
        when(dockerClient.execStartCmd(any(String.class))).thenReturn(execStartCmd);
        when(execStartCmd.exec(any(ExecStartResultCallback.class))).thenReturn(mock(ExecStartResultCallback.class));

        final InspectExecCmd inspectExecCmd = mock(InspectExecCmd.class);
        final InspectExecResponse state = mock(InspectExecResponse.class);
        when(dockerClient.inspectExecCmd(any(String.class))).thenReturn(inspectExecCmd);
        when(inspectExecCmd.exec()).thenReturn(state);
        when(state.isRunning()).thenReturn(false);
        when(state.getExitCode()).thenReturn(exitCode);

        final Docker docker = new DockerImpl(dockerClient);
        final ProcessResult result = docker.executeInContainer(new ContainerName(containerId), command);
        assertThat(result.getExitStatus(), is(exitCode));
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
                .andExistingContainers(ContainerBuilder.forId("container-1").withImageId("image-1"))
                .expectUnusedImages();
    }

    @Test
    public void onlyLeafImageIsUnused() throws Exception {
        ImageGcTester
                .withExistingImages(
                        ImageBuilder.forId("parent-image"),
                        ImageBuilder.forId("leaf-image").withParentId("parent-image"))
                .expectUnusedImages("leaf-image", "parent-image");
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
                .expectUnusedImages("image-1", "image-2", "parent-image");
    }

    @Test
    public void unusedLeafWithUsedSiblingIsIdentified() throws Exception {
        ImageGcTester
                .withExistingImages(
                        ImageBuilder.forId("parent-image"),
                        ImageBuilder.forId("image-1").withParentId("parent-image").withTag("latest"),
                        ImageBuilder.forId("image-2").withParentId("parent-image").withTag("1.24"))
                .andExistingContainers(ContainerBuilder.forId("vespa-node-1").withImageId("image-1"))
                .expectUnusedImages("1.24");
    }

    @Test
    public void unusedImagesWithMultipleTags() throws Exception {
        ImageGcTester
                .withExistingImages(
                        ImageBuilder.forId("parent-image"),
                        ImageBuilder.forId("image-1").withParentId("parent-image")
                                .withTag("vespa-6").withTag("vespa-6.28").withTag("vespa:latest"))
                .expectUnusedImages("vespa-6", "vespa-6.28", "vespa:latest", "parent-image");
    }

    @Test
    public void taggedImageWithNoContainersIsUnused() throws Exception {
        ImageGcTester
                .withExistingImages(ImageBuilder.forId("image-1").withTag("vespa-6"))
                .expectUnusedImages("vespa-6");
    }

    @Test
    public void unusedImagesWithMultipleTagsAndExcept() throws Exception {
        ImageGcTester
                .withExistingImages(
                        ImageBuilder.forId("parent-image"),
                        ImageBuilder.forId("image-1").withParentId("parent-image")
                                .withTag("vespa-6").withTag("vespa-6.28").withTag("vespa:latest"))
                .andExceptImages("vespa-6.28")
                .expectUnusedImages("vespa-6", "vespa:latest");
    }

    @Test
    public void unusedImagesWithExcept() throws Exception {
        ImageGcTester
                .withExistingImages(
                        ImageBuilder.forId("parent-1"),
                        ImageBuilder.forId("parent-2").withTag("p-tag:1"),
                        ImageBuilder.forId("image-1-1").withParentId("parent-1").withTag("i-tag:1").withTag("i-tag:2").withTag("i-tag-3"),
                        ImageBuilder.forId("image-1-2").withParentId("parent-1"),
                        ImageBuilder.forId("image-2-1").withParentId("parent-2").withTag("i-tag:4"))
                .andExceptImages("image-1-2")
                .andExistingContainers(
                        ContainerBuilder.forId("cont-1").withImageId("image-1-1"))
                .expectUnusedImages("i-tag:4", "p-tag:1");
    }

    private static class ImageGcTester {
        private final List<Image> existingImages;
        private Set<DockerImage> exceptImages = Collections.emptySet();
        private List<com.github.dockerjava.api.model.Container> existingContainers = Collections.emptyList();

        private ImageGcTester(final List<Image> images) {
            this.existingImages = images;
        }

        private static ImageGcTester withExistingImages(final ImageBuilder... images) {
            final List<Image> existingImages = Arrays.stream(images)
                    .map(ImageBuilder::toImage)
                    .collect(Collectors.toList());
            return new ImageGcTester(existingImages);
        }

        private ImageGcTester andExistingContainers(final ContainerBuilder... containers) {
            this.existingContainers = Arrays.stream(containers)
                    .map(ContainerBuilder::toContainer)
                    .collect(Collectors.toList());
            return this;
        }

        private ImageGcTester andExceptImages(final String... images) {
            this.exceptImages = Arrays.stream(images)
                    .map(DockerImage::new)
                    .collect(Collectors.toSet());
            return this;
        }

        private void expectUnusedImages(final String... imageIds) throws Exception {
            final DockerClient dockerClient = mock(DockerClient.class);
            final DockerImpl docker = new DockerImpl(dockerClient);
            final ListImagesCmd listImagesCmd = mock(ListImagesCmd.class);
            final ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);

            when(dockerClient.listImagesCmd()).thenReturn(listImagesCmd);
            when(listImagesCmd.withShowAll(true)).thenReturn(listImagesCmd);
            when(listImagesCmd.exec()).thenReturn(existingImages);

            when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
            when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
            when(listContainersCmd.exec()).thenReturn(existingContainers);

            final List<DockerImage> expectedUnusedImages = Arrays.stream(imageIds)
                    .map(DockerImage::new)
                    .collect(Collectors.toList());
            assertThat(
                    docker.getUnusedDockerImages(exceptImages),
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
            return new ObjectMapper().readValue(serialized, toClass);
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

        private static ImageBuilder forId(String id) { return new ImageBuilder(id); }
        private ImageBuilder withParentId(String parentId) { this.parentId = parentId; return this; }
        private ImageBuilder withTag(String tag) { this.repoTags.add(tag); return this; }

        private Image toImage() { return createFrom(Image.class, this); }
    }

    // Workaround for Container class that can't be instantiated directly in Java (instantiate via Jackson instead).
    private static class ContainerBuilder {
        // Json property names must match exactly the property names in the Container class.
        @JsonProperty("Id")
        private final String id;

        @JsonProperty("ImageID")
        private String imageId;

        private ContainerBuilder(String id) { this.id = id; }
        private static ContainerBuilder forId(final String id) { return new ContainerBuilder(id); }
        private ContainerBuilder withImageId(String imageId) { this.imageId = imageId; return this; }

        private com.github.dockerjava.api.model.Container toContainer() {
            return createFrom(com.github.dockerjava.api.model.Container.class, this);
        }
    }
}
