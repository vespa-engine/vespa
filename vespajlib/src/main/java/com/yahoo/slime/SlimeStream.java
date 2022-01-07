// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Simple utility to bridge Slime and Streams.
 *
 * @author ogronnesby
 */
public final class SlimeStream {

    private SlimeStream() {}

    /**
     * Create a stream from a Slime {@link Inspector} pointing to an array.
     * @param array the array inspector
     * @param mapper the function mapping to Stream elements
     * @return A Stream of array elements
     */
    public static <T> Stream<T> fromArray(Inspector array, Function<Inspector, T> mapper) {
        return IntStream.range(0, array.entries())
                .mapToObj(array::entry)
                .map(mapper);
    }

}
