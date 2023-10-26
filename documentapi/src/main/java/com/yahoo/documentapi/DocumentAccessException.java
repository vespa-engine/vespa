// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;

import java.util.Set;
import java.util.HashSet;

/**
 * General exception thrown from various methods in the Vespa Document API.
 *
 * @author Einar M R Rosenvinge
 */
public class DocumentAccessException extends RuntimeException {

    private Set<Integer> errorCodes = new HashSet<>();

    public Set<Integer> getErrorCodes() {
        return errorCodes;
    }

    public DocumentAccessException() {
        super();
    }

    public DocumentAccessException(String message) {
        super(message);
    }

    public DocumentAccessException(String message, Set<Integer> errorCodes) {
        super(message);
        this.errorCodes = errorCodes;
    }

    public boolean hasConditionNotMetError(){
        return errorCodes.contains(DocumentProtocol.ERROR_TEST_AND_SET_CONDITION_FAILED);
    }

    public DocumentAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public DocumentAccessException(Throwable cause) {
        super(cause);
    }
}
