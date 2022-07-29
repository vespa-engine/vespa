// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.test.NonWorkingRequestHandler;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Simon Thoresen Hult
 */
public class BindingSetTestCase {

    @Test
    void requireThatAccessorsWork() {
        Map<UriPattern, RequestHandler> handlers = new LinkedHashMap<>();
        RequestHandler foo = new NonWorkingRequestHandler();
        handlers.put(new UriPattern("http://host/foo"), foo);
        RequestHandler bar = new NonWorkingRequestHandler();
        handlers.put(new UriPattern("http://host/bar"), bar);
        BindingSet<RequestHandler> bindings = new BindingSet<>(handlers.entrySet());

        Iterator<Map.Entry<UriPattern, RequestHandler>> it = bindings.iterator();
        assertNotNull(it);
        assertTrue(it.hasNext());
        Map.Entry<UriPattern, RequestHandler> entry = it.next();
        assertNotNull(entry);
        assertEquals(new UriPattern("http://host/foo"), entry.getKey());
        assertSame(foo, entry.getValue());
        assertTrue(it.hasNext());
        assertNotNull(entry = it.next());
        assertEquals(new UriPattern("http://host/bar"), entry.getKey());
        assertSame(bar, entry.getValue());
        assertFalse(it.hasNext());
    }

    @Test
    void requireThatSimpleResolutionWorks() {
        Map<UriPattern, RequestHandler> handlers = new LinkedHashMap<>();
        RequestHandler foo = new NonWorkingRequestHandler();
        handlers.put(new UriPattern("http://host/foo"), foo);
        RequestHandler bar = new NonWorkingRequestHandler();
        handlers.put(new UriPattern("http://host/bar"), bar);

        BindingSet<RequestHandler> bindings = new BindingSet<>(handlers.entrySet());
        BindingMatch<RequestHandler> match = bindings.match(URI.create("http://host/foo"));
        assertNotNull(match);
        assertEquals(0, match.groupCount());
        assertSame(foo, match.target());
        assertSame(foo, bindings.resolve(URI.create("http://host/foo")));

        assertNotNull(match = bindings.match(URI.create("http://host/bar")));
        assertEquals(0, match.groupCount());
        assertSame(bar, match.target());
        assertSame(bar, bindings.resolve(URI.create("http://host/bar")));
    }

    @Test
    void requireThatPatternResolutionWorks() {
        Map<UriPattern, RequestHandler> handlers = new LinkedHashMap<>();
        RequestHandler foo = new NonWorkingRequestHandler();
        handlers.put(new UriPattern("http://host/*"), foo);
        RequestHandler bar = new NonWorkingRequestHandler();
        handlers.put(new UriPattern("http://host/path"), bar);

        BindingSet<RequestHandler> bindings = new BindingSet<>(handlers.entrySet());
        BindingMatch<RequestHandler> match = bindings.match(URI.create("http://host/anon"));
        assertNotNull(match);
        assertEquals(1, match.groupCount());
        assertEquals("anon", match.group(0));
        assertSame(foo, match.target());
        assertSame(foo, bindings.resolve(URI.create("http://host/anon")));

        assertNotNull(match = bindings.match(URI.create("http://host/path")));
        assertEquals(0, match.groupCount());
        assertSame(bar, match.target());
        assertSame(bar, bindings.resolve(URI.create("http://host/path")));
    }

    @Test
    void requireThatPatternResolutionWorksForWildCards() {
        Map<UriPattern, RequestHandler> handlers = new LinkedHashMap<>();
        RequestHandler foo = new NonWorkingRequestHandler();
        handlers.put(new UriPattern("http://host:*/bar"), foo);
        RequestHandler bob = new NonWorkingRequestHandler();
        handlers.put(new UriPattern("http://*abc:*/*bar"), bob);
        RequestHandler car = new NonWorkingRequestHandler();
        handlers.put(new UriPattern("*://*:21/*"), car);


        BindingSet<RequestHandler> bindings = new BindingSet<>(handlers.entrySet());
        BindingMatch<RequestHandler> match = bindings.match(URI.create("http://host:8080/bar"));
        assertNotNull(match);
        assertEquals(1, match.groupCount());
        assertEquals("8080", match.group(0));
        assertSame(foo, match.target());
        assertSame(foo, bindings.resolve(URI.create("http://host:8080/bar")));

        match = bindings.match(URI.create("http://host:8080/foo/bar"));
        assertNull(match);

        match = bindings.match(URI.create("http://xyzabc:8080/pqrbar"));
        assertNotNull(match);
        assertSame(bob, match.target());

        match = bindings.match(URI.create("ftp://lmn:21/abc"));
        assertNotNull(match);
        assertSame(car, match.target());
    }

