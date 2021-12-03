// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.servlet;

import com.yahoo.jdisc.HeaderFields;
import com.yahoo.jdisc.http.Cookie;

import java.util.List;
import java.util.Map;

/**
 * Common interface for JDisc and servlet http responses.
 */
public interface ServletOrJdiscHttpResponse {

    public void copyHeaders(HeaderFields target);

    public int getStatus();

    public Map<String, Object> context();

    public List<Cookie> decodeSetCookieHeader();

}
