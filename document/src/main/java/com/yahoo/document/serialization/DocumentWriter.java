// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentType;

/**
 * @author ravishar
 */
public interface DocumentWriter extends FieldWriter {

    /** Writes a document. */
    void write(Document document);

    void write(DocumentId id);

    void write(DocumentType type);

}
