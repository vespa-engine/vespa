// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.io.GrowableByteBuffer;

/**
 * Interface for de-serializing documents.
 *
 * A particular instance of this class is tied to a version of the document format.
 *
 * @author geirst
 */
public interface DocumentDeserializer extends DocumentReader, DocumentUpdateReader, FieldReader, AnnotationReader, SpanNodeReader, SpanTreeReader {

    /**
     * Returns the underlying buffer used for de-serialization.
     */
    GrowableByteBuffer getBuf();

}

