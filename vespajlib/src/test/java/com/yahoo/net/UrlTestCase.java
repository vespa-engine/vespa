// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.net;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class UrlTestCase {

    @Test
    public void requireThatAccessorsWork() {
        Url url = Url.fromString("scheme://user:pass@host:69/path?query#fragment");
        assertEquals("scheme://user:pass@host:69/path?query#fragment", url.toString());
        assertEquals("scheme", url.getScheme());
        assertEquals(0, url.getSchemeBegin());
        assertEquals(6, url.getSchemeEnd());
        assertEquals("user", url.getUserInfo());
        assertEquals(9, url.getUserInfoBegin());
        assertEquals(13, url.getUserInfoEnd());
        assertEquals("pass", url.getPassword());
        assertEquals(14, url.getPasswordBegin());
        assertEquals(18, url.getPasswordEnd());
        assertEquals("host", url.getHost());
        assertEquals(19, url.getHostBegin());
        assertEquals(23, url.getHostEnd());
        assertEquals("69", url.getPortString());
        assertEquals(24, url.getPortBegin());
        assertEquals(26, url.getPortEnd());
        assertEquals(Integer.valueOf(69), url.getPort());
        assertEquals("/path", url.getPath());
        assertEquals(26, url.getPathBegin());
        assertEquals(31, url.getPathEnd());
        assertEquals("query", url.getQuery());
        assertEquals(32, url.getQueryBegin());
        assertEquals(37, url.getQueryEnd());
        assertEquals("fragment", url.getFragment());
        assertEquals(38, url.getFragmentBegin());
        assertEquals(46, url.getFragmentEnd());
    }

    @Test
    public void requireThatOffsetsAreNeverOutOfBounds() {
        Url url = Url.fromString("http:");
        assertEquals(0, url.getSchemeBegin());
        assertEquals(4, url.getSchemeEnd());
        assertEquals(5, url.getUserInfoBegin());
        assertEquals(5, url.getUserInfoEnd());
        assertEquals(5, url.getPasswordBegin());
        assertEquals(5, url.getPasswordEnd());
        assertEquals(5, url.getHostBegin());
        assertEquals(5, url.getHostEnd());
        assertEquals(5, url.getPortBegin());
        assertEquals(5, url.getPortEnd());
        assertEquals(5, url.getPathBegin());
        assertEquals(5, url.getPathEnd());
        assertEquals(5, url.getQueryBegin());
        assertEquals(5, url.getQueryEnd());
        assertEquals(5, url.getFragmentBegin());
        assertEquals(5, url.getFragmentEnd());

        url = Url.fromString("//host");
        assertEquals(0, url.getSchemeBegin());
        assertEquals(0, url.getSchemeEnd());
        assertEquals(2, url.getUserInfoBegin());
        assertEquals(2, url.getUserInfoEnd());
        assertEquals(2, url.getPasswordBegin());
        assertEquals(2, url.getPasswordEnd());
        assertEquals(2, url.getHostBegin());
        assertEquals(6, url.getHostEnd());
        assertEquals(6, url.getPortBegin());
        assertEquals(6, url.getPortEnd());
        assertEquals(6, url.getPathBegin());
        assertEquals(6, url.getPathEnd());
        assertEquals(6, url.getQueryBegin());
        assertEquals(6, url.getQueryEnd());
        assertEquals(6, url.getFragmentBegin());
        assertEquals(6, url.getFragmentEnd());
    }

    @Test
    public void requireThatCommonSchemesCanBeParsed() {
        assertParse("ftp://ftp.is.co.za/rfc/rfc1808.txt",
                    "ftp", null, null, "ftp.is.co.za", null, "/rfc/rfc1808.txt", null, null);
        assertParse("http://www.ietf.org/rfc/rfc 2396.txt",
                    "http", null, null, "www.ietf.org", null, "/rfc/rfc 2396.txt", null, null);
        assertParse("ldap://[2001:db8::7]/c=GB?objectClass?one",
                    "ldap", null, null, "2001:db8::7", null, "/c=GB", "objectClass?one", null);
        assertParse("mailto:John.Doe@example.com",
                    "mailto", null, null, null, null, "John.Doe@example.com", null, null);
        assertParse("news:comp.infosystems.www.servers.unix",
                    "news", null, null, null, null, "comp.infosystems.www.servers.unix", null, null);
        assertParse("tel:+1-816-555-1212",
                    "tel", null, null, null, null, "+1-816-555-1212", null, null);
        assertParse("telnet://192.0.2.16:80/",
                    "telnet", null, null, "192.0.2.16", 80, "/", null, null);
        assertParse("urn:oasis:names:specification:docbook:dtd:xml:4.1.2",
                    "urn", null, null, null, null, "oasis:names:specification:docbook:dtd:xml:4.1.2", null, null);
    }

    @Test
    public void requireThatAllComponentsCanBeParsed() {
        assertParse("scheme:",
                    "scheme", null, null, null, null, null, null, null);
        assertParse("scheme://",
                    "scheme", null, null, null, null, "//", null, null);
        assertParse("scheme://host",
                    "scheme", null, null, "host", null, null, null, null);
        try {
            assertParse("scheme://host:foo",
                        null, null, null, null, null, null, null, null);
            fail();
        } catch (NumberFormatException e) {
            // expected
        }
        assertParse("scheme://host:69",
                    "scheme", null, null, "host", 69, null, null, null);
        assertParse("scheme://user@host:69",
                    "scheme", "user", null, "host", 69, null, null, null);
        assertParse("scheme://user:pass@host:69",
                    "scheme", "user", "pass", "host", 69, null, null, null);
        assertParse("scheme://user:pass@host:69",
                    "scheme", "user", "pass", "host", 69, null, null, null);
        assertParse("scheme://user:pass@host:69/",
                    "scheme", "user", "pass", "host", 69, "/", null, null);
        assertParse("scheme://user:pass@host:69/path",
                    "scheme", "user", "pass", "host", 69, "/path", null, null);
        assertParse("scheme://user:pass@host:69/path?query",
                    "scheme", "user", "pass", "host", 69, "/path", "query", null);
        assertParse("scheme://user:pass@host:69/path?query#fragment",
                    "scheme", "user", "pass", "host", 69, "/path", "query", "fragment");
        assertParse("scheme://user@host:69/path?query#fragment",
                    "scheme", "user", null, "host", 69, "/path", "query", "fragment");
        assertParse("scheme://host:69/path?query#",
                    "scheme", null, null, "host", 69, "/path", "query", null);
        assertParse("scheme://host:69/path?query#fragment",
                    "scheme", null, null, "host", 69, "/path", "query", "fragment");
        assertParse("scheme://host/path?query#fragment",
                    "scheme", null, null, "host", null, "/path", "query", "fragment");
        assertParse("scheme:///path?query#fragment",
                    "scheme", null, null, null, null, "///path", "query", "fragment");
        assertParse("scheme://?query#fragment",
                    "scheme", null, null, null, null, "//", "query", "fragment");
        assertParse("scheme://#fragment",
                    "scheme", null, null, null, null, "//", null, "fragment");
    }

    @Test
    public void requireThatIPv6CanBeParsed() {
        assertParse("http://[2001:0db8:85a3:0000:0000:8a2e:0370:7334]",
                    "http", null, null, "2001:0db8:85a3:0000:0000:8a2e:0370:7334", null, null, null, null);
        assertParse("http://[2001:0db8:85a3:0000:0000:8a2e:0370:7334]/path",
                    "http", null, null, "2001:0db8:85a3:0000:0000:8a2e:0370:7334", null, "/path", null, null);

        assertParse("http://[2001:0db8:85a3:0000:0000:8a2e:0370:7334]:80",
                    "http", null, null, "2001:0db8:85a3:0000:0000:8a2e:0370:7334", 80, null, null, null);
        assertParse("http://[2001:0db8:85a3:0000:0000:8a2e:0370:7334]:80/path",
                    "http", null, null, "2001:0db8:85a3:0000:0000:8a2e:0370:7334", 80, "/path", null, null);
    }

    private static void assertParse(String input, String scheme, String userInfo, String password, String host,
                                    Integer port, String path, String query, String fragment)
    {
        Url urlA = Url.fromString(input);
        assertEquals("Image", input, urlA.toString());
        assertUrl(urlA, scheme, userInfo, password, host, port, path, query, fragment);

        Url urlB = new Url(urlA.getScheme(), urlA.getUserInfo(), urlA.getPassword(), urlA.getHost(), urlA.getPort(),
                           urlA.getPath(), urlA.getQuery(), urlA.getFragment());
        assertUrl(urlB, scheme, userInfo, password, host, port, path, query, fragment);

        Url urlC = Url.fromString(urlB.toString());
        assertEquals(urlB, urlC);
        assertUrl(urlC, scheme, userInfo, password, host, port, path, query, fragment);
    }

    private static void assertUrl(Url url, String scheme, String userInfo, String password, String host, Integer port,
                                  String path, String query, String fragment)
    {
        assertEquals("Scheme", scheme, url.getScheme());
        assertEquals("User", userInfo, url.getUserInfo());
        assertEquals("Password", password, url.getPassword());
        assertEquals("Host", host, url.getHost());
        assertEquals("Port", port, url.getPort());
        assertEquals("Path", path, url.getPath());
        assertEquals("Query", query, url.getQuery());
        assertEquals("Fragment", fragment, url.getFragment());
    }
}
