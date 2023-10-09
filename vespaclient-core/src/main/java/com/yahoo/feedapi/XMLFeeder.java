// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedapi;

import com.yahoo.document.DocumentTypeManager;
import com.yahoo.vespaxmlparser.FeedReader;
import com.yahoo.vespaxmlparser.VespaXMLFeedReader;

import java.io.InputStream;

/**
 * Unpack a stream of document operations represented as XML and push to a feed
 * access point.
 *
 * @author Thomas Gundersen
 * @author Steinar Knutsen
 */
public class XMLFeeder extends Feeder {
    public XMLFeeder(DocumentTypeManager docMan, SimpleFeedAccess sender, InputStream stream) {
        super(docMan, new VespaFeedSender(sender), stream);
    }

    @Override
    protected FeedReader createReader() throws Exception {
        return new VespaXMLFeedReader(stream, docMan);
    }
}
