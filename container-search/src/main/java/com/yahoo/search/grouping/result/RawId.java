// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

import com.yahoo.prelude.hitfield.RawBase64;

/**
 * This class is used in {@link Group} instances where the identifying expression evaluated to a {@link Byte} array.
 *
 * @author Simon Thoresen Hult
 */
public class RawId extends ValueGroupId<RawBase64> {

    /**
     * Constructs a new instance of this class.
     *
     * @param value The identifying byte array.
     */
    public RawId(byte[] value) {
        super("raw", new RawBase64(value));
    }
}
