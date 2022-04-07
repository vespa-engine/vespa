// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.http;

import ai.vespa.validation.StringWrapper;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static ai.vespa.validation.Validation.require;
import static ai.vespa.validation.Validation.requireInRange;
import static java.net.URLDecoder.decode;
import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

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
public class HttpURL {

    private final Scheme scheme;
    private final DomainName domain;
    private final int port;
    private final Path path;
    private final Query query;

    private HttpURL(Scheme scheme, DomainName domain, int port, Path path, Query query) {
        this.scheme = requireNonNull(scheme);
        this.domain = requireNonNull(domain);
        this.port = requireInRange(port, "port number", -1, (1 << 16) - 1);
        this.path = requireNonNull(path);
        this.query = requireNonNull(query);
    }

    public static HttpURL create(Scheme scheme, DomainName domain, int port, Path path, Query query) {
        return new HttpURL(scheme, domain, port, path, query);
    }

    public static HttpURL create(Scheme scheme, DomainName domain, int port, Path path) {
        return create(scheme, domain, port, path, Query.empty());
    }

    public static HttpURL create(Scheme scheme, DomainName domain, int port) {
        return create(scheme, domain, port, Path.empty(), Query.empty());
    }

    public static HttpURL create(Scheme scheme, DomainName domain) {
        return create(scheme, domain, -1);
    }

    public static HttpURL from(URI uri) {
        return from(uri, HttpURL::requirePathSegment, HttpURL::requireNothing);
    }

    public static HttpURL from(URI uri, Consumer<String> pathValidator, Consumer<String> queryValidator) {
        if ( ! uri.normalize().equals(uri))
            throw new IllegalArgumentException("uri should be normalized, but got: " + uri);

        return create(Scheme.of(uri.getScheme()),
                      DomainName.of(requireNonNull(uri.getHost(), "URI must specify a host")),
                      uri.getPort(),
                      Path.parse(uri.getRawPath(), pathValidator),
                      Query.parse(uri.getRawQuery(), queryValidator));
    }

    /** Returns a copy of this with the given scheme. */
    public HttpURL withScheme(Scheme scheme) {
        return create(scheme, domain, port, path, query);
    }

    /** Returns a copy of this with the given domain. */
    public HttpURL withDomain(DomainName domain) {
        return create(scheme, domain, port, path, query);
    }

    /** Returns a copy of this with the given non-negative port. */
    public HttpURL withPort(int port) {
        return create(scheme, domain, port, path, query);
    }

    /** Returns a copy of this with no port specified. */
    public HttpURL withoutPort() {
        return create(scheme, domain, -1, path, query);
    }

    /** Returns a copy of this with only the given path. */
    public HttpURL withPath(Path path) {
        return create(scheme, domain, port, path, query);
    }

    /** Returns a copy of this with the given path appended. */
    public HttpURL appendPath(Path path) {
        return create(scheme, domain, port, this.path.append(path), query);
    }

    /** Returns a copy of this with only the given query. */
    public HttpURL withQuery(Query query) {
        return create(scheme, domain, port, path, query);
    }

    /** Returns a copy of this with all entries of the query appended. */
    public HttpURL appendQuery(Query query) {
        return create(scheme, domain, port, path, this.query.add(query.entries()));
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

    public Path path() {
        return path;
    }

    public Query query() {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HttpURL httpURL = (HttpURL) o;
        return port == httpURL.port && scheme == httpURL.scheme && domain.equals(httpURL.domain) && path.equals(httpURL.path) && query.equals(httpURL.query);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, domain, port, path, query);
    }

    @Override
    public String toString() {
        return asURI().toString();
    }

    /** Require that the given string (possibly decoded multiple times) contains none of {@code '/', '?', '#'}, and isn't either of {@code "", ".", ".."}. */
    public static String requirePathSegment(String value) {
        while ( ! value.equals(value = decode(value, UTF_8)));
        require( ! value.contains("/"), value, "path segment decoded cannot contain '/'");
        require( ! value.contains("?"), value, "path segment decoded cannot contain '?'");
        require( ! value.contains("#"), value, "path segment decoded cannot contain '#'");
        return Path.requireNonNormalizable(value);
    }

    private static void requireNothing(String value) { }

    public static class Path {

        private static final Path empty = empty(HttpURL::requirePathSegment);

        private final List<String> segments;
        private final boolean trailingSlash;
        private final UnaryOperator<String> validator;

        private Path(List<String> segments, boolean trailingSlash, UnaryOperator<String> validator) {
            this.segments = requireNonNull(segments);
            this.trailingSlash = trailingSlash;
            this.validator = requireNonNull(validator);
        }

        /** Creates a new, empty path, with a trailing slash, using {@link HttpURL#requirePathSegment} for segment validation. */
        public static Path empty() {
            return empty;
        }

        /** Creates a new, empty path, with a trailing slash, using the indicated validator for segments. */
        public static Path empty(Consumer<String> validator) {
            return new Path(List.of(), true, segmentValidator(validator));
        }

