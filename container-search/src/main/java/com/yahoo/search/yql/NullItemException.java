// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

/**
 * Used to communicate a NullItem has been encountered in the query tree.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
@SuppressWarnings("serial")
public class NullItemException extends RuntimeException {
    public NullItemException(String message) {
        super(message);
    }
}
