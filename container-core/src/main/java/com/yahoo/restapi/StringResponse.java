// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import java.nio.charset.StandardCharsets;

/**
 * @author bratseth
 */
public class StringResponse extends ByteArrayResponse {
    public StringResponse(String message) {
        super(message.getBytes(StandardCharsets.UTF_8));
    }
}
