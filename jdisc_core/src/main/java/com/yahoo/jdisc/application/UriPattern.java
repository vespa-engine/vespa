// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>This class holds a regular expression designed so that it only matches certain {@link URI}s. The constructor of
 * this class accepts a simplified pattern string, and turns that into something that can be used to quickly match
 * against URIs. This class also implements {@link Comparable} in such a way that stricter patterns order before looser
 * patterns.</p>
 *
 * <p>Here are some examples of ordering:</p>
 * <ul>
 * <li><code>http://host/path</code> evaluated before <code>*://host/path</code></li>
 * <li><code>http://host/path</code> evaluated before <code>http://&#42;/path</code></li>
 * <li><code>http://a.host/path</code> evaluated before <code>http://*.host/path</code></li>
 * <li><code>http://*.host/path</code> evaluated before <code>http://host/path</code></li>
 * <li><code>http://host.a/path</code> evaluated before <code>http://host.&#42;/path</code></li>
 * <li><code>http://host.&#42;/path</code> evaluated before <code>http://host/path</code></li>
 * <li><code>http://host:80/path</code> evaluated before <code>http://host:&#42;/path</code></li>
 * <li><code>http://host/path</code> evaluated before <code>http://host/*</code></li>
 * <li><code>http://host/path/*</code> evaluated before <code>http://host/path</code></li>
 * </ul>
 *
 * @author Simon Thoresen Hult
 * @author bjorncs
 */
public class UriPattern implements Comparable<UriPattern> {

    public static final int DEFAULT_PRIORITY = 0;
    private static final Pattern PATTERN = Pattern.compile("([^:]+)://([^:/]+)(:((\\*)|([0-9]+)))?/(.*)",
                                                           Pattern.UNICODE_CASE | Pattern.CANON_EQ);
    private final String pattern;
    private final GlobPattern scheme;
    private final GlobPattern host;
    private final int port;
    private final GlobPattern path;

    // TODO Vespa 8 jonmv remove
    private final int priority;

    /**
     * <p>Creates a new instance of this class that represents the given pattern string, with a priority of <code>0</code>.
     * The input string must be on the form <code>&lt;scheme&gt;://&lt;host&gt;[:&lt;port&gt;]&lt;path&gt;</code>, where
     * '*' can be used as a wildcard character at any position.</p>
     *
     * @param uri The pattern to parse.
     * @throws IllegalArgumentException If the pattern could not be parsed.
     */
    public UriPattern(String uri) {
        Matcher matcher = PATTERN.matcher(uri);
        if ( ! matcher.find())
            throw new IllegalArgumentException(uri);

        scheme = GlobPattern.compile(normalizeScheme(nonNullOrWildcard(matcher.group(1))));
        host = GlobPattern.compile(nonNullOrWildcard(matcher.group(2)));
        port = parseOrZero(matcher.group(4));
        path = GlobPattern.compile(nonNullOrWildcard(matcher.group(7)));
        pattern = scheme + "://" + host + ":" + (port > 0 ? port : "*") + "/" + path;
        this.priority = DEFAULT_PRIORITY;
    }

    /**
     * <p>Creates a new instance of this class that represents the given pattern string, with the given priority. The
     * input string must be on the form <code>&lt;scheme&gt;://&lt;host&gt;[:&lt;port&gt;]&lt;path&gt;</code>, where
     * '*' can be used as a wildcard character at any position.</p>
     *
     * @deprecated Use {@link #UriPattern(String)} and let's avoid another complication here.
     * @param uri      The pattern to parse.
     * @param priority The priority of this pattern.
     * @throws IllegalArgumentException If the pattern could not be parsed.
     */
    @Deprecated(forRemoval = true, since = "7")
    public UriPattern(String uri, int priority) {
        Matcher matcher = PATTERN.matcher(uri);
        if (!matcher.find()) {
            throw new IllegalArgumentException(uri);
        }
        scheme = GlobPattern.compile(normalizeScheme(nonNullOrWildcard(matcher.group(1))));
        host = GlobPattern.compile(nonNullOrWildcard(matcher.group(2)));
        port = parseOrZero(matcher.group(4));
        path = GlobPattern.compile(nonNullOrWildcard(matcher.group(7)));
        pattern = scheme + "://" + host + ":" + (port > 0 ? port : "*") + "/" + path;
        this.priority = priority;
    }

