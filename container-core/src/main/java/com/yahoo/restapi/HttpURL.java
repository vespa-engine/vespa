// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import ai.vespa.validation.StringWrapper;
import com.yahoo.net.DomainName;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.StringJoiner;
import java.util.function.Function;

import static ai.vespa.validation.Validation.require;
import static ai.vespa.validation.Validation.requireInRange;
import static java.net.URLDecoder.decode;
import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

/**
 * This is the best class for creating, manipulating and inspecting HTTP URLs, because:
 * <ul>
 *     <li>It is more restrictive than {@link URI}, but with a richer construction API, reducing risk of blunder.
 *         <ul>
 *             <li>Scheme must be HTTP or HTTPS.</li>
 *             <li>Authority must be a {@link DomainName}, with an optional port.</li>
 *             <li>{@link Path} must be normalized at all times.</li>
 *             <li>Only {@link Query} is allowed, in addition to the above.</li>
 *         </ul>
 *     </li>
 *     <li>
 *         It contains all those helpful builder methods that {@link URI} has none of.
 *         <ul>
 *             <li>{@link Path} can be parsed, have segments or other paths appended, and cut.</li>
 *             <li>{@link Query} can be parsed, and keys and key-value pairs can be inserted or removed.</li>
 *         </ul>
 *         All these (except the parse methods) operate on <em>decoded</em> values.
 *     </li>
 *     <li>It makes it super-easy to use a {@link StringWrapper} for validation of path and query segments.</li>
 * </ul>
 *
 * @author jonmv
 */
public class HttpURL<T> {

    private final Scheme scheme;
    private final DomainName domain;
    private final int port;
    private final Path<T> path;
    private final Query<T> query;

    private HttpURL(Scheme scheme, DomainName domain, int port, Path<T> path, Query<T> query) {
        this.scheme = requireNonNull(scheme);
        this.domain = requireNonNull(domain);
        this.port = requireInRange(port, "port number", -1, (1 << 16) - 1);
        this.path = requireNonNull(path);
        this.query = requireNonNull(query);
    }

    public static <T> HttpURL<T> create(Scheme scheme, DomainName domain, int port, Path<T> path, Query<T> query) {
        return new HttpURL<>(scheme, domain, port, path, query);
    }

    public static HttpURL<String> create(Scheme scheme, DomainName domain, int port, Path<String> path) {
        return create(scheme, domain, port, path, Query.empty());
    }

    public static <T extends StringWrapper<T>> HttpURL<T> create(Scheme scheme, DomainName domain, int port, Path<T> path, Function<String, T> validator) {
        return create(scheme, domain, port, path, Query.empty(validator));
    }

    public static <T extends StringWrapper<T>> HttpURL<T> create(Scheme scheme, DomainName domain, int port, Function<String, T> validator) {
        return create(scheme, domain, port, Path.empty(validator), validator);
    }

    public static HttpURL<String> create(Scheme scheme, DomainName domain, int port) {
        return create(scheme, domain, port, Path.empty());
    }

    public static <T extends StringWrapper<T>> HttpURL<T> create(Scheme scheme, DomainName domain, Function<String, T> validator) {
        return create(scheme, domain, -1, validator);
    }

    public static HttpURL<String> create(Scheme scheme, DomainName domain) {
        return create(scheme, domain, -1);
    }

    public static HttpURL<String> from(URI uri) {
        return from(uri, identity(), identity());
    }

    public static <T extends StringWrapper<T>> HttpURL<T> from(URI uri, Function<String, T> validator) {
        return from(uri, validator, T::value);
    }

    private static <T> HttpURL<T> from(URI uri, Function<String, T> validator, Function<T, String> inverse) {
        if ( ! uri.normalize().equals(uri))
            throw new IllegalArgumentException("uri should be normalized, but got: " + uri);

        return create(Scheme.of(uri.getScheme()),
                      DomainName.of(requireNonNull(uri.getHost(), "URI must specify a host")),
                      uri.getPort(),
                      Path.parse(uri.getRawPath(), validator, inverse),
                      Query.parse(uri.getRawQuery(), validator, inverse));
    }

