// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib;

import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentUpdate;

/**
 * Represents an in-memory entry.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
class DynamicEntry extends Entry {
    private DocumentOperation op;
    private boolean bodyStripped;

    DynamicEntry(DocumentOperation op, boolean bodyStripped) {
        this.op = op;
        this.bodyStripped = bodyStripped;
    }

    DynamicEntry(DocumentUpdate op) {
        this.op = op;
        this.bodyStripped = false;
    }

    DynamicEntry(DocumentRemove op) {
        this.op = op;
        this.bodyStripped = false;
    }

    @Override
    public boolean valid() {
        return true;
    }

    @Override
    public boolean isRemoveEntry() {
        return op instanceof DocumentRemove;
    }

    @Override
    public boolean isBodyStripped() {
        return bodyStripped;
    }

    @Override
    public boolean isUpdateEntry() {
        return op instanceof DocumentUpdate;
    }

    @Override
    public long getTimestamp() {
        if (op instanceof DocumentPut) {
            DocumentPut put = (DocumentPut) op;
            final Long lastModified = put.getDocument().getLastModified();
            if (lastModified != null) {
                return lastModified;
            }
        }
        return 0L;
    }

    @Override
    public DocumentOperation getDocumentOperation() {
        return op;
    }

    @Override
    public DocumentOperation getHeader() {
	return op;
        //TODO: Only return header fields of Document here...?
    }
}
