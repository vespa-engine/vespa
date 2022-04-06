// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import ai.vespa.validation.Name;
import com.yahoo.net.DomainName;
import com.yahoo.restapi.HttpURL.Query;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Consumer;

import static com.yahoo.net.DomainName.localhost;
import static com.yahoo.restapi.HttpURL.Scheme.http;
import static com.yahoo.restapi.HttpURL.Scheme.https;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author jonmv
 */
class HttpURLTest {

    @Test
    void testConversionBackAndForth() {
        for (String uri : List.of("http://minimal",
                                  "http://empty.query?",
                                  "http://zero-port:0?no=path",
                                  "http://only-path/",
                                  "https://strange/queries?=&foo",
                                  "https://weirdness?=foo",
                                  "https://encoded/%3F%3D%26%2F?%3F%3D%26%2F=%3F%3D%26%2F",
                                  "https://host.at.domain:123/one/two/?three=four&five")) {
            Consumer<String> pathValidator = __ -> { };
            assertEquals(uri, HttpURL.from(URI.create(uri), pathValidator, pathValidator).asURI().toString(),
                         "uri '" + uri + "' should be returned unchanged");
        }
    }

    @Test
    void testModification() {
        HttpURL url = HttpURL.create(http, localhost).withPath(HttpURL.Path.empty(Name::of));
        assertEquals(http, url.scheme());
        assertEquals(localhost, url.domain());
        assertEquals(OptionalInt.empty(), url.port());
        assertEquals(HttpURL.Path.empty(Name::of), url.path());
        assertEquals(HttpURL.Query.empty(Name::of), url.query());

        url = url.withScheme(https)
                 .withDomain(DomainName.of("domain"))
                 .withPort(0)
                 .withPath(url.path().append("foo").withoutTrailingSlash())
                 .withQuery(url.query().put("boo", "bar").add("baz"));
        assertEquals(https, url.scheme());
        assertEquals(DomainName.of("domain"), url.domain());
        assertEquals(OptionalInt.of(0), url.port());
        assertEquals(HttpURL.Path.parse("/foo", Name::of), url.path());
        assertEquals(HttpURL.Query.parse("boo=bar&baz", Name::of), url.query());
    }

    @Test
    void testInvalidURIs() {
        assertEquals("scheme must be HTTP or HTTPS",
                     assertThrows(IllegalArgumentException.class,
                                  () -> HttpURL.from(URI.create("file:/txt"))).getMessage());

        assertEquals("URI must specify a host",
                     assertThrows(NullPointerException.class,
                                  () -> HttpURL.from(URI.create("http:///foo"))).getMessage());

        assertEquals("port number must be at least '-1' and at most '65535', but got: '65536'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> HttpURL.from(URI.create("http://foo:65536/bar"))).getMessage());

        assertEquals("uri should be normalized, but got: http://foo//",
                     assertThrows(IllegalArgumentException.class,
                                  () -> HttpURL.from(URI.create("http://foo//"))).getMessage());

        assertEquals("uri should be normalized, but got: http://foo/./",
                     assertThrows(IllegalArgumentException.class,
                                  () -> HttpURL.from(URI.create("http://foo/./"))).getMessage());

        assertEquals("path segments cannot be \"\", \".\", or \"..\", but got: '..'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> HttpURL.from(URI.create("http://foo/.."))).getMessage());

        assertEquals("path segments cannot be \"\", \".\", or \"..\", but got: '..'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> HttpURL.from(URI.create("http://foo/.%2E"))).getMessage());

        assertEquals("name must match '[A-Za-z][A-Za-z0-9_-]{0,63}', but got: '/'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> HttpURL.from(URI.create("http://foo/%2F"), Name::of, Name::of)).getMessage());

        assertEquals("name must match '[A-Za-z][A-Za-z0-9_-]{0,63}', but got: '/'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> HttpURL.from(URI.create("http://foo?%2F"), Name::of, Name::of)).getMessage());

        assertEquals("name must match '[A-Za-z][A-Za-z0-9_-]{0,63}', but got: ''",
                     assertThrows(IllegalArgumentException.class,
                                  () -> HttpURL.from(URI.create("http://foo?"), Name::of, Name::of)).getMessage());
    }

    @Test
    void testPath() {
        HttpURL.Path path = HttpURL.Path.parse("foo/bar/baz", Name::of);
        List<String> expected = List.of("foo", "bar", "baz");
        assertEquals(expected, path.segments());

        assertEquals(expected.subList(1, 3), path.skip(1).segments());
        assertEquals(expected.subList(0, 2), path.cut(1).segments());
        assertEquals(expected.subList(1, 2), path.skip(1).cut(1).segments());

        assertEquals("path '/foo/bar/baz/'", path.withTrailingSlash().toString());
        assertEquals(path, path.withoutTrailingSlash().withoutTrailingSlash());

        assertEquals(List.of("one", "foo", "bar", "baz", "two"),
                     HttpURL.Path.from(List.of("one")).append(path).append("two").segments());

        assertEquals(List.of(expected.get(2), expected.get(0)),
                     path.append(path).cut(2).skip(2).segments());

        for (int i = 0; i < 3; i++) {
            assertEquals(path.head(i), path.cut(3 - i));
            assertEquals(path.tail(i), path.skip(3 - i));
        }

        assertThrows(NullPointerException.class,
                     () -> path.append((String) null));

        List<String> names = new ArrayList<>();
        names.add(null);
        assertThrows(NullPointerException.class,
                     () -> path.append(names));

        assertEquals("name must match '[A-Za-z][A-Za-z0-9_-]{0,63}', but got: '???'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> path.append("???")).getMessage());

        assertEquals("fromIndex(2) > toIndex(1)",
                     assertThrows(IllegalArgumentException.class,
                                  () -> path.cut(2).skip(2)).getMessage());

        assertEquals("path segment decoded cannot contain '/', but got: '/'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> HttpURL.Path.empty().append("%2525252525252525%2525252525253%25252532%252525%252534%36")).getMessage());

        assertEquals("path segment decoded cannot contain '?', but got: '?'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> HttpURL.Path.empty().append("?")).getMessage());

        assertEquals("path segment decoded cannot contain '#', but got: '#'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> HttpURL.Path.empty().append("#")).getMessage());

        assertEquals("path segments cannot be \"\", \".\", or \"..\", but got: '..'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> HttpURL.Path.empty().append("%2E%25252E")).getMessage());
    }

    @Test
    void testQuery() {
        Query query = Query.parse("foo=bar&baz", Name::of);
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("foo", "bar");
        expected.put("baz", null);
        assertEquals(expected, query.entries());

        expected.remove("baz");
        assertEquals(expected, query.remove("baz").entries());

        expected.put("baz", null);
        expected.remove("foo");
        assertEquals(expected, query.remove("foo").entries());
        assertEquals(expected, Query.empty(Name::of).add("baz").entries());

        assertEquals("query '?foo=bar&baz=bax&quu=fez&moo'",
                     query.put("baz", "bax").merge(Query.from(Map.of("quu", "fez"))).add("moo").toString());

        assertThrows(NullPointerException.class,
                     () -> query.remove(null));

        assertThrows(NullPointerException.class,
                     () -> query.add(null));

        assertThrows(NullPointerException.class,
                     () -> query.put(null, "hax"));

        assertThrows(NullPointerException.class,
                     () -> query.put("hax", null));

        Map<String, String> names = new LinkedHashMap<>();
        names.put(null, "hax");
        assertThrows(NullPointerException.class,
                     () -> query.merge(names));
    }

}
