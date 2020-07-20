// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * URI binding pattern used by filter and handler bindings.
 *
 * There are two types of binding; user generated and model generated bindings.
 * - User generated bindings are bindings which are constructed from directly from 'binding' elements from services.xml
 * - Model generated bindings are binding which are implicitly constructed by the model, e.g built-in handlers.
 *
 * @author bjorncs
 */
public class BindingPattern implements Comparable<BindingPattern> {

    private static final Pattern BINDING_PATTERN =
            Pattern.compile("([^:]+)://([^:/]+)(:((\\*)|([0-9]+)))?(/.*)", Pattern.UNICODE_CASE | Pattern.CANON_EQ);

    private final String scheme;
    private final String host;
    private final String port;
    private final String path;
    private final boolean isUserGenerated;

    private BindingPattern(
            String scheme,
            String host,
            String port,
            String path,
            boolean isUserGenerated) {
        this.scheme = Objects.requireNonNull(scheme, "Scheme in binding must be specified");
        this.host = Objects.requireNonNull(host, "Host must be specified");
        this.port = port;
        this.path = validatePath(path);
        this.isUserGenerated = isUserGenerated;
    }

    private static String validatePath(String path) {
        Objects.requireNonNull(path, "Path must be specified");
        if (!path.startsWith("/")) throw new IllegalArgumentException("Path has not '/' as prefix: " + path);
        return path;
    }

    public static BindingPattern createUserGeneratedFromPattern(String pattern) {
        return createFromBindingString(pattern, true);
    }

    public static BindingPattern createUserGeneratedFromHttpPath(String path) {
        return new BindingPattern("http", "*", null, path, true);
    }

    public static BindingPattern createModelGeneratedFromPattern(String pattern) {
        return createFromBindingString(pattern, false);
    }

    public static BindingPattern createModelGeneratedFromHttpPath(String path) {
        return new BindingPattern("http", "*", null, path, false);
    }

    private static BindingPattern createFromBindingString(String binding, boolean isUserGenerated) {
        Matcher matcher = BINDING_PATTERN.matcher(binding);
        if (!matcher.matches()) throw new IllegalArgumentException("Invalid binding: " + binding);
        String scheme = matcher.group(1);
        String host = matcher.group(2);
        String port = matcher.group(4);
        String path = matcher.group(7);
        return new BindingPattern(scheme, host, port, path, isUserGenerated);
    }

    public String scheme() { return scheme; }
    public String host() { return host; }
    public Optional<String> port() { return Optional.ofNullable(port); }
    public String path() { return path; }
    public boolean isUserGenerated() { return isUserGenerated; }

    public String patternString() {
        StringBuilder builder = new StringBuilder(scheme).append("://").append(host);
        if (port != null) {
            builder.append(':').append(port);
        }
        return builder.append(path).toString();
    }

    /** Compares the underlying pattern string for equality */
    public boolean hasSamePattern(BindingPattern other) { return this.patternString().equals(other.patternString()); }

    @Override
    public String toString() {
        return "BindingPattern{" +
                "scheme='" + scheme + '\'' +
                ", host='" + host + '\'' +
                ", port='" + port + '\'' +
                ", path='" + path + '\'' +
                ", isUserGenerated=" + isUserGenerated +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BindingPattern that = (BindingPattern) o;
        return isUserGenerated == that.isUserGenerated &&
                Objects.equals(scheme, that.scheme) &&
                Objects.equals(host, that.host) &&
                Objects.equals(port, that.port) &&
                Objects.equals(path, that.path);
    }

    @Override public int hashCode() { return Objects.hash(scheme, host, port, path, isUserGenerated); }


    @Override
    public int compareTo(BindingPattern o) {
        return Comparator.comparing(BindingPattern::scheme)
                .thenComparing(BindingPattern::host)
                .thenComparing(pattern -> pattern.port().orElse(null))
                .thenComparing(BindingPattern::path)
                .thenComparing(BindingPattern::isUserGenerated)
                .compare(this, o);
    }
}
