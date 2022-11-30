// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container.image;

import com.yahoo.config.provision.DockerImage;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.component.TestTaskContext;
import com.yahoo.vespa.hosted.node.admin.container.Container;
import com.yahoo.vespa.hosted.node.admin.container.ContainerEngineMock;
import com.yahoo.vespa.hosted.node.admin.container.ContainerId;
import com.yahoo.vespa.hosted.node.admin.container.ContainerName;
import com.yahoo.vespa.hosted.node.admin.container.ContainerResources;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author freva
 * @author mpolden
 */
public class ContainerImagePrunerTest {

    private final Tester tester = new Tester();

    @Test
    void noImagesMeansNoUnusedImages() {
        tester.withExistingImages()
                .expectDeletedImages();
    }

    @Test
    void singleImageWithoutContainersIsUnused() {
        tester.withExistingImages(image("image-1"))
                // Even though nothing is using the image, we will keep it for at least 1h
                .expectDeletedImagesAfterMinutes(0)
                .expectDeletedImagesAfterMinutes(30)
                .expectDeletedImagesAfterMinutes(30, "image-1");
    }

    @Test
    void singleImageWithContainerIsUsed() {
        tester.withExistingImages(image("image-1"))
                .withExistingContainers(container("container-1", "image-1"))
                .expectDeletedImages();
    }

    @Test
    void multipleUnusedImagesAreIdentified() {
        tester.withExistingImages(image("image-1"), image("image-2"))
                .expectDeletedImages("image-1", "image-2");
    }

    @Test
    void unusedImagesWithMultipleTags() {
        tester.withExistingImages(image("image-1", "vespa-6", "vespa-6.28", "vespa:latest"))
                .expectDeletedImages("vespa-6", "vespa-6.28", "vespa:latest");
    }

    @Test
    void unusedImagesWithMultipleUntagged() {
        tester.withExistingImages(image("image1", "<none>:<none>"),
                image("image2", "<none>:<none>"))
                .expectDeletedImages("image1", "image2");
    }

    @Test
    void taggedImageWithNoContainersIsUnused() {
        tester.withExistingImages(image("image-1", "vespa-6"))
                .expectDeletedImages("vespa-6");
    }

    @Test
    void reDownloadingImageIsNotImmediatelyDeleted() {
        tester.withExistingImages(image("image"))
                .expectDeletedImages("image") // After 1h we delete image
                .expectDeletedImagesAfterMinutes(0) // image is immediately re-downloaded, but is not deleted
                .expectDeletedImagesAfterMinutes(10)
                .expectDeletedImages("image"); // 1h after re-download it is deleted again
    }

    @Test
    void reDownloadingImageIsNotImmediatelyDeletedWhenDeletingByTag() {
        tester.withExistingImages(image("image", "my-tag"))
                .expectDeletedImages("my-tag") // After 1h we delete image
                .expectDeletedImagesAfterMinutes(0) // image is immediately re-downloaded, but is not deleted
                .expectDeletedImagesAfterMinutes(10)
                .expectDeletedImages("my-tag"); // 1h after re-download it is deleted again
    }

    /** Same scenario as in {@link #multipleUnusedImagesAreIdentified()} */
    @Test
    void doesNotDeleteExcludedByIdImages() {
        tester.withExistingImages(image("image-1"), image("image-2"))
                // Normally, image-1 should also be deleted, but because we exclude image-1 only image-2 is deleted
                .expectDeletedImages(List.of("image-1"), "image-2");
    }

    /** Same as in {@link #doesNotDeleteExcludedByIdImages()} but with tags */
    @Test
    void doesNotDeleteExcludedByTagImages() {
        tester.withExistingImages(image("image-1", "vespa:6.288.16"), image("image-2", "vespa:6.289.94"))
                .expectDeletedImages(List.of("vespa:6.288.16"), "vespa:6.289.94");
    }

    @Test
    void excludingNotDownloadedImageIsNoop() {
        tester.withExistingImages(image("image-1", "vespa:6.288.16"),
                image("image-2", "vespa:6.289.94"))
                .expectDeletedImages(List.of("vespa:6.300.1"), "vespa:6.288.16", "vespa:6.289.94", "rhel-6");
    }

    private static Image image(String id, String... tags) {
        return new Image(id, List.of(tags));
    }

    private static Container container(String name, String imageId) {
        return new Container(new ContainerId("id-of-" + name), new ContainerName(name), Instant.EPOCH,
                             Container.State.running, imageId, DockerImage.EMPTY, Map.of(),
                             42, 43, name + ".example.com", ContainerResources.UNLIMITED,
                             List.of(), true);
    }

    private static class Tester {

        private final ContainerEngineMock containerEngine = new ContainerEngineMock();
        private final TaskContext context = new TestTaskContext();
        private final ManualClock clock = new ManualClock();
        private final ContainerImagePruner pruner = new ContainerImagePruner(containerEngine, clock);
        private final Map<String, Integer> removalCountByImageId = new HashMap<>();

        private boolean initialized = false;

        private Tester withExistingImages(Image... images) {
            containerEngine.setImages(List.of(images));
            return this;
        }

        private Tester withExistingContainers(Container... containers) {
            containerEngine.addContainers(List.of(containers));
            return this;
        }

        private Tester expectDeletedImages(String... imageIds) {
            return expectDeletedImagesAfterMinutes(60, imageIds);
        }

        private Tester expectDeletedImages(List<String> excludedRefs, String... imageIds) {
            return expectDeletedImagesAfterMinutes(60, excludedRefs, imageIds);
        }

        private Tester expectDeletedImagesAfterMinutes(int minutesAfter, String... imageIds) {
            return expectDeletedImagesAfterMinutes(minutesAfter, List.of(), imageIds);
        }

        private Tester expectDeletedImagesAfterMinutes(int minutesAfter, List<String> excludedRefs, String... imageIds) {
            if (!initialized) {
                // Run once with a very long expiry to initialize internal state of existing images
                pruner.removeUnusedImages(context, List.of(), Duration.ofDays(999));
                initialized = true;
            }

            clock.advance(Duration.ofMinutes(minutesAfter));

            pruner.removeUnusedImages(context, excludedRefs, Duration.ofHours(1).minusSeconds(1));

            List.of(imageIds)
                  .forEach(imageId -> {
                      int newValue = removalCountByImageId.getOrDefault(imageId, 0) + 1;
                      removalCountByImageId.put(imageId, newValue);

                      assertTrue(containerEngine.listImages(context).stream().noneMatch(image -> image.id().equals(imageId)),
                                 "Image " + imageId + " removed");
                  });
            return this;
        }
    }

}
