// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.systemflags;

import java.util.OptionalInt;

/**
 * @author bjorncs
 */
class FlagsClientException extends RuntimeException {

    private final int responseCode;

    FlagsClientException(int responseCode, String message) {
        super(message);
        this.responseCode = responseCode;
    }

    FlagsClientException(String message, Throwable cause) {
        super(message, cause);
        this.responseCode = -1;
    }

    OptionalInt responseCode() {
        return responseCode > 0 ? OptionalInt.of(responseCode) : OptionalInt.empty();
    }
}