    @Test
    void requireThatPatternResolutionWorksForFilters() {
        Map<UriPattern, RequestHandler> handlers = new LinkedHashMap<>();
        RequestHandler foo = new NonWorkingRequestHandler();
        handlers.put(new UriPattern("http://*/filtered/*"), foo);

        BindingSet<RequestHandler> bindings = new BindingSet<>(handlers.entrySet());
        BindingMatch<RequestHandler> match = bindings.match(URI.create("http://localhost:80/status.html"));
        assertNull(match);
        match = bindings.match(URI.create("http://localhost/filtered/status.html"));
        assertNotNull(match);
        assertSame(foo, match.target());
    }

    @Test
    void requireThatTreeSplitCanBeBoundForSchemes() {
        Map<UriPattern, RequestHandler> handlers = new LinkedHashMap<>();
        RequestHandler httpfoo = new NonWorkingRequestHandler();
        RequestHandler httpsfoo = new NonWorkingRequestHandler();
        RequestHandler ftpfoo = new NonWorkingRequestHandler();
        handlers.put(new UriPattern("http://host/foo"), httpfoo);
        handlers.put(new UriPattern("https://host/foo"), httpsfoo);
        handlers.put(new UriPattern("ftp://host/foo"), ftpfoo);
        BindingSet<RequestHandler> bindings = new BindingSet<>(handlers.entrySet());
        assertNotNull(bindings);
    }

    @Test
    void requireThatTreeSplitCanBeBoundForHosts() {
        Map<UriPattern, RequestHandler> handlers = new LinkedHashMap<>();
        RequestHandler foo = new NonWorkingRequestHandler();
        RequestHandler foobar = new NonWorkingRequestHandler();
        RequestHandler fooqux = new NonWorkingRequestHandler();
        handlers.put(new UriPattern("http://hostabc/foo"), foobar);
        handlers.put(new UriPattern("http://hostpqr/foo"), fooqux);
        handlers.put(new UriPattern("http://host/foo"), foo);
        BindingSet<RequestHandler> bindings = new BindingSet<>(handlers.entrySet());
        assertNotNull(bindings);
    }

    @Test
    void requireThatTreeSplitCanBeBoundForPorts() {
        Map<UriPattern, RequestHandler> handlers = new LinkedHashMap<>();
        RequestHandler foo8080 = new NonWorkingRequestHandler();
        RequestHandler foo80 = new NonWorkingRequestHandler();
        RequestHandler foobar = new NonWorkingRequestHandler();
        RequestHandler foopqrbar = new NonWorkingRequestHandler();

        handlers.put(new UriPattern("http://host:8080/foo"), foo8080);
        handlers.put(new UriPattern("http://host:70/foo"), foo80);
        handlers.put(new UriPattern("http://hostpqr:70/foo"), foopqrbar);
        handlers.put(new UriPattern("http://host:80/foobar"), foobar);
        BindingSet<RequestHandler> bindings = new BindingSet<>(handlers.entrySet());
        assertNotNull(bindings);
    }

    @Test
    void requireThatTreeSplitCanBeBoundForPaths() {
        Map<UriPattern, RequestHandler> handlers = new LinkedHashMap<>();
        RequestHandler foo = new NonWorkingRequestHandler();
        RequestHandler foobar = new NonWorkingRequestHandler();
        RequestHandler fooqux = new NonWorkingRequestHandler();
        handlers.put(new UriPattern("http://host/foobar"), foobar);
        handlers.put(new UriPattern("http://host/fooqux"), fooqux);
        handlers.put(new UriPattern("http://host/foo"), foo);
        BindingSet<RequestHandler> bindings = new BindingSet<>(handlers.entrySet());
        assertNotNull(bindings);
    }

