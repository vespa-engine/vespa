// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.serialization;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.yahoo.search.predicate.serialization.SerializationTestHelper.*;

/**
 * @author bjorncs
 */
public class SerializationHelperTest {

    @Test
    void require_that_long_serialization_works() throws IOException {
        long[] longs = {1, 2, 3, 4};
        assertSerializationDeserializationMatches(
                longs, SerializationHelper::writeLongArray, SerializationHelper::readLongArray);
    }

    @Test
    void require_that_int_serialization_works() throws IOException {
        int[] ints = {1, 2, 3, 4};
        assertSerializationDeserializationMatches(
                ints, SerializationHelper::writeIntArray, SerializationHelper::readIntArray);
    }

    @Test
    void require_that_byte_serialization_works() throws IOException {
        byte[] bytes = {1, 2, 3, 4};
        assertSerializationDeserializationMatches(
                bytes, SerializationHelper::writeByteArray, SerializationHelper::readByteArray);
    }

    @Test
    void require_that_short_serialization_works() throws IOException {
        short[] shorts = {1, 2, 3, 4};
        assertSerializationDeserializationMatches(
                shorts, SerializationHelper::writeShortArray, SerializationHelper::readShortArray);
    }


}