    /**
     * <p>Attempts to match the given {@link URI} to this pattern. Note that only the scheme, host, port, and path
     * components of the URI are used, <em>and these must all be defined</em>. Only <em>absolute</em> URIs are supported.
     * Any user info, query or fragment part is ignored.</p>
     *
     * @param uri The URI to match.
     * @return A {@link Match} object describing the match found, or null if not found.
     */
    public Match match(URI uri) {
        if ( ! uri.isAbsolute() || uri.getHost() == null) // URI must have scheme, host and absolute (or empty) path.
            return null;

        // Performance optimization: match in order of increasing cost and decreasing discriminating power.
        if (port > 0 && port != uri.getPort())
            return null;

        GlobPattern.Match pathMatch = path.match(uri.getRawPath(), uri.getRawPath().isEmpty() ? 0 : 1); // Strip leading '/'.
        if (pathMatch == null)
            return null;

        GlobPattern.Match hostMatch = host.match(uri.getHost());
        if (hostMatch == null)
            return null;

        GlobPattern.Match schemeMatch = scheme.match(normalizeScheme(uri.getScheme()));
        if (schemeMatch == null)
            return null;

        return new Match(schemeMatch, hostMatch, port > 0 ? 0 : uri.getPort(), pathMatch);
    }

    @Override
    public int hashCode() {
        return pattern.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof UriPattern && pattern.equals(((UriPattern) obj).pattern);
    }

    @Override
    public String toString() {
        return pattern;
    }

    @Override
    public int compareTo(UriPattern rhs) {
        int cmp;
        cmp = rhs.priority - priority;
        if (cmp != 0) {
            return cmp;
        }
        cmp = scheme.compareTo(rhs.scheme);
        if (cmp != 0) {
            return cmp;
        }
        cmp = host.compareTo(rhs.host);
        if (cmp != 0) {
            return cmp;
        }
        cmp = path.compareTo(rhs.path);
        if (cmp != 0) {
            return cmp;
        }
        cmp = rhs.port - port;
        if (cmp != 0) {
            return cmp;
        }
        return 0;
    }

    private static String nonNullOrBlank(String str) {
        return str != null ? str : "";
    }

    private static String nonNullOrWildcard(String val) {
        return val != null ? val : "*";
    }

    private static int parseOrZero(String str) {
        if (str == null || str.equals("*")) {
            return 0;
        }
        return Integer.parseInt(str);
    }

    private static String normalizeScheme(String scheme) {
        if (scheme.equals("https")) return "http"; // handle 'https' in bindings and uris as 'http'
        return scheme;
    }

    /**
     * <p>This class holds the result of a {@link UriPattern#match(URI)} operation. It contains methods to inspect the
     * groups captured during matching, where a <em>group</em> is defined as a sequence of characters matches by a
     * wildcard in the {@link UriPattern}.</p>
     */
    public static class Match {

        private final GlobPattern.Match scheme;
        private final GlobPattern.Match host;
        private final int port;
        private final GlobPattern.Match path;

        private Match(GlobPattern.Match scheme, GlobPattern.Match host, int port, GlobPattern.Match path) {
            this.scheme = scheme;
            this.host = host;
            this.port = port;
            this.path = path;
        }

        /**
         * <p>Returns the number of captured groups of this match. Any non-negative integer smaller than the value
         * returned by this method is a valid group index for this match.</p>
         *
         * @return The number of captured groups.
         */
        public int groupCount() {
            return scheme.groupCount() + host.groupCount() + (port > 0 ? 1 : 0) + path.groupCount();
        }

        /**
         * <p>Returns the input subsequence captured by the given group by this match. Groups are indexed from left to
         * right, starting at zero. Note that some groups may match an empty string, in which case this method returns
         * the empty string. This method never returns null.</p>
         *
         * @param idx The index of the group to return.
         * @return The (possibly empty) substring captured by the group during matching, never <code>null</code>.
         * @throws IndexOutOfBoundsException If there is no group in the match with the given index.
         */
        public String group(int idx) {
            int len = scheme.groupCount();
            if (idx < len) {
                return scheme.group(idx);
            }
            idx = idx - len;
            len = host.groupCount();
            if (idx < len) {
                return host.group(idx);
            }
            idx = idx - len;
            len = port > 0 ? 1 : 0;
            if (idx < len) {
                return String.valueOf(port);
            }
            idx = idx - len;
            return path.group(idx);
        }
    }
}
