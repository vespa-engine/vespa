// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.jdisc.handler.CompletionHandler;

/**
 * A completion handler which does access logging.
 *
 * @see ThreadedHttpRequestHandler#createLoggingCompletionHandler(long, long, HttpResponse, HttpRequest, ContentChannelOutputStream)
 * @author Steinar Knutsen
 */
public interface LoggingCompletionHandler extends CompletionHandler {

    /**
     * Set the commit start time to the current time. Commit start is only well
     * defined for synchronous renderers, it is the point in time when rendering
     * has finished, but there may still be I/O operations to transfer the data
     * to the client pending.
     */
    void markCommitStart();

}
