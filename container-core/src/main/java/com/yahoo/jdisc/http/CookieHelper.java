// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http;

import com.yahoo.jdisc.HeaderFields;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Helper for encoding/decoding cookies on request/response.
 *
 * @author bjorncs
 */
public class CookieHelper {

    private CookieHelper() {}

    public static List<Cookie> decodeSetCookieHeader(HeaderFields headers) {
        List<String> cookies = headers.get(HttpHeaders.Names.SET_COOKIE);
        if (cookies == null) {
            return Collections.emptyList();
        }
        List<Cookie> ret = new LinkedList<>();
        for (String cookie : cookies) {
            ret.add(Cookie.fromSetCookieHeader(cookie));
        }
        return ret;
    }

    public static void encodeSetCookieHeader(HeaderFields headers, List<Cookie> cookies) {
        headers.remove(HttpHeaders.Names.SET_COOKIE);
        for (Cookie cookie : cookies) {
            headers.add(HttpHeaders.Names.SET_COOKIE, Cookie.toSetCookieHeaders(Arrays.asList(cookie)));
        }
    }
}
