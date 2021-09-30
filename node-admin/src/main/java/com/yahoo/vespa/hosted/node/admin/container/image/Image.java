// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container.image;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * This represents a container image that exists locally.
 *
 * @author mpolden
 */
public class Image {

    private final String id;
    private final Optional<String> parentId;
    private final List<String> names;

    public Image(String id, Optional<String> parentId, List<String> names) {
        this.id = Objects.requireNonNull(id);
        this.parentId = Objects.requireNonNull(parentId);
        this.names = List.copyOf(Objects.requireNonNull(names));
    }

    /** The identifier of this image */
    public String id() {
        return id;
    }

    /** ID of the parent image of this, if any */
    public Optional<String> parentId() {
        return parentId;
    }

    /** Names for this image, such as tags or digests */
    public List<String> names() {
        return names;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Image image = (Image) o;
        return id.equals(image.id) && parentId.equals(image.parentId) && names.equals(image.names);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, parentId, names);
    }

    @Override
    public String toString() {
        return "image " + id;
    }

}
