// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

import com.yahoo.prelude.hitfield.RawBase64;

/**
 * This class is used in {@link Group} instances where the identifying
 * expression evaluated to a {@link com.yahoo.search.grouping.request.RawBucket}.
 *
 * @author Ulf Lilleengen
 */
public class RawBucketId extends BucketGroupId<RawBase64> {

    /**
     * Constructs a new instance of this class.
     *
     * @param from The identifying inclusive-from raw buffer.
     * @param to   The identifying exclusive-to raw buffer.
     */
    public RawBucketId(byte[] from, byte[] to) {
        super("raw_bucket", new RawBase64(from), new RawBase64(to));
    }
}
