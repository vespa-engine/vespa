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
import com.yahoo.vespa.hosted.node.admin.container.image.ContainerImagePruner;
import com.yahoo.vespa.hosted.node.admin.container.image.Image;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertTrue;

/**
 * @author freva
 * @author mpolden
 */
public class ContainerImagePrunerTest {

    private final Tester tester = new Tester();

    @Test
    public void noImagesMeansNoUnusedImages() {
        tester.withExistingImages()
              .expectDeletedImages();
    }

    @Test
    public void singleImageWithoutContainersIsUnused() {
        tester.withExistingImages(image("image-1"))
              // Even though nothing is using the image, we will keep it for at least 1h
              .expectDeletedImagesAfterMinutes(0)
              .expectDeletedImagesAfterMinutes(30)
              .expectDeletedImagesAfterMinutes(30, "image-1");
    }

    @Test
    public void singleImageWithContainerIsUsed() {
        tester.withExistingImages(image("image-1"))
              .withExistingContainers(container("container-1", "image-1"))
              .expectDeletedImages();
    }


    @Test
    public void multipleUnusedImagesAreIdentified() {
        tester.withExistingImages(image("image-1"), image("image-2"))
              .expectDeletedImages("image-1", "image-2");
    }


    @Test
    public void multipleUnusedLeavesAreIdentified() {
        tester.withExistingImages(image("parent-image"),
                                  image("image-1", "parent-image"),
                                  image("image-2", "parent-image"))
              .expectDeletedImages("image-1", "image-2", "parent-image");
    }


    @Test
    public void unusedLeafWithUsedSiblingIsIdentified() {
        tester.withExistingImages(image("parent-image"),
                                  image("image-1", "parent-image", "latest"),
                                  image("image-2", "parent-image", "1.24"))
              .withExistingContainers(container("vespa-node-1", "image-1"))
              .expectDeletedImages("1.24"); // Deleting the only tag will delete the image
    }


    @Test
    public void unusedImagesWithMultipleTags() {
        tester.withExistingImages(image("parent-image"),
                                  image("image-1", "parent-image", "vespa-6", "vespa-6.28", "vespa:latest"))
              .expectDeletedImages("vespa-6", "vespa-6.28", "vespa:latest", "parent-image");
    }


    @Test
    public void unusedImagesWithMultipleUntagged() {
        tester.withExistingImages(image("image1", null, "<none>:<none>"),
                                  image("image2", null, "<none>:<none>"))
              .expectDeletedImages("image1", "image2");
    }


    @Test
    public void taggedImageWithNoContainersIsUnused() {
        tester.withExistingImages(image("image-1", null, "vespa-6"))
              .expectDeletedImages("vespa-6");
    }


    @Test
    public void unusedImagesWithSimpleImageGc() {
        tester.withExistingImages(image("parent-image"))
              .expectDeletedImagesAfterMinutes(30)
              .withExistingImages(image("parent-image"),
                                    image("image-1", "parent-image"))
              .expectDeletedImagesAfterMinutes(0)
              .expectDeletedImagesAfterMinutes(30)
              // At this point, parent-image has been unused for 1h, but image-1 depends on parent-image and it has
              // only been unused for 30m, so we cannot delete parent-image yet. 30 mins later both can be removed
              .expectDeletedImagesAfterMinutes(30, "image-1", "parent-image");
    }


    @Test
    public void reDownloadingImageIsNotImmediatelyDeleted() {
        tester.withExistingImages(image("image"))
              .expectDeletedImages("image") // After 1h we delete image
              .expectDeletedImagesAfterMinutes(0) // image is immediately re-downloaded, but is not deleted
              .expectDeletedImagesAfterMinutes(10)
              .expectDeletedImages("image"); // 1h after re-download it is deleted again
    }


    @Test
    public void reDownloadingImageIsNotImmediatelyDeletedWhenDeletingByTag() {
        tester.withExistingImages(image("image", null, "image-1", "my-tag"))
              .expectDeletedImages("image-1", "my-tag") // After 1h we delete image
              .expectDeletedImagesAfterMinutes(0) // image is immediately re-downloaded, but is not deleted
              .expectDeletedImagesAfterMinutes(10)
              .expectDeletedImages("image-1", "my-tag"); // 1h after re-download it is deleted again
    }

    /** Same scenario as in {@link #multipleUnusedImagesAreIdentified()} */
    @Test
    public void doesNotDeleteExcludedByIdImages() {
        tester.withExistingImages(image("parent-image"),
                                  image("image-1", "parent-image"),
                                  image("image-2", "parent-image"))
              // Normally, image-1 and parent-image should also be deleted, but because we exclude image-1
              // we cannot delete parent-image, so only image-2 is deleted
              .expectDeletedImages(List.of("image-1"), "image-2");
    }

    /** Same as in {@link #doesNotDeleteExcludedByIdImages()} but with tags */
    @Test
    public void doesNotDeleteExcludedByTagImages() {
        tester.withExistingImages(image("parent-image", "rhel-6"),
                                  image("image-1", "parent-image", "vespa:6.288.16"),
                                  image("image-2", "parent-image", "vespa:6.289.94"))
              .expectDeletedImages(List.of("vespa:6.288.16"), "vespa:6.289.94");
    }

    @Test
    public void exludingNotDownloadedImageIsNoop() {
        tester.withExistingImages(image("parent-image", "rhel-6"),
                                  image("image-1", "parent-image", "vespa:6.288.16"),
                                  image("image-2", "parent-image", "vespa:6.289.94"))
              .expectDeletedImages(List.of("vespa:6.300.1"), "vespa:6.288.16", "vespa:6.289.94", "rhel-6");
    }

    private static Image image(String id) {
        return image(id, null);
    }

    private static Image image(String id, String parentId, String... tags) {
        return new Image(id, Optional.ofNullable(parentId), List.of(tags));
    }

    private static Container container(String name, String imageId) {
        return new Container(new ContainerId("id-of-" + name), new ContainerName(name),
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
            return expectDeletedImagesAfterMinutes(minutesAfter, Collections.emptyList(), imageIds);
        }

        private Tester expectDeletedImagesAfterMinutes(int minutesAfter, List<String> excludedRefs, String... imageIds) {
            if (!initialized) {
                // Run once with a very long expiry to initialize internal state of existing images
                pruner.removeUnusedImages(context, List.of(), Duration.ofDays(999));
                initialized = true;
            }

            clock.advance(Duration.ofMinutes(minutesAfter));

            pruner.removeUnusedImages(context, excludedRefs, Duration.ofHours(1).minusSeconds(1));

            Arrays.stream(imageIds)
                  .forEach(imageId -> {
                      int newValue = removalCountByImageId.getOrDefault(imageId, 0) + 1;
                      removalCountByImageId.put(imageId, newValue);

                      assertTrue("Image " + imageId + " removed",
                                 containerEngine.listImages(context).stream().noneMatch(image -> image.id().equals(imageId)));
                  });
            return this;
        }
    }

}
