// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

/**
 * This class is used in {@link Group} instances where the identifying expression evaluated to a {@link String}.
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
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
