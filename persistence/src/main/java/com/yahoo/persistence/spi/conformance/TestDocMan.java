// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.spi.conformance;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;

public class TestDocMan extends DocumentTypeManager {

    public TestDocMan() {
        DocumentType docType = new DocumentType("testdoctype1");
        docType.addHeaderField("headerval", DataType.INT);
        docType.addField("content", DataType.STRING);

        registerDocumentType(docType);
    }

    public Document createRandomDocumentAtLocation(long location, long timestamp) {
        return createRandomDocumentAtLocation(location, timestamp, 100, 100);
    }

    public Document createRandomDocumentAtLocation(long location, long timestamp, int minSize, int maxSize) {
        Document document = new Document(getDocumentType("testdoctype1"),
                new DocumentId("userdoc:footype:" + location + ":" + timestamp));

        document.setFieldValue("headerval", new IntegerFieldValue((int)timestamp));

        StringBuffer value = new StringBuffer();
        int length = (int)(Math.random() * (maxSize - minSize)) + minSize;
        for (int i = 0; i < length; ++i) {
            value.append("A");
        }

        document.setFieldValue("content", new StringFieldValue(value.toString()));
        return document;
    }
}
