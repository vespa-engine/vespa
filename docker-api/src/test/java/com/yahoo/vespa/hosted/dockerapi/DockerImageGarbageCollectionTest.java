// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.Image;
import org.junit.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author freva
 */
public class DockerImageGarbageCollectionTest {
    @Test
    public void noImagesMeansNoUnusedImages() {
        new ImageGcTester(0)
                .withExistingImages()
                .expectUnusedImages();
    }

    @Test
    public void singleImageWithoutContainersIsUnused() {
        new ImageGcTester(0)
                .withExistingImages(new ImageBuilder("image-1"))
                .expectUnusedImages("image-1");
    }

    @Test
    public void singleImageWithContainerIsUsed() {
        new ImageGcTester(0)
                .withExistingImages(ImageBuilder.forId("image-1"))
                .andExistingContainers(ContainerBuilder.forId("container-1").withImageId("image-1"))
                .expectUnusedImages();
    }

    @Test
    public void multipleUnusedImagesAreIdentified() {
        new ImageGcTester(0)
                .withExistingImages(
                        ImageBuilder.forId("image-1"),
                        ImageBuilder.forId("image-2"))
                .expectUnusedImages("image-1", "image-2");
    }

    @Test
    public void multipleUnusedLeavesAreIdentified() {
        new ImageGcTester(0)
                .withExistingImages(
                        ImageBuilder.forId("parent-image"),
                        ImageBuilder.forId("image-1").withParentId("parent-image"),
                        ImageBuilder.forId("image-2").withParentId("parent-image"))
                .expectUnusedImages("image-1", "image-2", "parent-image");
    }

    @Test
    public void unusedLeafWithUsedSiblingIsIdentified() {
        new ImageGcTester(0)
                .withExistingImages(
                        ImageBuilder.forId("parent-image"),
                        ImageBuilder.forId("image-1").withParentId("parent-image").withTags("latest"),
                        ImageBuilder.forId("image-2").withParentId("parent-image").withTags("1.24"))
                .andExistingContainers(ContainerBuilder.forId("vespa-node-1").withImageId("image-1"))
                .expectUnusedImages("1.24");
    }

    @Test
    public void unusedImagesWithMultipleTags() {
        new ImageGcTester(0)
                .withExistingImages(
                        ImageBuilder.forId("parent-image"),
                        ImageBuilder.forId("image-1").withParentId("parent-image")
                                .withTags("vespa-6", "vespa-6.28", "vespa:latest"))
                .expectUnusedImages("vespa-6", "vespa-6.28", "vespa:latest", "parent-image");
    }

    @Test
    public void taggedImageWithNoContainersIsUnused() {
        new ImageGcTester(0)
                .withExistingImages(ImageBuilder.forId("image-1").withTags("vespa-6"))
                .expectUnusedImages("vespa-6");
    }

    @Test
    public void unusedImagesWithSimpleImageGc() {
        new ImageGcTester(20)
                .withExistingImages(
                        ImageBuilder.forId("parent-image").withLastUsedMinutesAgo(25),
                        ImageBuilder.forId("image-1").withParentId("parent-image").withLastUsedMinutesAgo(5))
                .expectUnusedImages();
    }

    @Test
    public void unusedImagesWithImageGc() {
        new ImageGcTester(20)
                .withExistingImages(
                        ImageBuilder.forId("parent-1").withLastUsedMinutesAgo(40),
                        ImageBuilder.forId("parent-2").withTags("p-tag:1").withLastUsedMinutesAgo(10),
                        ImageBuilder.forId("image-1-1").withParentId("parent-1").withTags("i-tag:1", "i-tag:2", "i-tag-3").withLastUsedMinutesAgo(5),
                        ImageBuilder.forId("image-1-2").withParentId("parent-1").withLastUsedMinutesAgo(25),
                        ImageBuilder.forId("image-2-1").withParentId("parent-2").withTags("i-tag:4").withLastUsedMinutesAgo(30))
                .andExistingContainers(
                        ContainerBuilder.forId("cont-1").withImageId("image-1-1"))
                .expectUnusedImages("image-1-2", "i-tag:4");
    }

    private static class ImageGcTester {
        private static DockerImageGarbageCollector imageGC;

        private List<Image> existingImages = Collections.emptyList();
        private List<com.github.dockerjava.api.model.Container> existingContainers = Collections.emptyList();

        private ImageGcTester(int imageGcMinTimeInMinutes) {
            imageGC = new DockerImageGarbageCollector(Duration.ofMinutes(imageGcMinTimeInMinutes));
        }

        private ImageGcTester withExistingImages(final ImageBuilder... images) {
            this.existingImages = Arrays.stream(images)
                    .map(ImageBuilder::toImage)
                    .collect(Collectors.toList());
            return this;
        }

        private ImageGcTester andExistingContainers(final ContainerBuilder... containers) {
            this.existingContainers = Arrays.stream(containers)
                    .map(ContainerBuilder::toContainer)
                    .collect(Collectors.toList());
            return this;
        }

        private void expectUnusedImages(final String... imageIds) {
            final List<DockerImage> expectedUnusedImages = Arrays.stream(imageIds)
                    .map(DockerImage::new)
                    .collect(Collectors.toList());

            assertThat(imageGC.getUnusedDockerImages(existingImages, existingContainers), is(expectedUnusedImages));
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
        private String parentId = ""; // docker-java returns empty string and not null if the parent is not present

        @JsonProperty("RepoTags")
        private String[] repoTags = null;

        private ImageBuilder(String id) { this.id = id; }

        private static ImageBuilder forId(String id) { return new ImageBuilder(id); }
        private ImageBuilder withParentId(String parentId) { this.parentId = parentId; return this; }
        private ImageBuilder withTags(String... tags) { this.repoTags = tags; return this; }
        private ImageBuilder withLastUsedMinutesAgo(int minutesAgo) {
            ImageGcTester.imageGC.updateLastUsedTimeFor(id, Instant.now().minus(Duration.ofMinutes(minutesAgo)));
            return this;
        }

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
