// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

/**
 * This class is used in {@link Group} instances where the identifying expression evaluated to a {@link String}.
 *
 * @author Simon Thoresen Hult
 */
public class StringId extends ValueGroupId<String> {

    /**
     * Constructs a new instance of this class.
     *
     * @param value The identifying string.
     */
    public StringId(String value) {
        super("string", value);
    }
}