    public HttpURL<T> withScheme(Scheme scheme) {
        return create(scheme, domain, port, path, query);
    }

    public HttpURL<T> withDomain(DomainName domain) {
        return create(scheme, domain, port, path, query);
    }

    public HttpURL<T> withPort(int port) {
        return create(scheme, domain, port, path, query);
    }

    public HttpURL<T> withoutPort() {
        return create(scheme, domain, -1, path, query);
    }

    public HttpURL<T> withPath(Path<T> path) {
        return create(scheme, domain, port, path, query);
    }

    public HttpURL<T> withQuery(Query<T> query) {
        return create(scheme, domain, port, path, query);
    }

    public Scheme scheme() {
        return scheme;
    }

    public DomainName domain() {
        return domain;
    }

    public OptionalInt port() {
        return port == -1 ? OptionalInt.empty() : OptionalInt.of(port);
    }

    public Path<T> path() {
        return path;
    }

    public Query<T> query() {
        return query;
    }

    /** Returns an absolute, hierarchical URI representing this HTTP URL. */
    public URI asURI() {
        try {
            return new URI(scheme.name() + "://" + domain.value() + (port == -1 ? "" : ":" + port) + path.raw() + query.raw());
        }
        catch (URISyntaxException e) {
            throw new IllegalStateException("invalid URI, this should not happen", e);
        }
    }


    public static class Path<T> {

        private final List<T> segments;
        private final boolean trailingSlash;
        private final Function<String, T> validator;
        private final Function<T, String> inverse;

        private Path(List<T> segments, boolean trailingSlash, Function<String, T> validator, Function<T, String> inverse) {
            this.segments = requireNonNull(segments);
            this.trailingSlash = trailingSlash;
            this.validator = requireNonNull(validator);
            this.inverse = requireNonNull(inverse);
        }

        /** Creates a new, empty path, with a trailing slash. */
        public static Path<String> empty() {
            return new Path<>(List.of(), true, identity(), identity());
        }

        /** Creates a new, empty path, with a trailing slash, using the indicated string wrapper for segments. */
        public static <T extends StringWrapper<T>> Path<T> empty(Function<String, T> validator) {
            return new Path<>(List.of(), true, validator, T::value);
        }
        /** Creates a new path with the given <em>decoded</em> segments. */
        public static Path<String> from(List<String> segments) {
            return empty().append(segments);
        }

        /** Creates a new path with the given <em>decoded</em> segments, and the validator applied to each segment. */
        public static <T extends StringWrapper<T>> Path<T> from(List<String> segments, Function<String, T> validator) {
            return empty(validator).append(segments, identity(), true);
        }

        /** Parses the given raw, normalized path string; this ignores whether the path is absolute or relative.) */
        public static <T extends StringWrapper<T>> Path<T> parse(String raw, Function<String, T> validator) {
            return parse(raw, validator, T::value);
        }

        /** Parses the given raw, normalized path string; this ignores whether the path is absolute or relative. */
        public static Path<String> parse(String raw) {
            return parse(raw, identity(), identity());
        }

        private static <T> Path<T> parse(String raw, Function<String, T> validator, Function<T, String> inverse) {
            boolean trailingSlash = raw.endsWith("/");
            if (raw.startsWith("/")) raw = raw.substring(1);
            if (raw.isEmpty()) return new Path<>(List.of(), trailingSlash, validator, inverse);
            List<T> segments = new ArrayList<>();
            for (String segment : raw.split("/"))
                segments.add(validator.apply(requireNonNormalizable(decode(segment, UTF_8))));
            if (segments.size() == 0) requireNonNormalizable(""); // Raw path was only slashes.
            return new Path<>(segments, trailingSlash, validator, inverse);
        }

        private static String requireNonNormalizable(String segment) {
            return require( ! (segment.isEmpty() || segment.equals(".") || segment.equals("..")),
                           segment, "path segments cannot be \"\", \".\", or \"..\"");
        }