    @Test
    void requireThatTreeSplitCanBeBoundForWildcards() {
        Map<UriPattern, RequestHandler> handlers = new LinkedHashMap<>();
        RequestHandler foo8080 = new NonWorkingRequestHandler();
        RequestHandler foo80 = new NonWorkingRequestHandler();
        RequestHandler foobar = new NonWorkingRequestHandler();
        RequestHandler foopqrbar = new NonWorkingRequestHandler();

        handlers.put(new UriPattern("http://host:8080/foo"), foo8080);
        handlers.put(new UriPattern("http://host:708/foo"), foo80);
        handlers.put(new UriPattern("http://host:80/foobar"), foobar);
        handlers.put(new UriPattern("http://hos*:708/foo"), foopqrbar);
        BindingSet<RequestHandler> bindings = new BindingSet<>(handlers.entrySet());
        assertNotNull(bindings);
        assertSame(foopqrbar, bindings.resolve(URI.create("http://hostabc:708/foo")));
        assertSame(foo80, bindings.resolve(URI.create("http://host:708/foo")));
        assertSame(foo8080, bindings.resolve(URI.create("http://host:8080/foo")));
    }

    @Test
    void requireThatTreeWorksForURIWithQueryOrFragments() {
        Map<UriPattern, RequestHandler> handlers = new LinkedHashMap<>();
        RequestHandler foo = new NonWorkingRequestHandler();
        handlers.put(new UriPattern("http://*/application/v1/session"), foo);
        BindingSet<RequestHandler> bindings = new BindingSet<>(handlers.entrySet());
        assertNotNull(bindings);
        assertSame(foo, bindings.resolve(URI.create("http://abcxyz.yahoo.com:19071" +
                "/application/v1/session?name=base")));
        assertSame(foo, bindings.resolve(URI.create("http://abcxyz.yahoo.com:19071" +
                "/application/v1/session#application")));
    }

    @Test
    void requireThatTreeWorksForURIWithPathWildCards() {
        Map<UriPattern, RequestHandler> handlers = new LinkedHashMap<>();
        RequestHandler foo = new NonWorkingRequestHandler();
        RequestHandler foo1 = new NonWorkingRequestHandler();
        RequestHandler foo2 = new NonWorkingRequestHandler();
        RequestHandler foo3 = new NonWorkingRequestHandler();
        RequestHandler foo4 = new NonWorkingRequestHandler();
        RequestHandler foo5 = new NonWorkingRequestHandler();
        RequestHandler foo6 = new NonWorkingRequestHandler();
        RequestHandler foo7 = new NonWorkingRequestHandler();
        RequestHandler foo8 = new NonWorkingRequestHandler();
        RequestHandler foo9 = new NonWorkingRequestHandler();
        RequestHandler foo10 = new NonWorkingRequestHandler();
        RequestHandler foo11 = new NonWorkingRequestHandler();
        RequestHandler foo12 = new NonWorkingRequestHandler();
        RequestHandler foo13 = new NonWorkingRequestHandler();
        RequestHandler foo14 = new NonWorkingRequestHandler();
        RequestHandler foo15 = new NonWorkingRequestHandler();

        handlers.put(new UriPattern("http://*/config/v1/*"), foo);
        handlers.put(new UriPattern("http://*/config/v1/*/"), foo1);
        handlers.put(new UriPattern("http://*/config/v1/*/*"), foo2);
        handlers.put(new UriPattern("http://*/config/v1/*/*/"), foo3);
        handlers.put(new UriPattern("http://*/application/v2/tenant*"), foo4);
        handlers.put(new UriPattern("http://*/application/v2/tenant/*"), foo5);
        handlers.put(new UriPattern("http://*/application/v2/tenant/*/session"), foo6);
        handlers.put(new UriPattern("http://*/application/v2/tenant/*/session/*/prepared"), foo7);
        handlers.put(new UriPattern("http://*/application/v2/tenant/*/session/*/active"), foo8);
        handlers.put(new UriPattern("http://*/application/v2/tenant/*/session/*/content/*"), foo9);
        handlers.put(new UriPattern("http://*/application/v2/tenant/*/application/"), foo10);
        handlers.put(new UriPattern("http://*/application/v2/tenant/*/application/*/environment/*/" +
                "region/*/instance/*/content/*"), foo11);
        handlers.put(new UriPattern("http://*/config/v2/tenant/*/application/*/*"), foo12);
        handlers.put(new UriPattern("http://*/config/v2/tenant/*/application/*/*/*"), foo13);
        handlers.put(new UriPattern("http://*/config/v2/tenant/*/application/*/environment" +
                "/*/region/*/instance/*/*"), foo14);
        handlers.put(new UriPattern("http://*/config/v2/tenant/*/application/*/*/*/"), foo15);


        BindingSet<RequestHandler> bindings = new BindingSet<>(handlers.entrySet());
        assertNotNull(bindings);
        assertSame(foo, bindings.resolve(URI.create("http://abcxyz.yahoo.com:19071" +
                "/config/v1/cloud.config.log.logd")));
        assertSame(foo1, bindings.resolve(URI.create("http://abcxyz.yahoo.com:19071" +
                "/config/v1/cloud.config.log.logd/")));
        assertSame(foo2, bindings.resolve(URI.create("http://abcxyz.yahoo.com:19071" +
                "/config/v1/cloud.config.log.logd/admin")));
        assertSame(foo3, bindings.resolve(URI.create("http://abcxyz.yahoo.com:19071" +
                "/config/v1/cloud.config.log.logd/admin/")));
        assertSame(foo4, bindings.resolve(URI.create("http://abcxyz.yahoo.com:19071" +
                "/application/v2/tenant")));
        assertSame(foo5, bindings.resolve(URI.create("http://abcxyz.yahoo.com:19071" +
                "/application/v2/tenant/b")));
        assertSame(foo6, bindings.resolve(URI.create("http://abcxyz.yahoo.com:19071" +
                "/application/v2/tenant/bar/session")));
        assertSame(foo7, bindings.resolve(URI.create("http://abcxyz.yahoo.com:19071" +
                "/application/v2/tenant/bar/session/aef/prepared")));
        assertSame(foo8, bindings.resolve(URI.create("http://abcxyz.yahoo.com:19071" +
                "/application/v2/tenant/bar/session/a/active")));
        assertSame(foo9, bindings.resolve(URI.create("http://abcxyz.yahoo.com:19071" +
                "/application/v2/tenant/bar/session/aef/content/x")));
        assertSame(foo10, bindings.resolve(URI.create("http://abcxyz.yahoo.com:19071" +
                "/application/v2/tenant/bar/session/application/")));
        assertSame(foo11, bindings.resolve(URI.create("http://abcxyz.yahoo.com:19071" +
                "/application/v2/tenant/bar/application/bbc/environment/xyz/region/m/inst" +
                "ance/a/content/l")));
        assertSame(foo12, bindings.resolve(URI.create("http://abcxyz.yahoo.com:19071" +
                "/config/v2/tenant/bar/application/bbc/xyz")));
        assertSame(foo13, bindings.resolve(URI.create("http://abcxyz.yahoo.com:19071" +
                "/config/v2/tenant/bar/application/bbc/xyz/a")));
        assertSame(foo14, bindings.resolve(URI.create("http://abcxyz.yahoo.com:19071" +
                "/config/v2/tenant/bar/application/bbc/environment/a/region/b/instance/a/b")));
        assertSame(foo15, bindings.resolve(URI.create("http://abcxyz.yahoo.com:19071" +
                "/config/v2/tenant/bar/application/bbc/xyz/a/c/")));
    }

