// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.net;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests the URI class
 *
 * @author bratseth
 */
public class URITestCase {

    @Test
    public void testEquality() {
        URI one = new URI("http://www.nils.arne.com");
        URI two = new URI("http://www.nils.arne.com");

        assertEquals(one, two);
        assertEquals(one.hashCode(), two.hashCode());
        assertEquals("http://www.nils.arne.com/", one.toString());

        assertEqualURIs(
                "http://info.t.fast.no/art.php?sid=29&mode=thread&order=0",
                "http://info.t.fast.no/art.php?sid=29&mode=thread&order=0");
        assertEqualURIs("http://a/g/", "http://a/g/");
        assertEquals("http://a/g;x?y#s",
                new URI("http://a/g;x?y#s", true).stringValue());
        assertEquals("http://a/g?y#s",
                new URI("http://a/g?y#s", true).stringValue());
        assertEqualURIs("http://a/b/c/.g", "http://a/b/c/.g");
        assertEqualURIs("http://a/b/c/..g", "http://a/b/c/..g");
        assertEqualURIs("http://a/b/c/g;x=1/y", "http://a/b/c/g;x=1/y");
        assertEquals("http://a/b/c/g#s/../x",
                new URI("http://a/b/c/g#s/../x", true).stringValue());
        assertEquals("http://www.strange_host.com/b",
                new URI("http://www.strange_host.com/b", true).stringValue());
    }

    @Test
    public void testOpaque() {
        URI uri = new URI("mailto:knut");

        assertEquals("mailto:knut", uri.toString());
        assertTrue(uri.isOpaque());
    }

    @Test
    public void testValid() {
        assertTrue(
                new URI("http://www.one.com/isValid?even=if&theres=args").isValid());
        assertTrue(
                !new URI("http://www.one.com/isValid?even=if&theres=args").isOpaque());

        assertTrue(!(new URI("not\\uri?", false, true).isValid()));

        assertTrue(new URI("http://www.strange_host.com/b").isValid());
        assertTrue(!new URI("http://www.strange_host.com/b").isOpaque());
    }

    @Test
    public void testSorting() {
        URI first = new URI("http://aisfirst.kanoo.com");
        URI second = new URI("www.thentheresw.com");

        assertTrue(first.compareTo(second) < 0);
        assertTrue(second.compareTo(second) == 0);
        assertTrue(second.compareTo(first) > 1);
    }

    @Test
    public void testHost() {
        assertEquals("a.b.c", new URI("http://A.B.C:567").getHost());
        assertEquals("www.kanoo.com",
                new URI("www.kanoo.com/foo", false, true).getHost());
        assertEquals("a.b.c", new URI("http://a.b.C/foo").getHost());
        assertEquals("a.b.c", new URI("http://a.b.C").getHost());
        assertEquals("a", new URI("http://A").getHost());
        assertEquals("a", new URI("http://A:80").getHost());
    }

    @Test
    public void testUnfragmenting() {
        assertEquals("http://www.sng.no/a/b/dee?kanoos&at=nught#chapter3",
                new URI("http://www.sng.no/a/b/cee/../dee?kanoos&at=nught#chapter3", true).stringValue());
        assertEquals("http://www.sng.no/a/b/dee?kanoos&at=nught",
                new URI("http://www.sng.no/a/b/cee/../dee?kanoos&at=nught#chapter3", false).stringValue());
    }

    @Test
    public void testNormalizing() {
        // Abbreviation resolving heuristics
        assertEquals("http://www.a.b/c",
                new URI("www.a.b/c", false, true).toString());
        assertEquals("file://x:\\a", new URI("x:\\a", false, true).toString());
        assertEquals("file://c:/a", new URI("c:/a", false, true).toString());

        // RFC 2396 normalizing
        assertEqualURIs("http://a/c/d", "http://a/b/../c/d");
        assertEqualURIs("http://a/b", "http://a/./b");

        // FAST normalizing
        assertEqualURIs("http://a/", "  http://a  ");
        assertEqualURIs("http://a/%e6;m%e5;ha%f8;l", "http://a/\u00E6m\u00E5ha\u00F8l");
        assertEqualURIs("http://a/&b", "http://a/&amp;b");
        assertEqualURIs("http://a/", "http://A");
        assertEqualURIs("http://a/", "http://a:80");
        assertEqualURIs("https://a/", "https://a:443");
        assertEqualURIs("http://a/", "http://a.");
        assertEqualURIs("http://a/b", "http://a//b");
        assertEqualURIs("http://a/b/", "http://A/b/");
        assertEqualURIs("http://a/b/", "http://a./b/");
        assertEqualURIs("http://a/", "http://a/b/../");
        assertEqualURIs("http://a/../", "http://a/b/../a/../../");
        assertEqualURIs("http://a/", "http://a/b/../");
        assertEqualURIs("http://a/b/c/d", "http://a/b/c/d");
        assertEqualURIs("http://a/b/c", "http://a/b/c#kanoo");

        // Everything combined
        assertEquals("http://www.a.b/m%e5;l/&/%f8;l&&/",
                new URI("   WWW.a.B:80//m\u00E5l/.//&amp;/./\u00F8l&amp;&amp;/foo/../upp/./..", true, true).toString());
    }

