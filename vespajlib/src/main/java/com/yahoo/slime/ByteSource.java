// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import java.io.IOException;

/**
 * A source of successive byte arrays, used by {@link BufferedInput} to consume
 * input incrementally without buffering everything into a single array first.
 */
@FunctionalInterface
interface ByteSource {

    /** Returns the next chunk of bytes, or null when there are no more. */
    byte[] next() throws IOException;

}
