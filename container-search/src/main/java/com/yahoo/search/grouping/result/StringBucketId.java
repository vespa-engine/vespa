// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

/**
 * This class is used in {@link Group} instances where the identifying expression evaluated to a {@link
 * com.yahoo.search.grouping.request.StringBucket}.
 *
 * @author Simon Thoresen Hult
 */
public class StringBucketId extends BucketGroupId<String> {

    /**
     * Constructs a new instance of this class.
     *
     * @param from The identifying inclusive-from string.
     * @param to   The identifying exclusive-to string.
     */
    public StringBucketId(String from, String to) {
        super("string_bucket", from, to);
    }

}
