// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

/**
 * Runtime exception to mark errors in query parsing.
 *
 * @author Steinar Knutsen
 * @deprecated no methods throw this
 */
@Deprecated // TODO: Remove on Vespa 8
public class QueryException extends RuntimeException {

    public QueryException(String message) {
        super(message);
    }

    public QueryException(String message, Throwable cause) {
        super(message, cause);
    }

}
