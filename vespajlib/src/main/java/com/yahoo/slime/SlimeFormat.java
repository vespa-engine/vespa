// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
     * Decode an input stream into the provided slime object
     *
     * @param is The input stream to read from.
     * @param slime The slime object to decode into.
     * @deprecated use e.g. {@link JsonDecoder} instead
     */
    @Deprecated(since = "7", forRemoval = true)
    void decode(InputStream is, Slime slime) throws IOException;

}
