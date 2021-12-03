// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import com.yahoo.jdisc.http.servlet.ServletOrJdiscHttpResponse;

import com.yahoo.jdisc.HeaderFields;
import com.yahoo.jdisc.http.Cookie;


import com.yahoo.jdisc.http.HttpResponse;

/**
 * This class was made abstract from 5.27. Test cases that need
 * a concrete instance should create a {@link JdiscFilterResponse}.
 *
 * @author tejalk
 */
public abstract class DiscFilterResponse {

	private final ServletOrJdiscHttpResponse parent;
	private final HeaderFields untreatedHeaders;
	private final List<Cookie> untreatedCookies;

	public DiscFilterResponse(ServletOrJdiscHttpResponse parent) {
		this.parent = parent;

        this.untreatedHeaders = new HeaderFields();
        parent.copyHeaders(untreatedHeaders);

		this.untreatedCookies = getCookies();
	}

    /* Attributes on the response are only used for unit testing.
     * There is no such thing as 'attributes' in the underlying response. */

	public Enumeration<String> getAttributeNames() {
		return Collections.enumeration(parent.context().keySet());
	}

	public Object getAttribute(String name) {
		return parent.context().get(name);
	}

	public void setAttribute(String name, Object value) {
		parent.context().put(name, value);
	}

	public void removeAttribute(String name) {
		parent.context().remove(name);
	}

	/**
	 * Returns the untreatedHeaders from the parent request
	 */
	public HeaderFields getUntreatedHeaders() {
		return untreatedHeaders;
	}

	/**
	 * Returns the untreatedCookies from the parent request
     */
    public List<Cookie> getUntreatedCookies() {
		return untreatedCookies;
	}

    /**
     * Sets a header with the given name and value.
     * <p>
     * If the header had already been set, the new value overwrites the previous one.
     */
    public abstract void setHeader(String name, String value);

    public abstract void removeHeaders(String name);

	/**
     * Sets a header with the given name and value.
     * <p>
     * If the header had already been set, the new value overwrites the previous one.
     */
	public abstract void setHeaders(String name, String value);

	/**
     * Sets a header with the given name and value.
     * <p>
     * If the header had already been set, the new value overwrites the previous one.
     */
	public abstract void setHeaders(String name, List<String> values);

    /**
     * Adds a header with the given name and value
     * @see com.yahoo.jdisc.HeaderFields#add
     */
    public abstract void addHeader(String name, String value);

	public abstract String getHeader(String name);

	public List<Cookie> getCookies() {
		return parent.decodeSetCookieHeader();
	}

	public abstract void setCookies(List<Cookie> cookies);

	public int getStatus() {
	    return parent.getStatus();
	}

	public abstract void setStatus(int status);

	/**
	 * Return the parent HttpResponse
     */
	public HttpResponse getParentResponse() {
        if (parent instanceof HttpResponse)
            return (HttpResponse)parent;
        throw new UnsupportedOperationException(
                "getParentResponse is not supported for " + parent.getClass().getName());
	}

    public void addCookie(JDiscCookieWrapper cookie) {
        if(cookie != null) {
            List<Cookie> cookies = new ArrayList<>();
            //Get current set of cookies first
            List<Cookie> c = getCookies();
            if((c != null) && (! c.isEmpty())) {
                cookies.addAll(c);
            }
            cookies.add(cookie.getCookie());
            setCookies(cookies);
        }
    }

    /**
     * This method does not actually send the response as it
     * does not have access to responseHandler but
     * just sets the status. The methodName is misleading
	 * for historical reasons.
     */
    public void sendError(int errorCode) throws IOException {
        setStatus(errorCode);
    }

    public void setCookie(String name, String value) {
        Cookie cookie = new Cookie(name, value);
        setCookies(Arrays.asList(cookie));
    }

 }
