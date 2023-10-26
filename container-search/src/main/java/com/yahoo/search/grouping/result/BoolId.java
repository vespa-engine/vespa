// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

/**
 * This class is used in {@link Group} instances where the identifying expression evaluated to a {@link Boolean}.
 *
 * @author baldersheim
 */
public class BoolId extends ValueGroupId<Boolean> {
    public BoolId(Boolean value) {
        super("bool", value);
    }
}
