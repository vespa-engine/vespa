// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentmodel;

import com.yahoo.document.DataType;
import com.yahoo.document.DataTypeName;
import com.yahoo.document.PositionDataType;

/**
 * This class represents the builtin 'document' document type that all other documenttypes inherits.
 * Remember that changes here must be compatible. Changes to types of fields can not be done here.
 * This must also match the mirroring class in c++.
 *
 * @author baldersheim
 */
public class VespaDocumentType {

    public static final NewDocumentType INSTANCE = newInstance();

    public static final DataTypeName NAME = new DataTypeName("document");

    private static NewDocumentType newInstance() {
        NewDocumentType vespa = new NewDocumentType(new NewDocumentType.Name(8, "document"));
        vespa.add(DataType.BYTE);
        vespa.add(DataType.INT);
        vespa.add(DataType.LONG);
        vespa.add(DataType.STRING);
        vespa.add(DataType.RAW);
        vespa.add(DataType.TAG);
        vespa.add(DataType.FLOAT);
        vespa.add(DataType.DOUBLE);
        vespa.add(DataType.DOCUMENT);
        vespa.add(PositionDataType.INSTANCE);
        vespa.add(DataType.URI);
        vespa.add(DataType.PREDICATE);
        vespa.add(DataType.BOOL);
        vespa.add(DataType.FLOAT16);
        return vespa;
    }

}
