// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.serialization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * @author bjorncs
 */
public class SerializationTestHelper {

    private SerializationTestHelper() {}

    public static <T> void assertSerializationDeserializationMatches
            (T object, Serializer<T> serializer, Deserializer<T> deserializer) throws IOException {

        ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream(4096);
        DataOutputStream out = new DataOutputStream(byteArrayOut);
        serializer.serialize(object, out);
        out.flush();

        byte[] bytes = byteArrayOut.toByteArray();
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        T newObject = deserializer.deserialize(in);

        byteArrayOut = new ByteArrayOutputStream(4096);
        out = new DataOutputStream(byteArrayOut);
        serializer.serialize(newObject, out);
        byte[] newBytes = byteArrayOut.toByteArray();
        assertArrayEquals(bytes, newBytes);
    }

    @FunctionalInterface
    public interface Serializer<T> {
        void serialize(T object, DataOutputStream out) throws IOException;
    }

    @FunctionalInterface
    public interface Deserializer<T> {
        T deserialize(DataInputStream in) throws IOException;
    }

}
