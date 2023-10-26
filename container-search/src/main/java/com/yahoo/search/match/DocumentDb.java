// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.match;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentOperation;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;

/**
 * A searchable database of documents
 *
 * @author bratseth
 */
public class DocumentDb extends Searcher {

    /**
     * Put a document or apply an update to this document db
     */
    public void put(DocumentOperation op) {

    }

    /** Remove a document from this document db */
    public void remove(Document document) {

    }

    /** Search this document db */
    @Override
    public Result search(Query query, Execution execution) {
        Result r = execution.search(query);
        return r;
    }

}
