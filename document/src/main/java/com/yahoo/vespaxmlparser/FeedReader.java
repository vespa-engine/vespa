// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaxmlparser;

/**
 * Minimal interface for reading operations from a stream for a feeder.
 *
 * Interface extracted from VespaXMLFeedReader to enable JSON feeding.
 *
 * @author steinar
 */
public interface FeedReader {

    /**
     * Reads the next operation from the stream.
     * @return  operation, possibly invalid if none was found.
     */
    FeedOperation read() throws Exception;
}
