// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

import java.util.Arrays;

/**
 * This class is used in {@link Group} instances where the identifying expression evaluated to a {@link Byte} array.
 *
 * @author Simon Thoresen Hult
 */
public class RawId extends ValueGroupId<byte[]> {

    /**
     * Constructs a new instance of this class.
     *
     * @param value The identifying byte array.
     */
    public RawId(byte[] value) {
        super("raw", value, Arrays.toString(value));
    }
}