        /** Returns a copy of this where the first segments are skipped. */
        public Path<T> skip(int count) {
            return new Path<>(segments.subList(count, segments.size()), trailingSlash, validator, inverse);
        }

        /** Returns a copy of this where the last segments are cut off. */
        public Path<T> cut(int count) {
            return new Path<>(segments.subList(0, segments.size() - count), trailingSlash, validator, inverse);
        }

        /** Returns a copy of this with the <em>decoded</em> segment appended at the end; it may not be either of {@code ""}, {@code "."} or {@code ".."}. */
        public Path<T> append(String segment) {
            return append(List.of(segment), identity(), trailingSlash);
        }

        /** Returns a copy of this all segments of the other path appended, with a trailing slash as per the appendage. */
        public <U> Path<T> append(Path<U> other) {
            return append(other.segments, other.inverse, other.trailingSlash);
        }

        /** Returns a copy of this all given segments appended, with a trailing slash as per this path. */
        public Path<T> append(List<T> segments) {
            return append(segments, inverse, trailingSlash);
        }

        private <U> Path<T> append(List<U> segments, Function<U, String> inverse, boolean trailingSlash) {
            List<T> copy = new ArrayList<>(this.segments);
            for (U segment : segments) copy.add(validator.apply(requireNonNormalizable(inverse.apply(segment))));
            return new Path<>(copy, trailingSlash, validator, this.inverse);
        }

        /** Returns a copy of this which encodes a trailing slash. */
        public Path<T> withTrailingSlash() {
            return new Path<>(segments, true, validator, inverse);
        }

        /** Returns a copy of this which does not encode a trailing slash. */
        public Path<T> withoutTrailingSlash() {
            return new Path<>(segments, false, validator, inverse);
        }

        /** The <em>URL decoded</em> segments that make up this path; never {@code null}, {@code ""}, {@code "."} or {@code ".."}. */
        public List<T> segments() {
            return Collections.unmodifiableList(segments);
        }

        /** A raw path string which parses to this, by splitting on {@code "/"}, and then URL decoding. */
        private String raw() {
            StringJoiner joiner = new StringJoiner("/", "/", trailingSlash ? "/" : "").setEmptyValue(trailingSlash ? "/" : "");
            for (T segment : segments)
                joiner.add(encode(inverse.apply(segment), UTF_8));
            return joiner.toString();
        }

        /** Intentionally not usable for constructing new URIs. Use {@link HttpURL} for that instead. */
        @Override
        public String toString() {
            return "path '" + raw() + "'";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Path<?> path = (Path<?>) o;
            return trailingSlash == path.trailingSlash && segments.equals(path.segments);
        }

        @Override
        public int hashCode() {
            return Objects.hash(segments, trailingSlash);
        }

    }


    public static class Query<T> {

        private final Map<T, T> values;
        private final Function<String, T> validator;
        private final Function<T, String> inverse;

        private Query(Map<T, T> values, Function<String, T> validator, Function<T, String> inverse) {
            this.values = requireNonNull(values);
            this.validator = requireNonNull(validator);
            this.inverse = requireNonNull(inverse);
        }

        /** Creates a new, empty query part. */
        public static Query<String> empty() {
            return new Query<>(Map.of(), identity(), identity());
        }

        /** Creates a new, empty query part, using the indicated string wrapper for keys and non-null values. */
        public static <T extends StringWrapper<T>> Query<T> empty(Function<String, T> validator) {
            return new Query<>(Map.of(), validator, T::value);
        }
        /** Creates a new query part with the given <em>decoded</em> values. */
        public static Query<String> from(Map<String, String> values) {
            return empty().merge(values);
        }

        /** Creates a new query part with the given <em>decoded</em> values, and the validator applied to each pair. */
        public static <T extends StringWrapper<T>> Query<T> from(Map<String, String> values, Function<String, T> validator) {
            return empty(validator).merge(values, identity());
        }

        /** Parses the given raw query string, using the indicated string wrapper to hold keys and non-null values. */
        public static <T extends StringWrapper<T>> Query<T> parse(String raw, Function<String, T> validator) {
            return parse(raw, validator, T::value);
        }

