// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A path which is able to match strings containing bracketed placeholders and return the
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
 * Note that for convenience in common use this has state which changes as a side effect of each matches
 * invocation. It is therefore for single thread use.
 *
 * An optional prefix can be used to match the path spec against an alternative path. This
 * is used when you have alternative paths mapped to the same resource.
 *
 * @author bratseth
 */
public class Path {

    // This path
    private final String pathString;
    private final String optionalPrefix;
    private final String[] elements;

    // Info about the last match
    private final Map<String, String> values = new HashMap<>();
    private String rest = "";

    public Path(URI uri) {
        this(uri, "");
    }

    public Path(URI uri, String optionalPrefix) {
        this.optionalPrefix = optionalPrefix;
        this.pathString = uri.getRawPath();
        this.elements = Stream.of(this.pathString.split("/"))
                              .map(part -> URLDecoder.decode(part, StandardCharsets.UTF_8))
                              .toArray(String[]::new);
    }

    private boolean matchesInner(String pathSpec) {
        values.clear();
        String[] specElements = pathSpec.split("/");
        boolean matchPrefix = false;
        if (specElements.length > 1 && specElements[specElements.length-1].equals("{*}")) {
            matchPrefix = true;
            specElements = Arrays.copyOf(specElements, specElements.length-1);
        }

        if (matchPrefix) {
            if (this.elements.length < specElements.length) return false;
        }
        else { // match exact
            if (this.elements.length != specElements.length) return false;
        }
        
        for (int i = 0; i < specElements.length; i++) {
            if (specElements[i].startsWith("{") && specElements[i].endsWith("}")) // placeholder
                values.put(specElements[i].substring(1, specElements[i].length()-1), elements[i]);
            else if ( ! specElements[i].equals(this.elements[i]))
                return false;
        }
        
        if (matchPrefix) {
            StringBuilder rest = new StringBuilder();
            for (int i = specElements.length; i < this.elements.length; i++)
                rest.append(elements[i]).append("/");
            if ( ! pathString.endsWith("/") && rest.length() > 0)
                rest.setLength(rest.length() - 1);
            this.rest = rest.toString();
        }
        
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
     * @param pathSpec the path string to match to this
     * @return true if the string matches, false otherwise
     */
    public boolean matches(String pathSpec) {
        if (matchesInner(pathSpec)) return true;
        if (optionalPrefix.isEmpty()) return false;
        return matchesInner(optionalPrefix + pathSpec);
    }

    /**
     * Returns the value of the given template variable in the last path matched, or null 
     * if the previous matches call returned false or if this has not matched anything yet.
     */
    public String get(String placeholder) {
        return values.get(placeholder);
    }

    /**
     * Returns the rest of the last matched path.
     * This is always the empty string (never null) unless the path spec ends with {*}
     */
    public String getRest() { return rest; }

    public String asString() {
        return pathString;
    }

    @Override
    public String toString() {
        return "path '" + String.join("/", elements) + "'";
    }
    
}