    @Test
    void requireThatPathOverPortWorks() {
        Map<UriPattern, RequestHandler> handlers = new LinkedHashMap<>();
        RequestHandler applicationStatus = new NonWorkingRequestHandler();
        RequestHandler search = new NonWorkingRequestHandler();
        RequestHandler legacy = new NonWorkingRequestHandler();
        handlers.put(new UriPattern("http://*/processing/*"), new NonWorkingRequestHandler());
        handlers.put(new UriPattern("http://*/statistics/*"), new NonWorkingRequestHandler());
        handlers.put(new UriPattern("http://*/state/v1/*"), new NonWorkingRequestHandler());
        handlers.put(new UriPattern("http://*/search/*"), search);
        handlers.put(new UriPattern("http://*/status.html"), new NonWorkingRequestHandler());
        handlers.put(new UriPattern("http://*/ApplicationStatus"), applicationStatus);
        handlers.put(new UriPattern("http://*:" + getDefaults().vespaWebServicePort() + "/*"), legacy);
        BindingSet<RequestHandler> bindings = new BindingSet<>(handlers.entrySet());
        assertNotNull(bindings);

        assertSame(applicationStatus, bindings.resolve(URI.create
                ("http://abcxyz.yahoo.com:" + getDefaults().vespaWebServicePort() + "/ApplicationStatus")));
        assertSame(search, bindings.resolve(URI.create
                ("http://abcxyz.yahoo.com:" + getDefaults().vespaWebServicePort() + "/search/?query=sddocname:music")));
        assertSame(legacy, bindings.resolve(URI.create
                ("http://abcxyz.yahoo.com:" + getDefaults().vespaWebServicePort() + "/stats/?query=stat:query")));
    }

