// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.DocumentTypeManager;
import com.yahoo.io.GrowableByteBuffer;

/**
 * Factory for creating document de-serializers tied to a document format.
 *
 * @author geirst
 */
public class DocumentDeserializerFactory {

    /**
     * Creates a de-serializer for the current head document format.
     * This format is an extension of the 6.x format.
     */
    public static DocumentDeserializer createHead(DocumentTypeManager manager, GrowableByteBuffer buf) {
        return new VespaDocumentDeserializerHead(manager, buf);
    }

    /**
     * Creates a de-serializer for the 6.x document format.
     * This format is an extension of the 4.2 format.
     */
    public static DocumentDeserializer create6(DocumentTypeManager manager, GrowableByteBuffer buf) {
        return new VespaDocumentDeserializer6(manager, buf);
    }

}
