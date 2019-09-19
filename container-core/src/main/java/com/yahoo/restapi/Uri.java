// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * A Uri which provides convenience methods for creating various manipulated copies.
 * This is immutable.
 * 
 * @author bratseth
 */
public class Uri {

    /** The URI instance wrapped by this */
    private final URI uri;
    
    public Uri(URI uri) {
        this.uri = uri;
    }

    public Uri(String uri) {
        try {
            this.uri = new URI(uri);
        }
        catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI", e);
        }
    }

    /** Returns a uri with the given path appended and all parameters removed */
    public Uri append(String pathElement) {
        return new Uri(withoutParameters().withTrailingSlash() + pathElement);
    }
    
    public Uri withoutParameters() {
        int parameterStart = uri.toString().indexOf("?");
        if (parameterStart < 0)
            return new Uri(uri.toString());
        else
            return new Uri(uri.toString().substring(0, parameterStart));
    }

    public Uri withPath(String path) {
        try {
            return new Uri(new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(),
                                   uri.getPort(), path, uri.getQuery(), uri.getFragment()));
        }
        catch (URISyntaxException e) {
            throw new IllegalArgumentException("Could not add path '" + path + "' to " + this);
        }
    }

    public Uri withTrailingSlash() {
        if (toString().endsWith("/")) return this;
        return new Uri(toString() + "/");
    }

    public URI toURI() { return uri; }

    @Override
    public String toString() { return uri.toString(); }

}