        /** Parses the given raw, normalized path string; this ignores whether the path is absolute or relative. */
        public static Path parse(String raw) {
            return parse(raw, HttpURL::requirePathSegment);
        }

        /** Parses the given raw, normalized path string; this ignores whether the path is absolute or relative.) */
        public static Path parse(String raw, Consumer<String> validator) {
            Path base = new Path(List.of(), raw.endsWith("/"), segmentValidator(validator));
            if (raw.startsWith("/")) raw = raw.substring(1);
            if (raw.isEmpty()) return base;
            List<String> segments = new ArrayList<>();
            for (String segment : raw.split("/")) segments.add(decode(segment, UTF_8));
            if (segments.isEmpty()) requireNonNormalizable(""); // Raw path was only slashes.
            return base.append(segments);
        }

        private static UnaryOperator<String> segmentValidator(Consumer<String> validator) {
            requireNonNull(validator, "segment validator cannot be null");
            return value -> {
                requireNonNormalizable(value);
                validator.accept(value);
                return value;
            };
        }

        private static String requireNonNormalizable(String segment) {
            return require( ! (segment.isEmpty() || segment.equals(".") || segment.equals("..")),
                           segment, "path segments cannot be \"\", \".\", or \"..\"");
        }

        /** Returns a copy of this where only the first segments are retained, and with a trailing slash. */
        public Path head(int count) {
            return count == segments.size() ? this : new Path(segments.subList(0, count), true, validator);
        }

        /** Returns a copy of this where only the last segments are retained. */
        public Path tail(int count) {
            return count == segments.size() ? this : new Path(segments.subList(segments.size() - count, segments.size()), trailingSlash, validator);
        }

        /** Returns a copy of this where the first segments are skipped. */
        public Path skip(int count) {
            return count == 0 ? this : new Path(segments.subList(count, segments.size()), trailingSlash, validator);
        }

        /** Returns a copy of this where the last segments are cut off, and with a trailing slash. */
        public Path cut(int count) {
            return count == 0 ? this : new Path(segments.subList(0, segments.size() - count), true, validator);
        }

        /** Returns a copy of this with the <em>decoded</em> segment appended at the end; it may not be either of {@code ""}, {@code "."} or {@code ".."}. */
        public Path append(String segment) {
            return append(List.of(segment), trailingSlash);
        }

        /** Returns a copy of this all segments of the other path appended, with a trailing slash as per the appendage. */
        public Path append(Path other) {
            return append(other.segments, other.trailingSlash);
        }

        /** Returns a copy of this all given segments appended, with a trailing slash as per this path. */
        public Path append(List<String> segments) {
            return append(segments, trailingSlash);
        }

        private Path append(List<String> segments, boolean trailingSlash) {
            List<String> copy = new ArrayList<>(this.segments);
            for (String segment : segments) copy.add(validator.apply(segment));
            return new Path(copy, trailingSlash, validator);
        }

        /** Whether this path has a trailing slash. */
        public boolean hasTrailingSlash() {
            return trailingSlash;
        }

        /** Returns a copy of this which encodes a trailing slash. */
        public Path withTrailingSlash() {
            return new Path(segments, true, validator);
        }

        /** Returns a copy of this which does not encode a trailing slash. */
        public Path withoutTrailingSlash() {
            return new Path(segments, false, validator);
        }

        /** The <em>URL decoded</em> segments that make up this path; never {@code null}, {@code ""}, {@code "."} or {@code ".."}. */
        public List<String> segments() {
            return Collections.unmodifiableList(segments);
        }

        /** A raw path string which parses to this, by splitting on {@code "/"}, and then URL decoding. */
        private String raw() {
            StringJoiner joiner = new StringJoiner("/", "/", trailingSlash ? "/" : "").setEmptyValue(trailingSlash ? "/" : "");
            for (String segment : segments) joiner.add(encode(segment, UTF_8));
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
            Path path = (Path) o;
            return trailingSlash == path.trailingSlash && segments.equals(path.segments);
        }

        @Override
        public int hashCode() {
            return Objects.hash(segments, trailingSlash);
        }

    }


    public static class Query {

        private static final Query empty = empty(__ -> { });

        private static class Node {

            final Node next;
            final String key;
            final String value;

            public Node(Node next, String key, String value) {
                this.next = next;
                this.key = key;
                this.value = value;
            }

        }

        private final Node head;
        private final UnaryOperator<String> validator;

        private Query(Node head, UnaryOperator<String> validator) {
            this.head = head;
            this.validator = requireNonNull(validator);
        }

        /** Creates a new, empty query part. */
        public static Query empty() {
            return empty;
        }

        /** Creates a new, empty query part, using the indicated string wrapper for keys and non-null values. */
        public static Query empty(Consumer<String> validator) {
            return new Query(null, entryValidator(validator));
        }

        /** Parses the given raw query string. */
        public static Query parse(String raw) {
            if (raw == null) return empty();
            return parse(raw, __-> { });
        }

