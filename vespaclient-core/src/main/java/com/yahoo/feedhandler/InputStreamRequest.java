// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedhandler;

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

    private final InputStream input;
    private final Map<String, String> properties = new HashMap<>();

    public InputStreamRequest(InputStream input) {
        this.input = input;
    }

    public void setProperty(String key, String value) {
        properties.put(key, value);
    }

    public String getProperty(String key) {
        return properties.get(key);
    }

    InputStream getData() { return input; }

}