    @Test
    public void testParemeterAdding() {
        assertEquals("http://a/?knug=zagg",
                new URI("http://a/").addParameter("knug", "zagg").stringValue());
        assertEquals("http://a/b?knug=zagg&fjukk=barra",
                new URI("http://a/b?knug=zagg").addParameter("fjukk", "barra").stringValue());
    }

    private void assertEqualURIs(String fasit, String test) {
        assertEquals(fasit, new URI(test).toString());
    }

    @Test
    public void testDepth() {
        assertEquals(0, new URI("test:hit").getDepth());
        assertEquals(0, new URI("test://hit").getDepth());
        assertEquals(0, new URI("test://hit/").getDepth());
        assertEquals(1, new URI("test://hit.test/hello  ").getDepth());
        assertEquals(1, new URI("test://hit.test/hello/").getDepth());
        assertEquals(0, new URI("test:// ").getDepth());
        assertEquals(0, new URI("test:///").getDepth());
        assertEquals(1, new URI("test:////").getDepth());
        assertEquals(2, new URI("test://hit.test/hello/test2/").getDepth());
    }

    @Test
    public void testURLEmpty() {
        URI uri = new URI("", true);
        assertTrue(uri.isValid());
        assertNull(uri.getScheme());
        assertNull(uri.getHost());
        assertNull(uri.getDomain());
        assertNull(uri.getMainTld());
        assertEquals(-1, uri.getPort());
        assertNull(uri.getPath());
        assertNull(uri.getFilename());
        assertNull(uri.getExtension());
        assertNull(uri.getParams());
        assertNull(uri.getQuery());
        assertNull(uri.getFragment());
    }

    @Test
    public void testURLDot() {
        URI uri = new URI(".", true);
        assertTrue(uri.isValid());
        assertNull(uri.getScheme());
        assertNull(uri.getHost());
        assertNull(uri.getDomain());
        assertNull(uri.getMainTld());
        assertEquals(-1, uri.getPort());
        assertNull(uri.getPath());  //differs from FastS_URL, "."
        assertNull(uri.getFilename());  //differs from FastS_URL, "."
        assertNull(uri.getExtension());
        assertNull(uri.getParams());
        assertNull(uri.getQuery());
        assertNull(uri.getFragment());
    }

    @Test
    public void testURLDotDot() {
        URI uri = new URI("..", true);
        assertTrue(uri.isValid());
        assertNull(uri.getScheme());
        assertNull(uri.getHost());
        assertNull(uri.getDomain());
        assertNull(uri.getMainTld());
        assertEquals(-1, uri.getPort());
        assertNull(uri.getPath());  //differs from FastS_URL, ".."
        assertNull(uri.getFilename());  //differs from FastS_URL, ".."
        assertNull(uri.getExtension());
        assertNull(uri.getParams());
        assertNull(uri.getQuery());
        assertNull(uri.getFragment());
    }

    @Test
    public void testURLUninett() {
        URI uri = new URI("http://180.uninett.no/servlet/online.Bransje", true);
        assertTrue(uri.isValid());
        assertEquals("http", uri.getScheme());
        assertEquals("180.uninett.no", uri.getHost());
        assertEquals("uninett.no", uri.getDomain());
        assertEquals("no", uri.getMainTld());
        assertEquals(-1, uri.getPort());
        assertEquals("/servlet/online.Bransje", uri.getPath());
        assertEquals("online.Bransje", uri.getFilename());
        assertEquals("Bransje", uri.getExtension());
        assertNull(uri.getParams());
        assertNull(uri.getQuery());
        assertNull(uri.getFragment());
    }

