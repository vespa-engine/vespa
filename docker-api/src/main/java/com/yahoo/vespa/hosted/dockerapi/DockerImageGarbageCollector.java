// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.google.common.base.Strings;
import com.yahoo.collections.Pair;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class keeps track of downloaded docker images and helps delete images that have not been recently used
 *
 * <p>Definitions:
 * <ul>
 *   <li>Every image has exactly 1 id</li>
 *   <li>Every image has between 0..n tags, see
 *       <a href="https://docs.docker.com/engine/reference/commandline/tag/">docker tag</a> for more</li>
 *   <li>Every image has 0..1 parent ids</li>
 * </ul>
 *
 * <p>Limitations:
 * <ol>
 *   <li>Image that has more than 1 tag cannot be deleted by ID</li>
 *   <li>Deleting a tag of an image with multiple tags will only remove the tag, the image with the
 *       remaining tags will remain</li>
 *   <li>Deleting the last tag of an image will delete the entire image.</li>
 *   <li>Image cannot be deleted if:</li>
 *   <ol>
 *     <li>It has 1 or more children</li>
 *     <li>A container uses it</li>
 *   </ol>
 * </ol>
 *
 * @author freva
 */
class DockerImageGarbageCollector {
    private static final Logger logger = Logger.getLogger(DockerImageGarbageCollector.class.getName());

    private final Map<String, Instant> lastTimeUsedByImageId = new ConcurrentHashMap<>();
    private final DockerEngine docker;
    private final Clock clock;

    DockerImageGarbageCollector(DockerEngine docker) {
        this(docker, Clock.systemUTC());
    }

    DockerImageGarbageCollector(DockerEngine docker, Clock clock) {
        this.docker = docker;
        this.clock = clock;
    }

    /**
     * This method must be called frequently enough to see all containers to know which images are being used
     *
     * @param excludes List of image references (tag or id) that should not be deleted regardless of their used status
     * @param minImageAgeToDelete Minimum duration after which an image can be removed if it has not been used
     * @return true iff at least 1 image was deleted
     */
    boolean deleteUnusedDockerImages(List<String> excludes, Duration minImageAgeToDelete) {
        List<Image> images = docker.listAllImages();
        List<Container> containers = docker.listAllContainers();

        Map<String, Image> imageByImageId = images.stream().collect(Collectors.toMap(Image::getId, Function.identity()));

        // Find all the ancestors for every local image id, this includes the image id itself
        Map<String, Set<String>> ancestorsByImageId = images.stream()
                .map(Image::getId)
                .collect(Collectors.toMap(
                        Function.identity(),
                        imageId -> {
                            Set<String> ancestors = new HashSet<>();
                            while (!Strings.isNullOrEmpty(imageId)) {
                                ancestors.add(imageId);
                                imageId = Optional.of(imageId).map(imageByImageId::get).map(Image::getParentId).orElse(null);
                            }
                            return ancestors;
                        }
                ));

        // The set of images that we want to keep is:
        // 1. The images that were recently used
        // 2. The images that were explicitly excluded
        // 3. All of the ancestors of from images in 1 & 2
        Set<String> imagesToKeep = Stream
                .concat(
                        getRecentlyUsedImageIds(images, containers, minImageAgeToDelete).stream(), // 1
                        referencesToImages(excludes, images).stream()) // 2
                .flatMap(imageId -> ancestorsByImageId.getOrDefault(imageId, Collections.emptySet()).stream()) // 3
                .collect(Collectors.toSet());

        // Now take all the images we have locally
        return imageByImageId.keySet().stream()

                // filter out images we want to keep
                .filter(imageId -> !imagesToKeep.contains(imageId))

                // Sort images in an order is safe to delete (children before parents)
                .sorted((o1, o2) -> {
                    // If image2 is parent of image1, image1 comes before image2
                    if (imageIsDescendantOf(imageByImageId, o1, o2)) return -1;
                    // If image1 is parent of image2, image2 comes before image1
                    else if (imageIsDescendantOf(imageByImageId, o2, o1)) return 1;
                    // Otherwise, sort lexicographically by image name (For testing)
                    else return o1.compareTo(o2);
                })

                // Map back to image
                .map(imageByImageId::get)

                // Delete image, if successful also remove last usage time to prevent re-download being instantly deleted
                .peek(image -> {
                    // Deleting an image by image ID with multiple tags will fail -> delete by tags instead
                    referencesOf(image).forEach(imageReference -> {
                        logger.info("Deleting unused image " + imageReference);
                        docker.deleteImage(imageReference);
                    });
                    lastTimeUsedByImageId.remove(image.getId());
                })
                .count() > 0;
    }

    private Set<String> getRecentlyUsedImageIds(List<Image> images, List<Container> containers, Duration minImageAgeToDelete) {
        final Instant now = clock.instant();

        // Add any already downloaded image to the list once
        images.forEach(image -> lastTimeUsedByImageId.putIfAbsent(image.getId(), now));

        // Update last used time for all current containers
        containers.forEach(container -> lastTimeUsedByImageId.put(container.getImageId(), now));

        // Return list of images that have been used within minImageAgeToDelete
        return lastTimeUsedByImageId.entrySet().stream()
                .filter(entry -> Duration.between(entry.getValue(), now).minus(minImageAgeToDelete).isNegative())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * Map given references (image tags or ids) to images.
     *
     * This only works if the given tag is actually present locally. This is fine, because if it isn't - we can't delete
     * it, so no harm done.
     */
    private Set<String> referencesToImages(List<String> references, List<Image> images) {
        Map<String, String> imageIdByImageTag = images.stream()
                .flatMap(image -> referencesOf(image).stream()
                                                     .map(repoTag -> new Pair<>(repoTag, image.getId())))
                .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));

        return references.stream()
                         .map(ref -> imageIdByImageTag.getOrDefault(ref, ref))
                         .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * @return true if ancestor is a parent or grand-parent or grand-grand-parent, etc. of img
     */
    private boolean imageIsDescendantOf(Map<String, Image> imageIdToImage, String img, String ancestor) {
        while (imageIdToImage.containsKey(img)) {
            img = imageIdToImage.get(img).getParentId();
            if (img == null) return false;
            if (ancestor.equals(img)) return true;
        }
        return false;
    }

    /**
     * Returns list of references to given image, preferring image tag(s), if any exist.
     *
     * If image is untagged, its ID is returned instead.
     */
    private static List<String> referencesOf(Image image) {
        if (image.getRepoTags() == null) {
            return List.of(image.getId());
        }
        return Arrays.stream(image.getRepoTags())
                     // Docker API returns untagged images as having the tag "<none>:<none>".
                     .map(tag -> {
                         if ("<none>:<none>".equals(tag)) return image.getId();
                         return tag;
                     })
                     .collect(Collectors.toUnmodifiableList());
    }

}
