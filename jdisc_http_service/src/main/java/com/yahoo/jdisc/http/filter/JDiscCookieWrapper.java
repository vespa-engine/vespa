// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.jdisc.http.Cookie;

import java.util.concurrent.TimeUnit;

/**
 * Wrapper of Cookie.
 *
 * @author tejalk
 *
 */
public class JDiscCookieWrapper {

    private Cookie cookie;

    protected JDiscCookieWrapper(Cookie cookie) {
        this.cookie = cookie;
    }

    public static JDiscCookieWrapper wrap(Cookie cookie) {
        return new JDiscCookieWrapper(cookie);
    }

    @Deprecated
    public String getComment() {
        return cookie.getComment();
    }

    public String getDomain() {
        return cookie.getDomain();
    }

    public int getMaxAge() {
        return cookie.getMaxAge(TimeUnit.SECONDS);
    }

    public String getName() {
        return cookie.getName();
    }

    public String getPath() {
        return cookie.getPath();
    }

    public boolean getSecure() {
        return cookie.isSecure();
    }

    public String getValue() {
        return cookie.getValue();
    }

    @Deprecated
    public int getVersion() {
        return cookie.getVersion();
    }

    @Deprecated
    public void setComment(String purpose) {
        cookie.setComment(purpose);
    }

    public void setDomain(String pattern) {
        cookie.setDomain(pattern);
    }

    public void setMaxAge(int expiry) {
        cookie.setMaxAge(expiry, TimeUnit.SECONDS);
    }

    public void setPath(String uri) {
        cookie.setPath(uri);
    }

    public void setSecure(boolean flag) {
      cookie.setSecure(flag);
    }

    public void setValue(String newValue) {
       cookie.setValue(newValue);
    }

    @Deprecated
    public void setVersion(int version) {
       cookie.setVersion(version);
    }

    /**
     * Return com.yahoo.jdisc.http.Cookie
     *
     * @return - cookie
     */
    public Cookie getCookie() {
       return cookie;
    }

}
