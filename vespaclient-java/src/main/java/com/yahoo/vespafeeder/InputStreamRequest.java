// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespafeeder;

import com.yahoo.container.jdisc.HttpRequest;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * This is needed because whoever wrote this library moronically decided to pass in-process communication through
 * the HTTP layer. As the feeded is being phased out in favor of the standalone HTTP client we don't bother to clean
 * it up properly.
 *
 * @author bratseth
 */
public class InputStreamRequest {

    private InputStream input;
    private Map<String, String> properties = new HashMap<>();

    protected InputStreamRequest(InputStream input) {
        this.input = input;
    }

    public void setProperty(String key, String value) {
        properties.put(key, value);
    }

    public String getProperty(String key) {
        return properties.get(key);
    }

    public HttpRequest toRequest() {
        return HttpRequest.createTestRequest("", com.yahoo.jdisc.http.HttpRequest.Method.POST, input, properties);
    }

}
