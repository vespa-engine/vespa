package com.yahoo.config.model.api;

import java.util.Objects;

/**
 * Id used for retrying an operation that caused an {@link IgnorableIllegalArgumentException} as a signal to the
 * caller that the original exception thrown with this id can be ignored (i.e. the
 * operation should succeed).
 *
 * @author hmusum
 */
public class IgnorableExceptionId {

    private final String id; // unique id for the exception, used to ignore exception on retries

    public IgnorableExceptionId(String id) {
        this.id = id;
    }

    /* No argument can be ignored if it is illegal */
    public static IgnorableExceptionId none() {
        return new IgnorableExceptionId("__none__");
    }

    public String id() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof IgnorableExceptionId)) return false;
        IgnorableExceptionId other = (IgnorableExceptionId)o;
        return this.id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

}
