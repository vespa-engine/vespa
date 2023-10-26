// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * URI binding pattern used by filter and handler bindings.
 *
 * @author bjorncs
 */
public abstract class BindingPattern implements Comparable<BindingPattern> {

    private static final Pattern BINDING_PATTERN =
            Pattern.compile("([^:]+)://([^:/]+)(:((\\*)|([0-9]+)))?(/.*)", Pattern.UNICODE_CASE | Pattern.CANON_EQ);

    public static final String WILDCARD_PATTERN = "*";

    private final String scheme;
    private final String host;
    private final String port;
    private final String path;

    protected BindingPattern(
            String scheme,
            String host,
            String port,
            String path) {
        this.scheme = Objects.requireNonNull(scheme, "Scheme in binding must be specified");
        this.host = Objects.requireNonNull(host, "Host must be specified");
        this.port = port;
        this.path = validatePath(path);
    }

    protected BindingPattern(String binding) {
        Matcher matcher = BINDING_PATTERN.matcher(binding);
        if (!matcher.matches()) throw new IllegalArgumentException("Invalid binding: " + binding);
        this.scheme = matcher.group(1);
        this.host = matcher.group(2);
        this.port = matcher.group(4);
        this.path = matcher.group(7);
    }

    private static String validatePath(String path) {
        Objects.requireNonNull(path, "Path must be specified");
        if (!path.startsWith("/")) throw new IllegalArgumentException("Path must have '/' as prefix: " + path);
        return path;
    }

    public String scheme() { return scheme; }
    public String host() { return host; }
    public Optional<String> port() { return Optional.ofNullable(port); }
    public String path() { return path; }

    public String patternString() {
        StringBuilder builder = new StringBuilder(scheme).append("://").append(host);
        if (port != null) {
            builder.append(':').append(port);
        }
        return builder.append(path).toString();
    }

    public String originalPatternString() {
        StringBuilder builder = new StringBuilder(scheme).append("://").append(host);
        originalPort().ifPresent(port -> builder.append(':').append(port));
        return builder.append(path).toString();
    }

    /** Compares the underlying pattern string for equality */
    public boolean hasSamePattern(BindingPattern other) { return this.patternString().equals(other.patternString()); }

    /** Returns true if pattern will match any port (if present) in uri **/
    public boolean matchesAnyPort() { return originalPort().filter(p -> !p.equals(WILDCARD_PATTERN)).isEmpty(); }

    public Optional<String> originalPort() {
        return port();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BindingPattern that = (BindingPattern) o;
        return Objects.equals(scheme, that.scheme) &&
                Objects.equals(host, that.host) &&
                Objects.equals(port, that.port) &&
                Objects.equals(path, that.path);
    }

    @Override public int hashCode() { return Objects.hash(scheme, host, port, path); }

    @Override
    public int compareTo(BindingPattern o) {
        return Comparator.comparing(BindingPattern::scheme)
                .thenComparing(BindingPattern::host)
                .thenComparing(pattern -> pattern.port().orElse(null))
                .thenComparing(BindingPattern::path)
                .compare(this, o);
    }
}