    @Test
    public void testURLUnderdusken() {
        URI uri = new URI("http://www.underdusken.no", true);
        assertTrue(uri.isValid());
        assertEquals("http", uri.getScheme());
        assertEquals("www.underdusken.no", uri.getHost());
        assertEquals("underdusken.no", uri.getDomain());
        assertEquals("no", uri.getMainTld());
        assertEquals(-1, uri.getPort());
        assertEquals("", uri.getPath());
        assertEquals("", uri.getFilename());
        assertNull(uri.getExtension());
        assertNull(uri.getParams());
        assertNull(uri.getQuery());
        assertNull(uri.getFragment());
    }

    @Test
    public void testURLUnderduskenUholdbar() {
        URI uri =
                new URI("http://www.underdusken.no/?page=dusker/html/0008/Uholdbar.html", true);
        assertTrue(uri.isValid());
        assertEquals("http", uri.getScheme());
        assertEquals("www.underdusken.no", uri.getHost());
        assertEquals("underdusken.no", uri.getDomain());
        assertEquals("no", uri.getMainTld());
        assertEquals(-1, uri.getPort());
        assertEquals("/", uri.getPath());
        assertEquals("", uri.getFilename());
        assertNull(uri.getExtension());
        assertNull(uri.getParams());
        assertEquals("page=dusker/html/0008/Uholdbar.html", uri.getQuery());
        assertNull(uri.getFragment());
    }

    @Test
    public void testURLUniKarlsruhe() {
        URI uri = new URI("http://www.uni-karlsruhe.de/~ig25/ssh-faq/", true);
        assertTrue(uri.isValid());
        assertEquals("http", uri.getScheme());
        assertEquals("www.uni-karlsruhe.de", uri.getHost());
        assertEquals("uni-karlsruhe.de", uri.getDomain());
        assertEquals("de", uri.getMainTld());
        assertEquals(-1, uri.getPort());
        assertEquals("/~ig25/ssh-faq/", uri.getPath());
        assertEquals("", uri.getFilename());
        assertNull(uri.getExtension());
        assertNull(uri.getParams());
        assertNull(uri.getQuery());
        assertNull(uri.getFragment());
    }

    @Test
    public void testURLDetteErEn() {
        URI uri = new URI("https://dette.er.en:2020/~janie/index.htm?param1=q&param2=r", true);
        assertTrue(uri.isValid());
        assertEquals("https", uri.getScheme());
        assertEquals("dette.er.en", uri.getHost());
        assertEquals("er.en", uri.getDomain());
        assertEquals("en", uri.getMainTld());
        assertEquals(2020, uri.getPort());
        assertEquals("/~janie/index.htm", uri.getPath());
        assertEquals("index.htm", uri.getFilename());
        assertEquals("htm", uri.getExtension());
        assertNull(uri.getParams());
        assertEquals("param1=q&param2=r", uri.getQuery());
        assertNull(uri.getFragment());
    }

    @Test
    public void testURLSonyCoUk() {
        URI uri = new URI("http://www.sony.co.uk/", true);
        assertTrue(uri.isValid());
        assertEquals("http", uri.getScheme());
        assertEquals("www.sony.co.uk", uri.getHost());
        assertEquals("sony.co.uk", uri.getDomain());
        assertEquals("uk", uri.getMainTld());
        assertEquals(-1, uri.getPort());
        assertEquals("/", uri.getPath());
        assertEquals("", uri.getFilename());
        assertNull(uri.getExtension());
        assertNull(uri.getParams());
        assertNull(uri.getQuery());
        assertNull(uri.getFragment());
    }

    @Test
    public void testURLSonyCoUk2() {
        URI uri = new URI("http://sony.co.uk/", true);
        assertTrue(uri.isValid());
        assertEquals("http", uri.getScheme());
        assertEquals("sony.co.uk", uri.getHost());
        //TODO: Fix when tldlist is implemented:
        //assertEquals("sony.co.uk", uri.getDomain());
        assertEquals("co.uk", uri.getDomain());
        assertEquals("uk", uri.getMainTld());
        assertEquals(-1, uri.getPort());
        assertEquals("/", uri.getPath());
        assertEquals("", uri.getFilename());
        assertNull(uri.getExtension());
        assertNull(uri.getParams());
        assertNull(uri.getQuery());
        assertNull(uri.getFragment());
    }

