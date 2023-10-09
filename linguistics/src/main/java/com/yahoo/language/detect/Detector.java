// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.detect;

import java.nio.ByteBuffer;

/**
 * Abstract superclass of all Detectors used for language and encoding detection.
 *
 * @author Einar M R Rosenvinge
 */
public interface Detector {

    /**
     * Detects language and encoding of the supplied byte array, possibly using a language/encoding hint.
     *
     * @param input  the buffer that is to be inspected
     * @param offset the offset to detect from
     * @param length the size to detect from
     * @param hint   a hint to the detector, or null for no hint
     * @return an array of possible language/encoding pairs, sorted by decreasing confidence (possibly empty, but never null)
     * @throws DetectionException if detection fails
     */
    Detection detect(byte[] input, int offset, int length, Hint hint);

    /**
     * Detects language and encoding of the supplied ByteBuffer, possibly using a language/encoding hint.
     *
     * @param input the buffer that is to be inspected, from its current position to its limit
     * @param hint  a hint to the detector, or null for no hint
     * @return an array of possible language/encoding pairs, sorted by decreasing confidence (possibly empty, but never null)
     * @throws DetectionException if detection fails
     */
    Detection detect(ByteBuffer input, Hint hint);

    /**
     * Detects language of the supplied String, possibly using a language hint.
     *
     * @param input the string that is to be inspected
     * @param hint  a hint to the detector, or null for no hint
     * @return an array of possible language/encoding pairs, sorted by decreasing confidence (possibly empty, but never null)
     * @throws DetectionException if detection fails
     */
    Detection detect(String input, Hint hint);

}
