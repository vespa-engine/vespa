// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.feature;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The ID of a FeatureConfigId
 * @author hakon
 */
public class FeatureConfigId {
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private final String id;

    public FeatureConfigId(String id) {
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException(FeatureConfigId.class.getSimpleName() + " '" + id +
                    "' doesn't match " + ID_PATTERN);
        }

        this.id = id;
    }

    /** Return the ID String. */
    public String asString() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FeatureConfigId that = (FeatureConfigId) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /** Please use {@link #asString}. */
    @Override
    public String toString() {
        return asString();
    }
}
