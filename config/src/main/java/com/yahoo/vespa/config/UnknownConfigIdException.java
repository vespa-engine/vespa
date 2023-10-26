// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

/**
 * Used when a config model does not recognize a config id
 * @author vegardh
 *
 */
@SuppressWarnings("serial")
public class UnknownConfigIdException extends IllegalArgumentException {

    public UnknownConfigIdException(String msg) {
        super(msg);
    }

}
