// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

/**
 * Thrown when a resource is not found
 *
 * @author bratseth
 */
class NotFoundException extends RuntimeException {

    public NotFoundException(String message) { super(message); }

    public NotFoundException(String message, Throwable cause) { super(message, cause); }

}
