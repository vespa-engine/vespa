// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Container;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author freva
 */
public class DockerImageGarbageCollector {
    private final Duration minAgeImageGc;
    private final Map<String, Instant> lastTimeUsedByImageId = new ConcurrentHashMap<>();

    public DockerImageGarbageCollector(Duration minAgeImageToDelete) {
        minAgeImageGc = minAgeImageToDelete;
    }

    public void updateLastUsedTimeFor(String imageId) {
        updateLastUsedTimeFor(imageId, Instant.now());
    }

    void updateLastUsedTimeFor(String imageId, Instant at) {
        lastTimeUsedByImageId.put(imageId, at);
    }

    /**
     * Generates lists of images that are safe to delete, in the order that is safe to delete them (children before
     * parents). The function starts with the set of all local images and then filters out images that are used now
     * and images that have been used recently (because they might be re-used again in near future).
     *
     * @param images List of all the local images
     * @param containers List of all the containers, including the ones that are stopped
     * @return List of image tags of unused images, if unused image has no tag, will return image ID instead.
     */
    public List<DockerImage> getUnusedDockerImages(List<Image> images, List<Container> containers) {
        Map<String, Image> dockerImageByImageId = images.stream().collect(Collectors.toMap(Image::getId, img -> img));
        Map<String, Image> unusedImagesByContainers = filterOutImagesUsedByContainers(dockerImageByImageId, containers);
        Map<String, Image> unusedImagesByRecent = filterOutRecentImages(unusedImagesByContainers);

        return unusedImagesByRecent.keySet().stream()
                .sorted((o1, o2) -> {
                    // If image2 is parent of image1, image1 comes before image2
                    if (imageIsDescendantOf(unusedImagesByRecent, o1, o2)) return -1;
                    // If image1 is parent of image2, image2 comes before image1
                    else if (imageIsDescendantOf(unusedImagesByRecent, o2, o1)) return 1;
                    // Otherwise, sort lexicographically by image name (For testing)
                    else return o1.compareTo(o2);
                })
                .flatMap(imageId -> {
                    // Deleting an image by image ID with multiple tags will fail -> map IDs to all the tags referring to the ID
                    String[] repoTags = unusedImagesByRecent.get(imageId).getRepoTags();
                    return (repoTags == null) ? Stream.of(imageId) : Stream.of(repoTags);
                })
                .map(DockerImage::new)
                .collect(Collectors.toList());
    }

    private Map<String, Image> filterOutImagesUsedByContainers(
            Map<String, Image> dockerImagesByImageId, List<com.github.dockerjava.api.model.Container> containerList) {
        Map<String, Image> filteredDockerImagesByImageId = new HashMap<>(dockerImagesByImageId);

        for (com.github.dockerjava.api.model.Container container : containerList) {
            String imageToSpare = container.getImageId();
            do {
                // May be null if two images have have the same parent, the first image will remove the parent, the
                // second will get null.
                Image sparedImage = filteredDockerImagesByImageId.remove(imageToSpare);
                imageToSpare = sparedImage == null ? "" : sparedImage.getParentId();
            } while (!imageToSpare.isEmpty());
        }

        return filteredDockerImagesByImageId;
    }

    private Map<String, Image> filterOutRecentImages(Map<String, Image> dockerImageByImageId) {
        Map<String, Image> filteredDockerImagesByImageId = new HashMap<>(dockerImageByImageId);

        final Instant now = Instant.now();
        filteredDockerImagesByImageId.keySet().forEach(imageId -> {
            if (! lastTimeUsedByImageId.containsKey(imageId)) lastTimeUsedByImageId.put(imageId, now);
        });

        lastTimeUsedByImageId.entrySet().stream()
                .filter(entry -> Duration.between(entry.getValue(), now).minus(minAgeImageGc).isNegative())
                .map(Map.Entry::getKey)
                .forEach(image -> {
                    String imageToSpare = image;
                    do {
                        Image sparedImage = filteredDockerImagesByImageId.remove(imageToSpare);
                        imageToSpare = sparedImage == null ? "" : sparedImage.getParentId();
                    } while (!imageToSpare.isEmpty());
                });
        return filteredDockerImagesByImageId;
    }

    /**
     * Returns true if ancestor is a parent or grand-parent or grand-grand-parent, etc. of img
     */
    private boolean imageIsDescendantOf(Map<String, Image> imageIdToImage, String img, String ancestor) {
        while (imageIdToImage.containsKey(img)) {
            img = imageIdToImage.get(img).getParentId();
            if (img == null) return false;
            if (ancestor.equals(img)) return true;
        }
        return false;
    }
}