    @Test
    void requireThatPathOverPortsDoNotWorkOverStricterPatterns() {
        Map<UriPattern, RequestHandler> handlers = new LinkedHashMap<>();
        RequestHandler foo = new NonWorkingRequestHandler();
        RequestHandler bar = new NonWorkingRequestHandler();
        handlers.put(new UriPattern("http://host:4050/a/"), foo);
        handlers.put(new UriPattern("http://host/a/"), bar);
        BindingSet<RequestHandler> bindings = new BindingSet<>(handlers.entrySet());
        assertNotNull(bindings);
        assertSame(foo, bindings.resolve(URI.create("http://host:4050/a/")));
    }

    @Test
    void requireThatSchemeOrderOverHost() {
        Map<UriPattern, RequestHandler> handlers = new LinkedHashMap<>();
        RequestHandler foo = new NonWorkingRequestHandler();
        RequestHandler bar = new NonWorkingRequestHandler();
        handlers.put(new UriPattern("http://host:5050/a/"), foo);
        handlers.put(new UriPattern("ftp://host:5050/a/"), bar);
        BindingSet<RequestHandler> bindings = new BindingSet<>(handlers.entrySet());
        assertNotNull(bindings);
        assertSame(foo, bindings.resolve(URI.create("http://host:5050/a/")));
        assertSame(bar, bindings.resolve(URI.create("ftp://host:5050/a/")));
    }

    @Test
    void requireThatPortsAreOrdered() {
        Map<UriPattern, RequestHandler> handlers = new LinkedHashMap<>();
        RequestHandler foo = new NonWorkingRequestHandler();
        RequestHandler bar = new NonWorkingRequestHandler();
        RequestHandler car = new NonWorkingRequestHandler();
        handlers.put(new UriPattern("http://host:5050/a/"), foo);
        handlers.put(new UriPattern("http://host:5051/a/"), bar);
        handlers.put(new UriPattern("http://host/a/"), car);
        BindingSet<RequestHandler> bindings = new BindingSet<>(handlers.entrySet());
        assertNotNull(bindings);
        assertSame(foo, bindings.resolve(URI.create("http://host:5050/a/")));
        assertSame(bar, bindings.resolve(URI.create("http://host:5051/a/")));
        assertSame(car, bindings.resolve(URI.create("http://host/a/")));
        assertSame(car, bindings.resolve(URI.create("http://host:8080/a/")));
        assertSame(car, bindings.resolve(URI.create("http://host:80/a/")));
    }

    @Test
    void requireThatPathsAreOrdered() {
        Map<UriPattern, RequestHandler> handlers = new LinkedHashMap<>();
        RequestHandler foo = new NonWorkingRequestHandler();
        RequestHandler bar = new NonWorkingRequestHandler();
        RequestHandler car = new NonWorkingRequestHandler();
        handlers.put(new UriPattern("http://host:5050/a/"), foo);
        handlers.put(new UriPattern("http://host:5050/b/"), bar);
        handlers.put(new UriPattern("http://host/a/"), car);
        BindingSet<RequestHandler> bindings = new BindingSet<>(handlers.entrySet());
        assertNotNull(bindings);
        assertSame(foo, bindings.resolve(URI.create("http://host:5050/a/")));
        assertSame(bar, bindings.resolve(URI.create("http://host:5050/b/")));
        assertSame(car, bindings.resolve(URI.create("http://host/a/")));
        assertSame(car, bindings.resolve(URI.create("http://host:8080/a/")));
        assertSame(car, bindings.resolve(URI.create("http://host:80/a/")));
    }

