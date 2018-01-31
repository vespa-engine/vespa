// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.container.handler.Coverage;
import com.yahoo.container.handler.Timing;
import com.yahoo.container.logging.HitCounts;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An HTTP response supporting async rendering and extended information for logging.
 *
 * @author Steinar Knutsen
 */
public abstract class ExtendedResponse extends AsyncHttpResponse {

    public ExtendedResponse(int status) {
        super(status);
    }

    @Override
    public abstract void render(OutputStream output, ContentChannel networkChannel, CompletionHandler handler)
        throws IOException;

    /**
     * @return user name performing the request
     */
    public String getUser() {
        return null;
    }

    /**
     * The parsed query or some other normal form for the query/request
     * resulting in this Response. Never null. This default implementation
     * returns null though.
     */
    public String getParsedQuery() {
        return null;
    }

    /**
     * Returns timing information about the processing leading to this response.
     * This default implementation returns null.
     *
     * @see com.yahoo.container.handler.Timing
     * @return a Timing instance or null
     */
    public Timing getTiming() {
        return null;
    }

    /**
     * Returns the completeness of the scan of the total known data for this
     * response. This default implementation returns null.
     *
     * @see Coverage
     * @return coverage information or null
     */
    public Coverage getCoverage() {
        return null;
    }

    /**
     * Returns the number of "hits" in this. This default implementation returns
     * null.
     *
     * @return a Counts instance or null
     */
    public HitCounts getHitCounts() {
        return null;
    }

}
