// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import java.nio.Buffer;
import java.nio.charset.Charset;
import java.nio.ByteBuffer;


/**
 * Conventient utility for reading lines from ByteBuffers.  Please
 * read the method documentation for readLine() carefully.  The NIO
 * ByteBuffer abstraction is somewhat clumsy and thus usage of this
 * code requires that you understand the semantics clearly.
 *
 * @author <a href="mailto:borud@yahoo-inc.com">Bjorn Borud</a>
 *
 */
public class ReadLine {
    static private Charset charset = Charset.forName("latin1");

    /**
     * Extract next line from a byte buffer.  Looks for EOL characters
     * between start and limit, and returns a string between start and
     * the EOL charachers.  It skips ahead past any remaining EOL
     * characters and sets position to the first non-EOL character.
     *
     * If it doesn't find an EOL characher between start and limit
     */
    public static String readLine(ByteBuffer buffer) {
        int start = buffer.position();

        for (int i = start; i < buffer.limit(); i++) {

            if (isEolChar(buffer.get(i))) {

                // detect and skip EOL at beginning.  Also, update
                // position so we compact the buffer if we exit the
                // for loop without having found a proper string
                if (i == start) {
                    for (; (i < buffer.limit()) && isEolChar(buffer.get(i)); i++) {
                        ;
                    }
                    start = i;
                    buffer.position(i);
                    continue;
                }

                // limit() returns a buffer (before Java 9) so we have to up-cast.
                // The downcast to Buffer is done to avoid "redundant cast" warning on Java 9.
                // TODO: when Java 8 is gone, remove the casts and above comments.
                // extract string between start and i.
                String line = charset.decode((ByteBuffer) ((Buffer)buffer.slice()).limit(i - start)).toString();

                // skip remaining
                for (; (i < buffer.limit()) && isEolChar(buffer.get(i)); i++) {
                    ;
                }

                buffer.position(i);
                return line;
            }
        }

        // if we get here we didn't find any string. this may be
        // because the buffer has no more content, ie. limit == position.
        // if that is the case we clear the buffer.
        //
        // if we have content, but no more EOL characters we compact the
        // buffer.
        //
        if (buffer.hasRemaining()) {
            buffer.compact();
        } else {
            buffer.clear();
        }

        return null;
    }

    static boolean isEolChar(byte b) {
        return ((10 == b) || (13 == b));
    }
}
