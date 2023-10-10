// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container;

/**
 * Wraps an Exception in a RuntimeException, for user convenience.
 *
 * @author gjoranv
 */
class ApplicationException extends RuntimeException {

    ApplicationException(Exception e) {
        super(e);
    }

}