    @Test
    public void testURLSomehostSomedomain() {
        URI uri = new URI("http://somehost.somedomain/this!is!it/boom", true);
        assertTrue(uri.isValid());
        assertEquals("http", uri.getScheme());
        assertEquals("somehost.somedomain", uri.getHost());
        assertEquals("somehost.somedomain", uri.getDomain());
        assertEquals("somedomain", uri.getMainTld());
        assertEquals(-1, uri.getPort());
        assertEquals("/this!is!it/boom", uri.getPath());
        assertEquals("boom", uri.getFilename());
        assertNull(uri.getExtension());
        assertNull(uri.getParams());
        assertNull(uri.getQuery());
        assertNull(uri.getFragment());
    }

    @Test
    public void testURLTestCom() {
        URI uri = new URI("http://test.com/index.htm?p1=q%20test&p2=r%10d", true);
        assertTrue(uri.isValid());
        assertEquals("http", uri.getScheme());
        assertEquals("test.com", uri.getHost());
        assertEquals("test.com", uri.getDomain());
        assertEquals("com", uri.getMainTld());
        assertEquals(-1, uri.getPort());
        assertEquals("/index.htm", uri.getPath());
        assertEquals("index.htm", uri.getFilename());
        assertEquals("htm", uri.getExtension());
        assertNull(uri.getParams());
        assertEquals("p1=q%20test&p2=r%10d", uri.getQuery());
        assertNull(uri.getFragment());
    }

    @Test
    public void testURLArthur() {
        URI uri = new URI("http://arthur/qm/images/qm1.gif", true);
        assertTrue(uri.isValid());
        assertEquals("http", uri.getScheme());
        assertEquals("arthur", uri.getHost());
        assertEquals("arthur", uri.getDomain());
        assertNull(uri.getMainTld());
        assertEquals(-1, uri.getPort());
        assertEquals("/qm/images/qm1.gif", uri.getPath());
        assertEquals("qm1.gif", uri.getFilename());
        assertEquals("gif", uri.getExtension());
        assertNull(uri.getParams());
        assertNull(uri.getQuery());
        assertNull(uri.getFragment());
    }

    @Test
    public void testURLFooCom() {
        URI uri = new URI("http://foo.com/ui;.gif", true);
        assertTrue(uri.isValid());
        assertEquals("http", uri.getScheme());
        assertEquals("foo.com", uri.getHost());
        assertEquals("foo.com", uri.getDomain());
        assertEquals("com", uri.getMainTld());
        assertEquals(-1, uri.getPort());
        assertEquals("/ui;.gif", uri.getPath());
        assertEquals("ui", uri.getFilename());
        assertNull(uri.getExtension());
        assertEquals(".gif", uri.getParams());
        assertNull(uri.getQuery());
        assertNull(uri.getFragment());
    }

    @Test
    public void testURLFooCom2() {
        URI uri = new URI("http://foo.com/ui;par1=1/par2=2", true);
        assertTrue(uri.isValid());
        assertEquals("http", uri.getScheme());
        assertEquals("foo.com", uri.getHost());
        assertEquals("foo.com", uri.getDomain());
        assertEquals("com", uri.getMainTld());
        assertEquals(-1, uri.getPort());
        assertEquals("/ui;par1=1/par2=2", uri.getPath());
        assertEquals("ui", uri.getFilename());
        assertNull(uri.getExtension());
        assertEquals("par1=1/par2=2", uri.getParams());
        assertNull(uri.getQuery());
        assertNull(uri.getFragment());
    }

    @Test
    public void testURLFooNo() {
        URI uri = new URI(
                "http://www.foo.no:8080/path/filename.ext;par1=hello/par2=world?query=test#fragment", true);
        assertTrue(uri.isValid());
        assertEquals("http", uri.getScheme());
        assertEquals("www.foo.no", uri.getHost());
        assertEquals("foo.no", uri.getDomain());
        assertEquals("no", uri.getMainTld());
        assertEquals(8080, uri.getPort());
        assertEquals("/path/filename.ext;par1=hello/par2=world", uri.getPath());
        assertEquals("filename.ext", uri.getFilename());
        assertEquals("ext", uri.getExtension());
        assertEquals("par1=hello/par2=world", uri.getParams());
        assertEquals("query=test", uri.getQuery());
        assertEquals("fragment", uri.getFragment());
    }

