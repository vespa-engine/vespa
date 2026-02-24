// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * Class for deserializing CBOR (RFC 8949) encoded data into a Slime object.
 */
public class CborFormat {

    /**
     * Take CBOR data and deserialize it into a Slime object.
     *
     * If the CBOR data can't be deserialized without problems
     * the returned Slime object will instead only contain the
     * three fields "partial_result" (contains anything successfully
     * decoded before encountering problems), "offending_input"
     * (containing any data that could not be deserialized) and
     * "error_message" (a string describing the problem encountered).
     *
     * @param data the data to be deserialized.
     * @return a new Slime object constructed from the data.
     */
    public static Slime decode(byte[] data) {
        return new CborDecoder().decode(data);
    }

    /**
     * Take CBOR data and deserialize it into a Slime object.
     *
     * If the CBOR data can't be deserialized without problems
     * the returned Slime object will instead only contain the
     * three fields "partial_result" (contains anything successfully
     * decoded before encountering problems), "offending_input"
     * (containing any data that could not be deserialized) and
     * "error_message" (a string describing the problem encountered).
     *
     * @param data array containing the data to be deserialized.
     * @param offset where in the array to start deserializing.
     * @param length how many bytes the deserializer is allowed to consume.
     * @return a new Slime object constructed from the data.
     */
    public static Slime decode(byte[] data, int offset, int length) {
        return new CborDecoder().decode(data, offset, length);
    }
}
