// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.config;

/**
 * @author gjoranv
 */
public class ResolveDependencyException extends RuntimeException {

    public ResolveDependencyException(String message) {
        super(message);
    }

}
