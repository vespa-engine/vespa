// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.handlers.replicator;

import java.util.Map;
import java.util.IdentityHashMap;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import com.yahoo.log.LogMessage;
import com.yahoo.logserver.formatter.LogFormatter;
import com.yahoo.logserver.formatter.LogFormatterManager;

/**
 * This class is used to cache log messages that have been
 * formatted into ByteBuffers.  The purpose of this class
 * is to make it easier to support multiple message formats while
 * still ensuring we don't format more messages than we strictly need
 * to and that we don't keep around more buffers that we ought to.
 * <p>
 * This is not a general purpose class, I think, so please
 * refer to the source code of the Replicator class for
 * information on how to use this.
 * <p>
 * This class is not threadsafe.
 *
 * @author Bjorn Borud
 */
public class FormattedBufferCache {
    // the documentation says " All of the methods defined in this
    // class are safe for use by multiple concurrent threads." so
    // we have only one instance of the charset for this class.
    //
    static private final Charset charset = Charset.forName("utf-8");

    private final IdentityHashMap<LogFormatter, ByteBuffer> buffers;

    public FormattedBufferCache() {
        // hope this is a good hash size
        int initialSize = LogFormatterManager.getFormatterNames().length * 2;
        buffers = new IdentityHashMap<LogFormatter, ByteBuffer>(initialSize);
    }

    /**
     * Return a ByteBuffer slice of a buffer containing the
     * LogMessage formatted by the LogFormatter.  If one didn't
     * exist in the cache from before, it will after this
     * method returns.
     *
     * @param msg       The log message you wish to format
     * @param formatter The log formatter you wish to use for formatting
     * @return Returns a ByteBuffer slice
     */
    public ByteBuffer getFormatted(LogMessage msg, LogFormatter formatter) {
        ByteBuffer bb = buffers.get(formatter);
        if (bb == null) {
            bb = charset.encode(formatter.format(msg));
            buffers.put(formatter, bb);
        }
        return bb.slice();
    }

    /**
     * After we're done distributing the log message to all the
     * clients we clear the cache so we are ready for the next
     * message.
     */
    public void reset() {
        buffers.clear();
    }

    /**
     * This is here for test purposes.  Don't get any bright ideas.
     */
    public Map<LogFormatter, ByteBuffer> getUnderlyingMapOnlyForTesting() {
        return buffers;
    }
}
