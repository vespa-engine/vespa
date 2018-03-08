// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Ulf Lilleengen
 */
public interface SlimeFormat {

    /**
     * Encode a slime object into the provided output stream
     *
     * @param os The outputstream to write to.
     * @param slime The slime object to encode.
     */
    void encode(OutputStream os, Slime slime) throws IOException;

    /**
     * Encode a slime object into the provided output stream
     *
     * @param is The input stream to read from.
     * @param slime The slime object to decode into.
     */
    void decode(InputStream is, Slime slime) throws IOException;

}
