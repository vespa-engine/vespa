// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Marker interface for use with the BufferChain data store.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public interface WritableByteTransmitter {
    void send(ByteBuffer src) throws IOException;
}
