// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.lang;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * An optional which contains a settable value
 *
 * @author bratseth
 */
public final class SettableOptional<T> {

    private T value = null;

    /** Creates a new empty settable optional */
    public SettableOptional() {}

    /** Creates a new settable optional with the given value */
    public SettableOptional(T value) { this.value = value; }

    /** Creates a new settable optional with the given value, or an empty */
    public SettableOptional(Optional<T> value) {
        this.value = value.isPresent() ? value.get() : null;
    }

    public boolean isPresent() {
        return value != null;
    }

    public T get() {
        if (value == null)
            throw new NoSuchElementException("No value present");
        return value;
    }

    public void set(T value) {
        this.value = value;
    }
    
    public Optional<T> asOptional() {
        if (value == null) return Optional.empty();
        return Optional.of(value);
    }

}

