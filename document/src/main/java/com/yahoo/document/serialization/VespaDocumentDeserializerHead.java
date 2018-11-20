// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.DocumentTypeManager;
import com.yahoo.io.GrowableByteBuffer;

/**
 * Class used for de-serializing documents on the current head document format.
 *
 * @author baldersheim
 */
@SuppressWarnings("deprecation")
public class VespaDocumentDeserializerHead extends VespaDocumentDeserializer6 {

    public VespaDocumentDeserializerHead(DocumentTypeManager manager, GrowableByteBuffer buffer) {
        super(manager, buffer);
    }

}
