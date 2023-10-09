// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.net;

import static org.junit.Assert.*;

import java.net.URISyntaxException;

import org.junit.Test;

/**
 * Check validity of the URI helper methods.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class UriToolsTestCase {

    private static final String SEARCH_QUERY = "/search/?query=sddocname:music#trick";

    @Test
    public final void testRawRequest() throws URISyntaxException {
        java.net.URI uri = new java.net.URI("http://localhost:" + 8080 + SEARCH_QUERY);
        assertEquals(SEARCH_QUERY, UriTools.rawRequest(uri));
    }

}
