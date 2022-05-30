// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

/**
 * @author hakonhall
 */
public class StringFlag extends FlagImpl<String, StringFlag> {
    public StringFlag(FlagId id, String defaultValue, FetchVector vector, FlagSerializer<String> serializer, FlagSource source) {
        super(id, defaultValue, vector, serializer, source, StringFlag::new);
    }

    @Override
    public StringFlag self() {
        return this;
    }

    public String value() {
        return boxedValue();
    }
}
