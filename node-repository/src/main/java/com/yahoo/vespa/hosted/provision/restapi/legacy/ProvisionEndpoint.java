// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.legacy;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * To avoid duplication of URI construction.
 * This class should be deleted when there's a provision client configured in services xml.
 * @author tonytv
 */
public class ProvisionEndpoint {

    public static final int configServerPort = 19071;

    public static URI provisionUri(String configServerHostName, int port) {
        try {
            return new URL("http", configServerHostName, port, "/hack/provision").toURI();
        } catch (URISyntaxException | MalformedURLException e) {
            throw new IllegalArgumentException("Failed creating provisionUri from " + configServerHostName, e);
        }
    }
}
