// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import com.yahoo.tensor.serialization.DataDisclosure;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Ulf Lilleengen
 */
public interface SlimeFormat {

    /**
     * Encode a slime object into the provided output stream
     *
     * @param os             The outputstream to write to.
     * @param slime          The slime object to encode.

     */
    void encode(OutputStream os, Slime slime) throws IOException;

    /**
     *
     * @param os
     * @param slime
     * @param dataDisclosure
     * @throws IOException
     */
    void encode(OutputStream os, Slime slime, DataDisclosure dataDisclosure) throws IOException;

}
