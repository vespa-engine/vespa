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