        /** Parses the given raw query string, using the validator on all keys and non-null values. */
        public static Query parse(String raw, Consumer<String> validator) {
            if (raw == null) return empty(validator);
            Query query = empty(validator);
            for (String pair : raw.split("&")) {
                int split = pair.indexOf("=");
                if (split == -1) query = query.add(decode(pair, UTF_8));
                else query = query.add(decode(pair.substring(0, split), UTF_8),
                                       decode(pair.substring(split + 1), UTF_8)); // any additional '=' become part of the value
            }
            return query;
        }

        private static UnaryOperator<String> entryValidator(Consumer<String> validator) {
            requireNonNull(validator);
            return value -> {
                validator.accept(value);
                return value;
            };
        }

        /** Returns a copy of this with the <em>decoded</em> non-null key pointing to the <em>decoded</em> non-null value. */
        public Query add(String key, String value) {
            return new Query(new Node(head, validator.apply(requireNonNull(key)), validator.apply(requireNonNull(value))), validator);
        }

        /** Returns a copy of this with the <em>decoded</em> non-null key pointing to "nothing". */
        public Query add(String key) {
            return new Query(new Node(head, validator.apply(requireNonNull(key)), null), validator);
        }

        /** Returns a copy of this with the <em>decoded</em> non-null key pointing <em>only</em> to the <em>decoded</em> non-null value. */
        public Query set(String key, String value) {
            return remove(key).add(key, value);
        }

        /** Returns a copy of this with the <em>decoded</em> non-null key <em>only</em> pointing to "nothing". */
        public Query set(String key) {
            return remove(key).add(key);
        }

        /** Returns a copy of this without any key-value pair with the <em>decoded</em> key. */
        public Query remove(String key) {
            return new Query(without(validator.apply(requireNonNull(key)), head), validator);
        }

        private static Node without(String key, Node node) {
            if (node == null) return node;                  // null does not contain the key
            Node child = without(key, node.next);           // get a child that does not contain the key
            if (node.key.equals(key)) return child;         // if we have the key, unlink us
            if (child == node.next) return node;            // if our next didn't have the key, return unchanged
            return new Node(child, node.key, node.value);   // if our next has changed, we must change too
        }

        /** Returns a copy of this with all given mappings appended to this. {@code null} values, but not lists of values, are allowed. */
        public Query add(Map<String, ? extends Iterable<String>> values) {
            Query query = this;
            for (Map.Entry<String, ? extends Iterable<String>> entry : values.entrySet())
                for (String value : entry.getValue())
                    query = value == null ? query.add(entry.getKey())
                                          : query.add(entry.getKey(), value);

            return query;
        }

        /** Returns a copy of this with all given mappings added to this, possibly replacing existing mappings. */
        public Query set(Map<String, String> values) {
            Query query = this;
            for (Map.Entry<String, String> entry : values.entrySet())
                query = entry.getValue() == null ? query.set(entry.getKey())
                        : query.set(entry.getKey(), entry.getValue());

            return query;
        }

        /**
         * The <em>URL decoded</em> key-value pairs that make up this query;
         * keys and values may be {@code ""}, and values are {@code null} when only key was specified.
         * When a key was used multiple times, this map contains only the last value associated with the key.
         */
        public Map<String, String> lastEntries() {
            Map<String, String> entries = new LinkedHashMap<>();
            for (Node node : nodes())
                entries.put(node.key, node.value);

            return entries;
        }

        /**
         * The <em>URL decoded</em> key-value pairs that make up this query;
         * keys and values may be {@code ""}, and values (not lists of values) are {@code null} when only key was specified.
         * When a key was used multiple times, this map lists the values in the same order as they were given.
         */
        public Map<String, List<String>> entries() {
            Map<String, List<String>> entries = new LinkedHashMap<>();
            for (Node node : nodes())
                entries.computeIfAbsent(node.key, __ -> new ArrayList<>(2)).add(node.value);

            return entries;
        }

        /** A raw query string, with {@code '?'} prepended, that parses to this, by splitting on {@code "&"}, then on {@code "="}, and then URL decoding; or the empty string if this is empty. */
        private String raw() {
            StringJoiner joiner = new StringJoiner("&", "?", "").setEmptyValue("");
            for (Node node : nodes())
                joiner.add(encode(node.key, UTF_8) +
                           (node.value == null ? "" : "=" + encode(node.value, UTF_8)));

            return joiner.toString();
        }

        /** Nodes in insertion order. */
        private Iterable<Node> nodes() {
            Deque<Node> nodes = new ArrayDeque<>();
            for (Node node = head; node != null; node = node.next)
                nodes.push(node);

            return nodes;
        }

        /** Intentionally not usable for constructing new URIs. Use {@link HttpURL} for that instead. */
        @Override
        public String toString() {
            return head == null ? "no query" : "query '" + raw().substring(1) + "'";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Query query = (Query) o;
            return entries().equals(query.entries());
        }

        @Override
        public int hashCode() {
            return Objects.hash(entries());
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
