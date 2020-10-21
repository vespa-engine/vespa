// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.Image;
import com.yahoo.test.ManualClock;
import org.junit.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author freva
 */
public class DockerImageGarbageCollectionTest {

    private final ImageGcTester gcTester = new ImageGcTester();

    @Test
    public void noImagesMeansNoUnusedImages() {
        gcTester.withExistingImages()
                .expectDeletedImages();
    }

    @Test
    public void singleImageWithoutContainersIsUnused() {
        gcTester.withExistingImages(new ImageBuilder("image-1"))
                // Even though nothing is using the image, we will keep it for at least 1h
                .expectDeletedImagesAfterMinutes(0)
                .expectDeletedImagesAfterMinutes(30)
                .expectDeletedImagesAfterMinutes(30, "image-1");
    }

    @Test
    public void singleImageWithContainerIsUsed() {
        gcTester.withExistingImages(ImageBuilder.forId("image-1"))
                .andExistingContainers(ContainerBuilder.forId("container-1").withImageId("image-1"))
                .expectDeletedImages();
    }

    @Test
    public void multipleUnusedImagesAreIdentified() {
        gcTester.withExistingImages(
                        ImageBuilder.forId("image-1"),
                        ImageBuilder.forId("image-2"))
                .expectDeletedImages("image-1", "image-2");
    }

    @Test
    public void multipleUnusedLeavesAreIdentified() {
        gcTester.withExistingImages(
                        ImageBuilder.forId("parent-image"),
                        ImageBuilder.forId("image-1").withParentId("parent-image"),
                        ImageBuilder.forId("image-2").withParentId("parent-image"))
                .expectDeletedImages("image-1", "image-2", "parent-image");
    }

    @Test
    public void unusedLeafWithUsedSiblingIsIdentified() {
        gcTester.withExistingImages(
                        ImageBuilder.forId("parent-image"),
                        ImageBuilder.forId("image-1").withParentId("parent-image").withTags("latest"),
                        ImageBuilder.forId("image-2").withParentId("parent-image").withTags("1.24"))
                .andExistingContainers(ContainerBuilder.forId("vespa-node-1").withImageId("image-1"))
                .expectDeletedImages("1.24"); // Deleting the only tag will delete the image
    }

    @Test
    public void unusedImagesWithMultipleTags() {
        gcTester.withExistingImages(
                        ImageBuilder.forId("parent-image"),
                        ImageBuilder.forId("image-1").withParentId("parent-image")
                                .withTags("vespa-6", "vespa-6.28", "vespa:latest"))
                .expectDeletedImages("vespa-6", "vespa-6.28", "vespa:latest", "parent-image");
    }


    @Test
    public void unusedImagesWithMultipleUntagged() {
        gcTester.withExistingImages(ImageBuilder.forId("image1")
                                                .withTags("<none>:<none>"),
                                    ImageBuilder.forId("image2")
                                                .withTags("<none>:<none>"))
                .expectDeletedImages("image1", "image2");
    }

    @Test
    public void taggedImageWithNoContainersIsUnused() {
        gcTester.withExistingImages(ImageBuilder.forId("image-1").withTags("vespa-6"))
                .expectDeletedImages("vespa-6");
    }

    @Test
    public void unusedImagesWithSimpleImageGc() {
        gcTester.withExistingImages(ImageBuilder.forId("parent-image"))
                .expectDeletedImagesAfterMinutes(30)
                .withExistingImages(
                        ImageBuilder.forId("parent-image"),
                        ImageBuilder.forId("image-1").withParentId("parent-image"))
                .expectDeletedImagesAfterMinutes(0)
                .expectDeletedImagesAfterMinutes(30)
                // At this point, parent-image has been unused for 1h, but image-1 depends on parent-image and it has
                // only been unused for 30m, so we cannot delete parent-image yet. 30 mins later both can be removed
                .expectDeletedImagesAfterMinutes(30, "image-1", "parent-image");
    }

    @Test
    public void reDownloadingImageIsNotImmediatelyDeleted() {
        gcTester.withExistingImages(ImageBuilder.forId("image"))
                .expectDeletedImages("image") // After 1h we delete image
                .expectDeletedImagesAfterMinutes(0) // image is immediately re-downloaded, but is not deleted
                .expectDeletedImagesAfterMinutes(10)
                .expectDeletedImages("image"); // 1h after re-download it is deleted again
    }

