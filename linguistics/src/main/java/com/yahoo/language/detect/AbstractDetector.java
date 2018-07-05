// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.detect;

import com.yahoo.text.Utf8;

import java.nio.ByteBuffer;

/**
 * @author Simon Thoresen Hult
 */
public abstract class AbstractDetector implements Detector {

    @Override
    public final Detection detect(String input, Hint hint) {
        byte[] buf = Utf8.toBytes(input);
        return detect(buf, 0, buf.length, hint);
    }

    @Override
    public final Detection detect(ByteBuffer input, Hint hint) {
        byte[] buf = new byte[input.remaining()];
        input.get(buf, 0, buf.length);
        return detect(buf, 0, buf.length, hint);
    }

}
