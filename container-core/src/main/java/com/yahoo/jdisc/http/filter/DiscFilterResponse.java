// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.jdisc.HeaderFields;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.Cookie;
import com.yahoo.jdisc.http.CookieHelper;
import com.yahoo.jdisc.http.HttpResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Response type for {@link SecurityResponseFilter}.
 *
 * @author Tejal Knot
 * @author bjorncs
 */
public class DiscFilterResponse {

	private final Response parent;
	private final HeaderFields untreatedHeaders;
	private final List<Cookie> untreatedCookies;

	public DiscFilterResponse(HttpResponse parent) { this((Response)parent); }

	DiscFilterResponse(Response parent) {
		this.parent = parent;

        this.untreatedHeaders = new HeaderFields();
		untreatedHeaders.addAll(parent.headers());

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
	public void setHeader(String name, String value) {
		parent.headers().put(name, value);
	}

	public void removeHeaders(String name) {
		parent.headers().remove(name);
	}

	/**
     * Sets a header with the given name and value.
     * <p>
     * If the header had already been set, the new value overwrites the previous one.
     */
	public void setHeaders(String name, String value) {
		parent.headers().put(name, value);
	}

	/**
     * Sets a header with the given name and value.
     * <p>
     * If the header had already been set, the new value overwrites the previous one.
     */
	public void setHeaders(String name, List<String> values) {
		parent.headers().put(name, values);
	}

    /**
     * Adds a header with the given name and value
     * @see com.yahoo.jdisc.HeaderFields#add
     */
    public void addHeader(String name, String value) {
		parent.headers().add(name, value);
	}

	public String getHeader(String name) {
		List<String> values = parent.headers().get(name);
		if (values == null || values.isEmpty()) {
			return null;
		}
		return values.get(values.size() - 1);
	}

	public List<Cookie> getCookies() {
		return CookieHelper.decodeSetCookieHeader(parent.headers());
	}

	public void setCookies(List<Cookie> cookies) {
		CookieHelper.encodeSetCookieHeader(parent.headers(), cookies);
	}

	public int getStatus() {
	    return parent.getStatus();
	}

	public void setStatus(int status) {
		parent.setStatus(status);
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
