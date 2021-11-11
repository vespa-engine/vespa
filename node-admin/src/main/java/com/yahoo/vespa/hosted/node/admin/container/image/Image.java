// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container.image;

import java.util.List;
import java.util.Objects;

/**
 * This represents a container image that exists locally.
 *
 * @author mpolden
 */
public class Image {

    private final String id;
    private final List<String> names;

    public Image(String id, List<String> names) {
        this.id = Objects.requireNonNull(id);
        this.names = List.copyOf(Objects.requireNonNull(names));
    }

    /** The identifier of this image */
    public String id() {
        return id;
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
        return id.equals(image.id) && names.equals(image.names);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, names);
    }

    @Override
    public String toString() {
        return "image " + id;
    }

}
