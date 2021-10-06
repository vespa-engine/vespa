// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.protect;

/**
 * Error codes to use in ErrorMessage instances for container applications.
 *
 * @author Steinar Knutsen
 */
public enum Error {

    NO_BACKENDS_IN_SERVICE(0),
    NULL_QUERY(1),
    REQUEST_TOO_LARGE(2),
    ILLEGAL_QUERY(3),
    INVALID_QUERY_PARAMETER(4),
    UNSPECIFIED(5),
    ERROR_IN_PLUGIN(6),
    INVALID_QUERY_TRANSFORMATION(7),
    RESULT_HAS_ERRORS(8),
    SERVER_IS_MISCONFIGURED(9),
    BACKEND_COMMUNICATION_ERROR(10),
    NO_ANSWER_WHEN_PINGING_NODE(11),
    TIMEOUT(12),
    EMPTY_DOCUMENTS(13),
    UNAUTHORIZED(14),
    FORBIDDEN(15),
    NOT_FOUND(16),
    BAD_REQUEST(17),
    INTERNAL_SERVER_ERROR(18),
    INSUFFICIENT_STORAGE(19);

    public final int code;

    Error(int code) {
        this.code = code;
    }

}
