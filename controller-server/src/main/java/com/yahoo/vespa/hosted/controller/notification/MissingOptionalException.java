// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.notification;

/**
 * Used to signal that an expected value was not present when creating NotificationContent
 *
 * @author enygaard
 */
class MissingOptionalException extends RuntimeException {
    private final String field;
    public MissingOptionalException(String field) {
        super(field + " was expected but not present");
        this.field = field;
    }

    public String field() {
        return field;
    }
}
