// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.io.GrowableByteBuffer;

/**
 * Class used for serializing documents on the current head document format.
 *
 * @author baldersheim
 */
public class VespaDocumentSerializerHead extends VespaDocumentSerializer6 {

    public VespaDocumentSerializerHead(GrowableByteBuffer buf) {
        super(buf);
    }

}