        /** Parses the given raw query string. */
        public static Query<String> parse(String raw) {
            return parse(raw, identity(), identity());
        }

        private static <T> Query<T> parse(String raw, Function<String, T> validator, Function<T, String> inverse) {
            if (raw == null) return new Query<>(Map.of(), validator, inverse);
            Map<T, T> values = new LinkedHashMap<>();
            for (String pair : raw.split("&")) {
                int split = pair.indexOf("=");
                String key, value;
                if (split == -1) { key = pair; value = null; }
                else { key = pair.substring(0, split); value = pair.substring(split + 1); }
                values.put(validator.apply(decode(key, UTF_8)), value == null ? null : validator.apply(decode(value, UTF_8)));
            }
            return new Query<>(values, validator, inverse);
        }

        /** Returns a copy of this with the <em>decoded</em> non-null key pointing to the <em>decoded</em> non-null value. */
        public Query<T> put(String key, String value) {
            Map<T, T> copy = new LinkedHashMap<>(values);
            copy.put(requireNonNull(validator.apply(key)), requireNonNull(validator.apply(value)));
            return new Query<>(copy, validator, inverse);
        }

        /** Returns a copy of this with the <em>decoded</em> non-null key pointing to "nothing". */
        public Query<T> add(String key) {
            Map<T, T> copy = new LinkedHashMap<>(values);
            copy.put(requireNonNull(validator.apply(key)), null);
            return new Query<>(copy, validator, inverse);
        }

        /** Returns a copy of this without any key-value pair with the <em>decoded</em> key. */
        public Query<T> remove(String key) {
            Map<T, T> copy = new LinkedHashMap<>(values);
            copy.remove(requireNonNull(validator.apply(key)));
            return new Query<>(copy, validator, inverse);
        }

        /** Returns a copy of this with all mappings from the other query added to this, possibly overwriting existing mappings. */
        public <U> Query<T> merge(Query<U> other) {
            return merge(other.values, other.inverse);
        }

        /** Returns a copy of this with all given mappings added to this, possibly overwriting existing mappings. */
        public Query<T> merge(Map<T, T> values) {
            return merge(values, inverse);
        }

        private <U> Query<T> merge(Map<U, U> values, Function<U, String> inverse) {
            Map<T, T> copy = new LinkedHashMap<>(this.values);
            values.forEach((key, value) -> copy.put(validator.apply(inverse.apply(requireNonNull(key, "keys cannot be null"))),
                                                    value == null ? null : validator.apply(inverse.apply(value))));
            return new Query<>(copy, validator, this.inverse);
        }

        /** The <em>URL decoded</em> key-value pairs that make up this query; keys and values may be {@code ""}, and values are {@code null} when only key was specified. */
        public Map<T, T> entries() {
            return unmodifiableMap(values);
        }

        /** A raw query string, with {@code '?'} prepended, that parses to this, by splitting on {@code "&"}, then on {@code "="}, and then URL decoding; or the empty string if this is empty. */
        private String raw() {
            StringJoiner joiner = new StringJoiner("&", "?", "").setEmptyValue("");
            values.forEach((key, value) -> joiner.add(encode(inverse.apply(key), UTF_8) +
                                                      (value == null ? "" : "=" + encode(inverse.apply(value), UTF_8))));
            return joiner.toString();
        }

        /** Intentionally not usable for constructing new URIs. Use {@link HttpURL} for that instead. */
        @Override
        public String toString() {
            return "query '" + raw() + "'";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Query<?> query = (Query<?>) o;
            return values.equals(query.values);
        }

        @Override
        public int hashCode() {
            return Objects.hash(values);
        }

    }


    public enum Scheme {
        http,
        https;
        public static Scheme of(String scheme) {
            if (scheme.equalsIgnoreCase(http.name())) return http;
            if (scheme.equalsIgnoreCase(https.name())) return https;
            throw new IllegalArgumentException("scheme must be HTTP or HTTPS");
        }
    }

}
