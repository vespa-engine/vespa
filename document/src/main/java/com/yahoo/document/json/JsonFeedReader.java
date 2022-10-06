// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.vespaxmlparser.DocumentFeedOperation;
import com.yahoo.vespaxmlparser.DocumentUpdateFeedOperation;
import com.yahoo.vespaxmlparser.FeedOperation;
import com.yahoo.vespaxmlparser.FeedReader;
import com.yahoo.vespaxmlparser.RemoveFeedOperation;

import java.io.InputStream;

/**
 * Facade between JsonReader and the FeedReader API.
 *
 * The feed reader will take ownership of the input stream and close it when the
 * last parseable document has been read.
 *
 * @author Steinar Knutsen
 */
public class JsonFeedReader implements FeedReader {

    private final JsonReader reader;
    private final InputStream stream;
    private static final JsonFactory jsonFactory = new JsonFactoryBuilder().disable(JsonFactory.Feature.CANONICALIZE_FIELD_NAMES).build();

    public JsonFeedReader(InputStream stream, DocumentTypeManager docMan) {
        reader = new JsonReader(docMan, stream, jsonFactory);
        this.stream = stream;
    }

    @Override
    public FeedOperation read() throws Exception {
        DocumentOperation documentOperation = reader.next();

        if (documentOperation == null) {
            stream.close();
            return FeedOperation.INVALID;
        }

        if (documentOperation instanceof DocumentUpdate) {
            return new DocumentUpdateFeedOperation((DocumentUpdate) documentOperation, documentOperation.getCondition());
        } else if (documentOperation instanceof DocumentRemove) {
            return new RemoveFeedOperation(documentOperation.getId(), documentOperation.getCondition());
        } else if (documentOperation instanceof DocumentPut) {
            return new DocumentFeedOperation(((DocumentPut) documentOperation).getDocument(), documentOperation.getCondition());
        } else {
            throw new IllegalArgumentException("Got unknown class from JSON reader: " + documentOperation.getClass().getName());
        }
    }

}