    @Test
    public void reDownloadingImageIsNotImmediatelyDeletedWhenDeletingByTag() {
        gcTester.withExistingImages(ImageBuilder.forId("image").withTags("image-1", "my-tag"))
                .expectDeletedImages("image-1", "my-tag") // After 1h we delete image
                .expectDeletedImagesAfterMinutes(0) // image is immediately re-downloaded, but is not deleted
                .expectDeletedImagesAfterMinutes(10)
                .expectDeletedImages("image-1", "my-tag"); // 1h after re-download it is deleted again
    }

    /** Same scenario as in {@link #multipleUnusedImagesAreIdentified()} */
    @Test
    public void doesNotDeleteExcludedByIdImages() {
        gcTester.withExistingImages(
                    ImageBuilder.forId("parent-image"),
                    ImageBuilder.forId("image-1").withParentId("parent-image"),
                    ImageBuilder.forId("image-2").withParentId("parent-image"))
                // Normally, image-1 and parent-image should also be deleted, but because we exclude image-1
                // we cannot delete parent-image, so only image-2 is deleted
                .expectDeletedImages(List.of("image-1"), "image-2");
    }

    /** Same as in {@link #doesNotDeleteExcludedByIdImages()} but with tags */
    @Test
    public void doesNotDeleteExcludedByTagImages() {
        gcTester.withExistingImages(
                    ImageBuilder.forId("parent-image").withTags("rhel-6"),
                    ImageBuilder.forId("image-1").withParentId("parent-image").withTags("vespa:6.288.16"),
                    ImageBuilder.forId("image-2").withParentId("parent-image").withTags("vespa:6.289.94"))
                .expectDeletedImages(List.of("vespa:6.288.16"), "vespa:6.289.94");
    }

    @Test
    public void exludingNotDownloadedImageIsNoop() {
        gcTester.withExistingImages(
                    ImageBuilder.forId("parent-image").withTags("rhel-6"),
                    ImageBuilder.forId("image-1").withParentId("parent-image").withTags("vespa:6.288.16"),
                    ImageBuilder.forId("image-2").withParentId("parent-image").withTags("vespa:6.289.94"))
                .expectDeletedImages(List.of("vespa:6.300.1"), "vespa:6.288.16", "vespa:6.289.94", "rhel-6");
    }

    private class ImageGcTester {
        private final DockerEngine docker = mock(DockerEngine.class);
        private final ManualClock clock = new ManualClock();
        private final DockerImageGarbageCollector imageGC = new DockerImageGarbageCollector(docker, clock);
        private final Map<String, Integer> numDeletes = new HashMap<>();
        private boolean initialized = false;

        private ImageGcTester withExistingImages(ImageBuilder... images) {
            when(docker.listAllImages()).thenReturn(Arrays.stream(images)
                    .map(ImageBuilder::toImage)
                    .collect(Collectors.toList()));
            return this;
        }

        private ImageGcTester andExistingContainers(ContainerBuilder... containers) {
            when(docker.listAllContainers()).thenReturn(Arrays.stream(containers)
                    .map(ContainerBuilder::toContainer)
                    .collect(Collectors.toList()));
            return this;
        }

        private ImageGcTester expectDeletedImages(String... imageIds) {
            return expectDeletedImagesAfterMinutes(60, imageIds);
        }

        private ImageGcTester expectDeletedImages(List<String> except, String... imageIds) {
            return expectDeletedImagesAfterMinutes(60, except, imageIds);
        }
        private ImageGcTester expectDeletedImagesAfterMinutes(int minutesAfter, String... imageIds) {
            return expectDeletedImagesAfterMinutes(minutesAfter, Collections.emptyList(), imageIds);
        }

        private ImageGcTester expectDeletedImagesAfterMinutes(int minutesAfter, List<String> except, String... imageIds) {
            if (!initialized) {
                // Run once with a very long expiry to initialize internal state of existing images
                imageGC.deleteUnusedDockerImages(Collections.emptyList(), Duration.ofDays(999));
                initialized = true;
            }

            clock.advance(Duration.ofMinutes(minutesAfter));

            imageGC.deleteUnusedDockerImages(except, Duration.ofHours(1).minusSeconds(1));

            Arrays.stream(imageIds)
                  .forEach(imageId -> {
                      int newValue = numDeletes.getOrDefault(imageId, 0) + 1;
                      numDeletes.put(imageId, newValue);
                      verify(docker, times(newValue)).deleteImage(eq(imageId));
                  });

            verify(docker, times(numDeletes.values().stream().mapToInt(i -> i).sum())).deleteImage(any());
            return this;
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