    @Test
    void requireThatStrictPatternsOrderBeforeWildcards() {
        Map<UriPattern, RequestHandler> handlers = new LinkedHashMap<>();

        RequestHandler fooScheme = new NonWorkingRequestHandler();
        RequestHandler barScheme = new NonWorkingRequestHandler();

        RequestHandler fooHost = new NonWorkingRequestHandler();
        RequestHandler barHost = new NonWorkingRequestHandler();

        RequestHandler fooPort = new NonWorkingRequestHandler();
        RequestHandler barPort = new NonWorkingRequestHandler();
        RequestHandler carPort = new NonWorkingRequestHandler();

        RequestHandler fooPath = new NonWorkingRequestHandler();
        RequestHandler barPath = new NonWorkingRequestHandler();

        handlers.put(new UriPattern("http://host/x/"), fooScheme);
        handlers.put(new UriPattern("*://host/x/"), barScheme);

        handlers.put(new UriPattern("http://host/abc/"), fooHost);
        handlers.put(new UriPattern("http://*/abc/"), barHost);

        handlers.put(new UriPattern("http://host:*/a/"), fooPort);
        handlers.put(new UriPattern("http://host:5050/b/"), barPort);
        handlers.put(new UriPattern("http://host/b/"), carPort);

        handlers.put(new UriPattern("http://hostname/abcde/"), fooPath);
        handlers.put(new UriPattern("http://hostname/*/"), barPath);

        BindingSet<RequestHandler> bindings = new BindingSet<>(handlers.entrySet());
        assertNotNull(bindings);
        assertSame(fooScheme, bindings.resolve(URI.create("http://host/x/")));
        assertSame(barScheme, bindings.resolve(URI.create("ftp://host/x/")));

        assertSame(fooHost, bindings.resolve(URI.create("http://host:8080/abc/")));
        assertSame(barHost, bindings.resolve(URI.create("http://lmn:5050/abc/")));

        assertSame(fooPort, bindings.resolve(URI.create("http://host:5050/a/")));
        assertSame(barPort, bindings.resolve(URI.create("http://host:5050/b/")));
        assertSame(carPort, bindings.resolve(URI.create("http://host/b/")));
        assertSame(carPort, bindings.resolve(URI.create("http://host:8080/b/")));
        assertSame(carPort, bindings.resolve(URI.create("http://host:80/b/")));
        assertSame(fooPath, bindings.resolve(URI.create("http://hostname/abcde/")));
        assertSame(barPath, bindings.resolve(URI.create("http://hostname/abcd/")));

    }

    @Test
    void requireThatToStringMethodWorks() {
        Map<UriPattern, RequestHandler> handlers = new LinkedHashMap<>();
        RequestHandler foo = new NonWorkingRequestHandler();
        RequestHandler bar = new NonWorkingRequestHandler();
        handlers.put(new UriPattern("http://host/foo"), foo);
        handlers.put(new UriPattern("http://host/bar"), bar);
        BindingSet<RequestHandler> bindings = new BindingSet<>(handlers.entrySet());
        assertNotNull(bindings);
        assertNotNull(bindings.toString());  //Just to get code coverage.
    }


    @Test
    void requireThatPatternsAreOrderedMoreSpecificToLess() {
        assertOrder("3://host/path", "2://host/path", "1://host/path");
        assertOrder("http://3/path", "http://2/path", "http://1/path");
        assertOrder("http://host:3/path", "http://host:2/path", "http://host:1/path");
        assertOrder("http://host/3", "http://host/2", "http://host/1");
        assertOrder("http://*/*", "*://host/2", "*://host/1");
        assertOrder("http://host/*", "http://*/2", "http://*/1");
        assertOrder("http://host:*/3", "http://host:2/2", "http://host:1/1");
        assertOrder("http://host/*/3/2/", "http://host/*/1/2", "http://host/*/2/*");
        assertOrder("http://host:69/path",
                "http://host/*",
                "http://*:69/path",
                "http://*/path",
                "http://*:69/*",
                "http://*/*",
                "*://host/path",
                "*://*/path",
                "*://*/*");
        assertOrder("http://*/HelloWorld",
                "http://*:4080/state/v1/*",
                "http://*:4083/*",
                "http://*:4081/*",
                "http://*:4080/*");
    }

    private static void assertOrder(String... expected) {
        for (int off = 0; off < expected.length; ++off) {
            List<String> actual = new ArrayList<>();
            for (int i = 0; i < expected.length; ++i) {
                actual.add(expected[(off + i) % expected.length]);
            }
            assertOrder(Arrays.asList(expected), actual);

            actual = new ArrayList<>();
            for (int i = expected.length; --i >= 0; ) {
                actual.add(expected[(off + i) % expected.length]);
            }
            assertOrder(Arrays.asList(expected), actual);
        }
    }

    private static void assertOrder(List<String> expected, List<String> actual) {
        BindingRepository<Object> repo = new BindingRepository<>();
        for (String pattern : actual) {
            repo.bind(pattern, new Object());
        }
        BindingSet<Object> bindings = repo.activate();
        Iterator<Map.Entry<UriPattern, Object>> it = bindings.iterator();
        for (String pattern : expected) {
            assertTrue(it.hasNext());
            assertEquals(new UriPattern(pattern), it.next().getKey());
        }
        assertFalse(it.hasNext());
    }
}
