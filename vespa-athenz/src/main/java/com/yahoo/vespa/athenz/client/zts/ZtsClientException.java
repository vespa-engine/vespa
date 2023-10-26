// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts;

/**
 * An exception that can be thrown by {@link ZtsClient} implementations.
 *
 * @author bjorncs
 */
public class ZtsClientException extends RuntimeException {

    private final int errorCode;
    private final String description;

    public ZtsClientException(int errorCode, String description) {
        super(createMessage(errorCode, description));
        this.errorCode = errorCode;
        this.description = description;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getDescription() {
        return description;
    }

    private static String createMessage(int code, String description) {
        return String.format("Received error from ZTS: code=%d, message=\"%s\"", code, description);
    }

}
