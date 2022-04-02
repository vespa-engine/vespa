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
                      DomainName.of(uri.getHost()),
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
        public static <T extends StringWrapper<T>> Path<T> empty(Function<String, T> validator) {
            return new Path<>(List.of(), true, validator, T::value);
        }

        /** Creates a new, empty path, with a trailing slash. */
        public static Path<String> empty() {
            return new Path<>(List.of(), true, identity(), identity());
        }

        /** Parses the given raw, normalized path string; this ignores whether the path is absolute or relative. */
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
                segments.add(validator.apply(requireNormalized(decode(segment, UTF_8))));
            if (segments.size() == 0) requireNormalized(""); // Raw path was only slashes.
            return new Path<>(segments, trailingSlash, validator, inverse);
        }

        private static String requireNormalized(String segment) {
            return require( ! (segment.isEmpty() || segment.equals(".") || segment.equals("..")),
                           segment, "path segments cannot be \"\", \".\", or \"..\"");
        }

        /** Returns a copy of this which eliminates the given number of segments, from the root down. */
        public Path<T> tailPath(int offset) {
            return new Path<>(segments.subList(offset, segments.size()), trailingSlash, validator, inverse);
        }

        /** Returns a copy of this which eliminates the given number of segments, from the end up. */
        public Path<T> headPath(int offset) {
            return new Path<>(segments.subList(0, segments.size() - offset), trailingSlash, validator, inverse);
        }

        /** Returns a copy of this with the <em>decoded</em> segment appended at the end; it may not be either of {@code ""}, {@code "."} or {@code ".."}. */
        public Path<T> with(T segment) {
            List<T> copy = new ArrayList<>(segments);
            copy.add(segment);
            return new Path<>(copy, trailingSlash, validator, inverse);
        }

        /** Returns a copy of this all segments of the other path appended, with a trailing slash as per the appendage. */
        public <U> Path<T> with(Path<U> other) {
            List<T> copy = new ArrayList<>(segments);
            for (U segment : other.segments)
                copy.add(validator.apply(other.inverse.apply(segment)));
            return new Path<>(copy, other.trailingSlash, validator, inverse);
        }

        /** Returns a copy of this which encodes a trailing slash. */
        public Path<T> withTrailingSlash() {
            return new Path<>(segments, true, validator, inverse);
        }

        /** Returns a copy of this which does not encode a trailing slash. */
        public Path<T> withoutTrailingSlash() {
            return new Path<>(segments, false, validator, inverse);
        }

        /** The URL decoded segments that make up this path; never {@code ""}, {@code "."} or {@code ".."}. */
        public List<T> decoded() {
            return Collections.unmodifiableList(segments);
        }

        /** A raw path string which parses to this, by splitting on {@code "/"}, and then URL decoding. */
        private String raw() {
            StringJoiner joiner = new StringJoiner("/", "/", trailingSlash ? "/" : "").setEmptyValue(trailingSlash ? "/" : "");
            for (T segment : segments)
                joiner.add(encode(inverse.apply(segment), UTF_8));
            return joiner.toString();
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

        /** Parses the given raw query string. */
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

        public static <T extends StringWrapper<T>> Query<T> empty(Function<String, T> validator) {
            return new Query<T>(Map.of(), validator, T::value);
        }

        public static <T> Query<String> empty() {
            return new Query<>(Map.of(), identity(), identity());
        }

        /** Returns a copy of this with the <em>decoded</em> non-null key pointing to the <em>decoded</em> non-null value. */
        public Query<T> with(T key, T value) {
            Map<T, T> copy = new LinkedHashMap<>(values);
            copy.put(requireNonNull(key), value);
            return new Query<>(copy, validator, inverse);
        }

        /** Returns a copy of this with the <em>decoded</em> non-null key pointing to "nothing". */
        public Query<T> with(T key) {
            Map<T, T> copy = new LinkedHashMap<>(values);
            copy.put(requireNonNull(key), null);
            return new Query<>(copy, validator, inverse);
        }

        /** Returns a copy of this without any key-value pair with the <em>decoded</em> key. */
        public Query<T> without(T key) {
            Map<T, T> copy = new LinkedHashMap<>(values);
            copy.remove(key);
            return new Query<>(copy, validator, inverse);
        }

        /** The URL decoded key-value pairs that make up this query; keys and values may be {@code ""}, and values are {@code} null if only key was specified. */
        public Map<T, T> decoded() {
            return unmodifiableMap(values);
        }

        /** A raw query string, with {@code '?'} prepended, that parses to this, by splitting on {@code "&"}, then on {@code "="}, and then URL decoding; or the empty string if this is empty. */
        private String raw() {
            StringJoiner joiner = new StringJoiner("&", "?", "").setEmptyValue("");
            values.forEach((key, value) -> joiner.add(encode(inverse.apply(key), UTF_8) +
                                                      (value == null ? "" : "=" + encode(inverse.apply(value), UTF_8))));
            return joiner.toString();
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
