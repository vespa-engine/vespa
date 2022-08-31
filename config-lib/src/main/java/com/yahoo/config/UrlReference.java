// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import java.util.Objects;

/**
 * Similar to {@link FileReference}, holds either a URL or a file path to the
 * downloaded file depending on state.
 *
 * @author lesters
 */
public final class UrlReference {

    /** Either the url or, if downloaded, the absolute path to the downloaded file. */
    private final String value;

    public UrlReference(String value) {
        this.value = Objects.requireNonNull(value);
    }

    public String value() {
        return value;
    }

    public static UrlReference valueOf(String value) {
        return new UrlReference(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof UrlReference &&
                value.equals(((UrlReference)other).value);
    }

    @Override
    public String toString() {
        return "url '" + value + "'";
    }

}
