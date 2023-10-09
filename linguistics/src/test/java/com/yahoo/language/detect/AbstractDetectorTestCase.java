// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.detect;

import com.yahoo.language.Language;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class AbstractDetectorTestCase {

    private static final Detection DETECTION = new Detection(Language.ARABIC, "encoding", true);
    private static final Charset UTF8 = StandardCharsets.UTF_8;

    @Test
    public void requireThatDetectStringForwardsUtf8Bytes() {
        Hint hint = Hint.newCountryHint("no");
        MyDetector detector = new MyDetector();
        Detection detection = detector.detect("69", hint);
        assertSame(DETECTION, detection);
        assertArrayEquals("69".getBytes(UTF8), detector.input);
        assertEquals(0, detector.offset);
        assertEquals(2, detector.length);
        assertSame(hint, detector.hint);
    }

    @Test
    public void requireThatDetectByteBufferForwardsUtf8Bytes() {
        byte[] buf = new byte[] { 6, 9 };
        Hint hint = Hint.newCountryHint("no");
        MyDetector detector = new MyDetector();
        Detection detection = detector.detect(ByteBuffer.wrap(buf), hint);
        assertSame(DETECTION, detection);
        assertArrayEquals(buf, detector.input);
        assertEquals(0, detector.offset);
        assertEquals(2, detector.length);
        assertSame(hint, detector.hint);
    }

    private static class MyDetector extends AbstractDetector {

        byte[] input;
        int offset;
        int length;
        Hint hint;

        @Override
        public Detection detect(byte[] input, int offset, int length, Hint hint) {
            this.input = input;
            this.offset = offset;
            this.length = length;
            this.hint = hint;
            return DETECTION;
        }
    }
}