    @Test
    public void testURLAmpersand() {
        URI uri = new URI("http://canonsarang.com/zboard/data/gallery04/HU&BANG.jpg", true);
        assertTrue(uri.isValid());
        assertEquals("http", uri.getScheme());
        assertEquals("canonsarang.com", uri.getHost());
        assertEquals("canonsarang.com", uri.getDomain());
        assertEquals("com", uri.getMainTld());
        assertEquals(-1, uri.getPort());
        assertEquals("/zboard/data/gallery04/HU&BANG.jpg", uri.getPath());
        assertEquals("HU&BANG.jpg", uri.getFilename());
        assertEquals("jpg", uri.getExtension());
        assertNull(uri.getParams());
        assertNull(uri.getQuery());
        assertNull(uri.getFragment());
    }

    @Test
    public void testQMark() {
        URI uri = new URI("http://foobar/?");
        assertTrue(uri.isValid());
        assertEquals("http", uri.getScheme());
        assertEquals("foobar", uri.getHost());
        assertEquals("", uri.getQuery());
    }

    @Test
    public void testTokenization() {
        URI uri = new URI("http://this.i_s:5000/wo_ho;ba-lo?gobo#banana", true);
        List<URI.Token> tokens = uri.tokenize();
        URI.Token token;

        token = tokens.get(0);
        assertEquals("http", token.getToken());
        assertEquals(URI.URLContext.URL_SCHEME, token.getContext());

        token = tokens.get(1);
        assertEquals("this", token.getToken());
        assertEquals(URI.URLContext.URL_HOST, token.getContext());

        token = tokens.get(2);
        assertEquals("i_s", token.getToken());
        assertEquals(URI.URLContext.URL_HOST, token.getContext());

        token = tokens.get(3);
        assertEquals("5000", token.getToken());
        assertEquals(URI.URLContext.URL_PORT, token.getContext());

        token = tokens.get(4);
        assertEquals("wo_ho", token.getToken());
        assertEquals(URI.URLContext.URL_PATH, token.getContext());

        token = tokens.get(5);
        assertEquals("ba-lo", token.getToken());
        assertEquals(URI.URLContext.URL_PATH, token.getContext());

        token = tokens.get(6);
        assertEquals("gobo", token.getToken());
        assertEquals(URI.URLContext.URL_QUERY, token.getContext());

        token = tokens.get(7);
        assertEquals("banana", token.getToken());
        assertEquals(URI.URLContext.URL_FRAGMENT, token.getContext());

        try {
            tokens.get(8);
            fail();
        } catch (IndexOutOfBoundsException ioobe) {
        }
    }

    @Test
    public void testFileURIEmptyHost() {
        URI uri = new URI("file:///C:/Inetpub/wwwroot/DW_SHORTCUTS.htm");
        List<URI.Token> tokens = uri.tokenize();
        URI.Token token;
        token = tokens.get(0);
        assertEquals("file", token.getToken());
        assertEquals(URI.URLContext.URL_SCHEME, token.getContext());

        token = tokens.get(1);
        assertEquals("localhost", token.getToken());
        assertEquals(URI.URLContext.URL_HOST, token.getContext());

        token = tokens.get(2);
        assertEquals("C", token.getToken());
        assertEquals(URI.URLContext.URL_PATH, token.getContext());

        token = tokens.get(3);
        assertEquals("Inetpub", token.getToken());
        assertEquals(URI.URLContext.URL_PATH, token.getContext());

        token = tokens.get(4);
        assertEquals("wwwroot", token.getToken());
        assertEquals(URI.URLContext.URL_PATH, token.getContext());

        token = tokens.get(5);
        assertEquals("DW_SHORTCUTS", token.getToken());
        assertEquals(URI.URLContext.URL_PATH, token.getContext());

        token = tokens.get(6);
        assertEquals("htm", token.getToken());
        assertEquals(URI.URLContext.URL_PATH, token.getContext());

        try {
            tokens.get(7);
            fail();
        } catch (IndexOutOfBoundsException ioobe) {
            // Success
        }
    }

}
