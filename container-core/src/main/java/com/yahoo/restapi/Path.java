// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import ai.vespa.http.HttpURL;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A normalized path which is able to match strings containing bracketed placeholders and return the
 * values given at the placeholders. The path is split on '/', and each part is then URL decoded.
 * 
 * E.g a path /a/1/bar/fuz/baz/%62%2f
 * will match /a/{foo}/bar/{b}/baz/{c}
 * and return foo=1, b=fuz, and c=c/
 * 
 * Only full path elements may be placeholders, i.e /a{bar} is not interpreted as one.
 * 
 * If the path spec ends with /{*}, it will match urls with any rest path.
 * The rest path (not including the trailing slash) will be available as getRest().
 * 
 * Note that for convenience in common use this has state which changes as a side effect of each
 * {@link Path#matches(String)} invocation. It is therefore for single thread use.
 *
 * @author bratseth
 */
public class Path {

    // This path
    private final HttpURL.Path path;

    // Info about the last match
    private final Map<String, String> values = new HashMap<>();
    private HttpURL.Path rest;

    /** Creates a new Path for matching the given URI against patterns, which uses {@link HttpURL#requirePathSegment} as a segment validator. */
    public Path(URI uri) {
        this.path = HttpURL.Path.parse(uri.getRawPath());
    }

    /** Creates a new Path for matching the given URI against patterns, with the given path segment validator. */
    public Path(URI uri, Consumer<String> validator) {
        this.path = HttpURL.Path.parse(uri.getRawPath(), validator);
    }

    /** Create a new Path for matching the given URI against patterns, without any segment validation. */
    public static Path withoutValidation(URI uri) {
        return new Path(uri, __ -> { });
    }

    private boolean matchesInner(String pathSpec) {
        values.clear();
        List<String> specElements = HttpURL.Path.parse(pathSpec).segments();
        boolean matchPrefix = false;
        if (specElements.size() > 1 && specElements.get(specElements.size() - 1).equals("{*}")) {
            matchPrefix = true;
            specElements = specElements.subList(0, specElements.size() - 1);
        }

        if (matchPrefix) {
            if (path.length() < specElements.size()) return false;
        }
        else { // match exact
            if (path.length() != specElements.size()) return false;
        }

        List<String> segments = path.segments();
        for (int i = 0; i < specElements.size(); i++) {
            if (specElements.get(i).startsWith("{") && specElements.get(i).endsWith("}")) // placeholder
                values.put(specElements.get(i).substring(1, specElements.get(i).length() - 1), segments.get(i));
            else if ( ! specElements.get(i).equals(segments.get(i)))
                return false;
        }

        rest = matchPrefix ? path.skip(specElements.size()) : null;

        return true;
    }

    /**
     * Parses the path according to pathSpec - must be called prior to {@link #get}
     *
     * Returns whether this path matches the given template string.
     * If the given template has placeholders, their values (accessible by get) are reset by calling this,
     * whether or not the path matches the given template.
     *
     * This will NOT match empty path elements.
     *
     * @param pathSpec the literal path string to match to this
     * @return true if the string matches, false otherwise
     */
    public boolean matches(String pathSpec) {
        return matchesInner(pathSpec);
    }

    /**
     * Returns the value of the given template variable in the last path matched, or null 
     * if the previous matches call returned false or if this has not matched anything yet.
     */
    public String get(String placeholder) {
        return values.get(placeholder);
    }

    /**
     * Returns the rest of the last matched path, or {@code null} if the path spec didn't end with {*}.
     */
    public HttpURL.Path getRest() {
        return rest;
    }

    /**
     * The path this holds.
     */
    public HttpURL.Path getPath() {
        return path;
    }

    @Override
    public String toString() {
        return path.toString();
    }

}
