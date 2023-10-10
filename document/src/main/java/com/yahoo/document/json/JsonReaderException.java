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
        super(createErrorMessage(docId, field, cause), cause);
        this.docId = docId;
        this.field = field;
        this.cause = cause;
    }

    public JsonReaderException(Field field, Throwable cause) {
        super(createErrorMessage(null, field, cause), cause);
        this.docId = null;
        this.field = field;
        this.cause = cause;
    }

    public static JsonReaderException addDocId(JsonReaderException oldException, DocumentId docId) {
        return new JsonReaderException(docId, oldException.field, oldException.cause);
    }

    private static String createErrorMessage(DocumentId docId, Field field, Throwable cause) {
        return String.format("Error in document '%s' - could not parse field '%s' of type '%s': %s",
                             docId, field.getName(), field.getDataType().getName(), cause.getMessage());
    }

    public DocumentId getDocId() {
        return docId;
    }

    public Field getField() {
        return field;
    }

}
