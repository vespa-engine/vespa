// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;


import java.util.zip.Inflater;


/**
 * @author <a href="mailto:borud@yahoo-inc.com">Bjorn Borud</a>
 */
public class SlowInflate {
    private Inflater inflater = new Inflater();

    public byte[] unpack(byte[] compressed, int inflatedLen) {
        byte[] decompressed = new byte[inflatedLen];

        inflater.reset();
        inflater.setInput(compressed);
        inflater.finished();
        try {
            inflater.inflate(decompressed);
        } catch (java.util.zip.DataFormatException e) {
            throw new RuntimeException("Decompression failure: " + e);
        }
        return decompressed;
    }
}
