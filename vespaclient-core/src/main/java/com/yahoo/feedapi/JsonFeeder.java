// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedapi;

import java.io.InputStream;

import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.json.JsonFeedReader;
import com.yahoo.vespaxmlparser.FeedReader;

/**
 * Unpack JSON document operations and push to a feed access point.
 *
 * @author steinar
 */
public class JsonFeeder extends Feeder {
    public JsonFeeder(DocumentTypeManager docMan, SimpleFeedAccess sender, InputStream stream) {
        super(docMan, new VespaFeedSender(sender), stream);
    }

    @Override
    protected FeedReader createReader() throws Exception {
        return new JsonFeedReader(stream, docMan);
    }
}
