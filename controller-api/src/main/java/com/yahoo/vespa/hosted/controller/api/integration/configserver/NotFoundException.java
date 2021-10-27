// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

/**
 * @author Tony Vaagenes
 */
public class NotFoundException extends Exception {
    public NotFoundException(String msg) {
        super(msg);
    }
}
