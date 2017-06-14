// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

/**
 * This class is used in {@link Group} instances where the identifying expression evaluated to a {@link Long}.
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class LongId extends ValueGroupId<Long> {

    /**
     * Constructs a new instance of this class.
     *
     * @param value The identifying long.
     */
    public LongId(Long value) {
        super("long", value);
    }
}
