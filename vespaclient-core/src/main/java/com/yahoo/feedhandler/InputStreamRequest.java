// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedhandler;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple wrapper of a stream and some properties.
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
