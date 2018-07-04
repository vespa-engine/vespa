// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

/**
 * This class is used in {@link Group} instances where the identifying expression evaluated to a {@link
 * com.yahoo.search.grouping.request.DoubleBucket}.
 *
 * @author Simon Thoresen Hult
 */
public class DoubleBucketId extends BucketGroupId<Double> {

    /**
     * Constructs a new instance of this class.
     *
     * @param from The identifying inclusive-from double.
     * @param to   The identifying exclusive-to double.
     */
    public DoubleBucketId(Double from, Double to) {
        super("double_bucket", from, to);
    }
}
