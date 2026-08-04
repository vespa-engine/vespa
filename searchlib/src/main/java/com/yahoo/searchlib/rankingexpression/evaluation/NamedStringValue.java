// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import java.util.Objects;

/**
 * A named string value, serialized as a single {@code "name:value"} string.
 */
public final class NamedStringValue extends StringValue {

    private final String name;
    private final String value;

    public NamedStringValue(String name, String value) {
        super(Objects.requireNonNull(name) + ":" + Objects.requireNonNull(value));
        this.name = name;
        this.value = value;
    }

    public String name() { return name; }

    public String value() { return value; }

    @Override
    public NamedStringValue asMutable() {
        if ( ! isFrozen()) return this;
        return new NamedStringValue(name, value);
    }

}
