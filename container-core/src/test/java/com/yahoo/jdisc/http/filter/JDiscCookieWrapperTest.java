// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.jdisc.http.Cookie;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JDiscCookieWrapperTest {

    @Test
    void requireThatWrapWorks() {
        Cookie cookie = new Cookie("name", "value");
        JDiscCookieWrapper wrapper = JDiscCookieWrapper.wrap(cookie);

        wrapper.setDomain("yahoo.com");
        wrapper.setMaxAge(10);
        wrapper.setPath("/path");

        assertEquals(wrapper.getName(), cookie.getName());
        assertEquals(wrapper.getValue(), cookie.getValue());
        assertEquals(wrapper.getDomain(), cookie.getDomain());
        assertEquals(wrapper.getMaxAge(), cookie.getMaxAge(TimeUnit.SECONDS));
        assertEquals(wrapper.getPath(), cookie.getPath());
        assertEquals(wrapper.getSecure(), cookie.isSecure());

    }
}
