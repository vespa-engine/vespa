// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

/**
 * This class represents a verbatim match within a {@link Hop}'s selector. This is nothing more than a string that will
 * be used as-is when performing service name lookups.
 *
 * @author Simon Thoresen Hult
 */
public class VerbatimDirective implements HopDirective {

    private final String image;

    /**
     * Constructs a new verbatim selector item for a given image.
     *
     * @param image The image to assign to this.
     */
    public VerbatimDirective(String image) {
        this.image = image;
    }

    @Override
    public boolean matches(HopDirective dir) {

        return dir instanceof VerbatimDirective && image.equals(((VerbatimDirective)dir).image);
    }

    /**
     * Returns the image to which this is a verbatim match.
     *
     * @return The image.
     */
    public String getImage() {
        return image;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof VerbatimDirective)) {
            return false;
        }
        VerbatimDirective rhs = (VerbatimDirective)obj;
        if (!image.equals(rhs.image)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return image != null ? image.hashCode() : 0;
    }

    @Override
    public String toString() {
        return image;
    }

    @Override
    public String toDebugString() {
        return "VerbatimDirective(image = '" + image + "')";
    }
}

