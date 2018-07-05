// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

/**
 * This class is used in {@link Group} instances where the identifying expression evaluated to a {@link
 * com.yahoo.search.grouping.request.LongBucket}.
 *
 * @author Simon Thoresen Hult
 */
public class LongBucketId extends BucketGroupId<Long> {

    /**
     * Constructs a new instance of this class.
     *
     * @param from The identifying inclusive-from long.
     * @param to   The identifying exclusive-to long.
     */
    public LongBucketId(Long from, Long to) {
        super("long_bucket", from, to);
    }
}
