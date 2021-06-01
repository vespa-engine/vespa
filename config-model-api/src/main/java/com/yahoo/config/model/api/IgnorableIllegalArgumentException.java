package com.yahoo.config.model.api;

public class IgnorableIllegalArgumentException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final IgnorableExceptionId id; // unique id for the exception, used to ignore exception on retries

    public IgnorableIllegalArgumentException(String s, IgnorableExceptionId id) {
        super(s);
        this.id = id;
    }

    public IgnorableExceptionId id() {
        return id;
    }

}
