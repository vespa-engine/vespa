// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.fieldpathupdate;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.datatypes.FieldPathIteratorHandler;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.serialization.DocumentUpdateReader;

/**
 * @author <a href="mailto:thomasg@yahoo-inc.com">Thomas Gundersen</a>
 */
public class RemoveFieldPathUpdate extends FieldPathUpdate {
    class IteratorHandler extends FieldPathIteratorHandler {
        IteratorHandler() {
        }

        @Override
        public ModificationStatus doModify(FieldValue fv) {
            return ModificationStatus.REMOVED;
        }

        @Override
        public boolean onComplex(FieldValue fv) {
            return false;
        }
    }

    IteratorHandler handler;

    public RemoveFieldPathUpdate(DocumentType type, String fieldPath, String whereClause) {
        super(FieldPathUpdate.Type.REMOVE, type, fieldPath, whereClause);
        handler = new IteratorHandler();
    }

    public RemoveFieldPathUpdate(DocumentType type, String fieldPath) {
        super(FieldPathUpdate.Type.REMOVE, type, fieldPath, null);
        handler = new IteratorHandler();
    }

    public RemoveFieldPathUpdate(DocumentType type, DocumentUpdateReader reader) {
        super(FieldPathUpdate.Type.REMOVE, type, reader);
        reader.read(this);
        handler = new IteratorHandler();
    }

    FieldPathIteratorHandler getIteratorHandler(Document doc) {
        return handler;
    }

    @Override
    public String toString() {
        return "Remove: " + super.toString();
    }
}
