// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json;

import com.yahoo.document.DocumentId;
import com.yahoo.document.Field;

/**
 * @author bjorncs
 */
public class JsonReaderException extends IllegalArgumentException {

    public final DocumentId docId;
    public final Field field;
    public final Throwable cause;

    public JsonReaderException(DocumentId docId, Field field, Throwable cause) {
        super("In document '" + docId + "': Could not parse " + field, cause);
        this.docId = docId;
        this.field = field;
        this.cause = cause;
    }

    public JsonReaderException(Field field, Throwable cause) {
        this(null, field, cause);
    }

    public static JsonReaderException addDocId(JsonReaderException oldException, DocumentId docId) {
        return new JsonReaderException(docId, oldException.field, oldException.cause);
    }

    public DocumentId getDocId() {
        return docId;
    }

    public Field getField() {
        return field;
    }

}
