// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.net;

import java.net.URI;

/**
 * Utility methods for working with URIs.
 *
 * @author Steinar Knutsen
 */
public final class UriTools {
    private UriTools() {
    }

    /**
     * Build a string representation of the normalized form of the given URI,
     * containg the path and optionally query and fragment parts. The query part
     * will be delimeted from the preceding data with "?" and the fragment with
     * "#".
     *
     * @param uri
     *            source for path, query and fragment in returned data
     * @return a string containing path, and optionally query and fragment,
     *         delimited by question mark and hash
     */
    public static String rawRequest(final URI uri) {
        final String rawQuery = uri.getRawQuery();
        final String rawFragment = uri.getRawFragment();
        final StringBuilder rawRequest = new StringBuilder();

        rawRequest.append(uri.getRawPath());
        if (rawQuery != null) {
            rawRequest.append("?").append(rawQuery);
        }

        if (rawFragment != null) {
            rawRequest.append("#").append(rawFragment);
        }

        return rawRequest.toString();
    }
}
