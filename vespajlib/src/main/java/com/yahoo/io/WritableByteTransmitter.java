// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Marker interface for use with the BufferChain data store.
 *
 * @author Steinar Knutsen
 */
public interface WritableByteTransmitter {
    void send(ByteBuffer src) throws IOException;
}
