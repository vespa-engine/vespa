// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

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
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.yahoo.vespa.defaults.Defaults;
import org.junit.Test;
import org.mockito.Matchers;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author tonytv
 */
public class DockerImplTest {
    @Test
    public void data_directories_are_mounted_in_from_the_host() {
        List<Bind> binds = DockerImpl.applicationStorageToMount(new ContainerName("my-container"));

        String dataDirectory = Defaults.getDefaults().vespaHome() + "logs";
        String directoryOnHost = "/home/docker/container-storage/my-container" + dataDirectory;
        assertThat(binds, hasItem(Bind.parse(directoryOnHost + ":" + dataDirectory)));
    }

    @Ignore
    @Test
    public void vespaVersionIsParsed() {
        assertThat(DockerImpl.parseVespaVersion("5.119.53"), is(Optional.of("5.119.53")));
    }

    @Test
    public void vespaVersionIsParsedWithSpacesAndNewlines() {
        assertThat(DockerImpl.parseVespaVersion("5.119.53\n"), is(Optional.of("5.119.53")));
        assertThat(DockerImpl.parseVespaVersion(" 5.119.53 \n"), is(Optional.of("5.119.53")));
        assertThat(DockerImpl.parseVespaVersion("\n 5.119.53 \n"), is(Optional.of("5.119.53")));
    }

    @Test
    public void vespaVersionIsParsedWithIrregularVersionScheme() {
        assertThat(DockerImpl.parseVespaVersion("7.2"), is(Optional.of("7.2")));
        assertThat(DockerImpl.parseVespaVersion("8.0-beta"), is(Optional.of("8.0-beta")));
        assertThat(DockerImpl.parseVespaVersion("foo"), is(Optional.of("foo")));
        assertThat(DockerImpl.parseVespaVersion("119"), is(Optional.of("119")));
    }

    @Test
    public void vespaVersionIsNotParsedFromNull() {
        assertThat(DockerImpl.parseVespaVersion(null), is(Optional.empty()));
    }

    @Test
    public void vespaVersionIsNotParsedFromEmptyString() {
        assertThat(DockerImpl.parseVespaVersion(""), is(Optional.empty()));
    }

    @Test
    public void vespaVersionIsNotParsedFromUnexpectedContent() {
        assertThat(DockerImpl.parseVespaVersion("No such command 'vespanodectl'"), is(Optional.empty()));
    }

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
        private List<com.github.dockerjava.api.model.Container> existingContainers = Collections.emptyList();

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
            final ListImagesCmd listImagesCmd = mock(ListImagesCmd.class);
            final ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);

            when(dockerClient.listImagesCmd()).thenReturn(listImagesCmd);
            when(listImagesCmd.withShowAll(true)).thenReturn(listImagesCmd);
            when(listImagesCmd.exec()).thenReturn(existingImages);

            when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
            when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
            when(listContainersCmd.exec()).thenReturn(existingContainers);

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

        public com.github.dockerjava.api.model.Container toContainer() {
            return createFrom(com.github.dockerjava.api.model.Container.class, this);
        }
    }
}
