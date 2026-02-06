// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * A Uri which provides convenience methods for creating various manipulated copies.
 * This is immutable.
 * 
 * @author bratseth
 */
public class UriBuilder {

    /** The URI instance wrapped by this */
    private final URI uri;
    
    public UriBuilder(URI uri) {
        this.uri = uri;
    }

    public UriBuilder(String uri) {
        try {
            this.uri = new URI(uri);
        }
        catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI", e);
        }
    }

    /** Returns a uri with the given path appended and all parameters removed */
    public UriBuilder append(String pathElement) {
        return new UriBuilder(withoutParameters().withTrailingSlash() + pathElement);
    }
    
    public UriBuilder withoutParameters() {
        int parameterStart = uri.toString().indexOf("?");
        if (parameterStart < 0)
            return new UriBuilder(uri.toString());
        else
            return new UriBuilder(uri.toString().substring(0, parameterStart));
    }

    public UriBuilder withPath(String path) {
        try {
            return new UriBuilder(new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(),
                                   uri.getPort(), path, uri.getQuery(), uri.getFragment()));
        }
        catch (URISyntaxException e) {
            throw new IllegalArgumentException("Could not add path '" + path + "' to " + this);
        }
    }

    public UriBuilder withTrailingSlash() {
        if (toString().endsWith("/")) return this;
        return new UriBuilder(toString() + "/");
    }

    public URI toURI() { return uri; }

    @Override
    public String toString() { return uri.toString(); }

}
