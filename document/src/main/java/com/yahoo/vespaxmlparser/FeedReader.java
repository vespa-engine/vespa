// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaxmlparser;

import com.yahoo.vespaxmlparser.VespaXMLFeedReader.Operation;

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
     * @param operation The operation to fill in. Operation is unchanged if none was found.
     */
    void read(Operation operation) throws Exception;

}
