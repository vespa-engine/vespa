// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.FieldPathEntry;
import com.yahoo.document.datatypes.FieldPathIteratorHandler;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.fieldpathupdate.AddFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.AssignFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.FieldPathUpdate;
import com.yahoo.document.fieldpathupdate.RemoveFieldPathUpdate;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public abstract class FieldPathUpdateHelper {

    public static boolean isComplete(FieldPathUpdate update) {
        if (!(update instanceof AssignFieldPathUpdate)) {
            return false;
        }
        // Only consider field path updates that touch a top-level field as 'complete',
        // as these may be converted to regular field value updates.
        return ((update.getFieldPath().size() == 1)
                && update.getFieldPath().get(0).getType() == FieldPathEntry.Type.STRUCT_FIELD);
    }

    public static void applyUpdate(FieldPathUpdate update, Document doc) {
        if (update instanceof AddFieldPathUpdate) {
            update.applyTo(doc);
        } else if (update instanceof AssignFieldPathUpdate) {
            AssignFieldPathUpdate assign = (AssignFieldPathUpdate)update;
            boolean createMissingPath = assign.getCreateMissingPath();
            boolean removeIfZero = assign.getRemoveIfZero();
            assign.setCreateMissingPath(true);
            assign.setRemoveIfZero(false);

            assign.applyTo(doc);

            assign.setCreateMissingPath(createMissingPath);
            assign.setRemoveIfZero(removeIfZero);
        } else if (update instanceof RemoveFieldPathUpdate) {
            doc.iterateNested(update.getFieldPath(), 0, new MyHandler());
        }
    }

    public static Document newPartialDocument(DocumentId docId, FieldPathUpdate update) {
        Document doc = new Document(update.getDocumentType(), docId);
        applyUpdate(update, doc);
        return doc;
    }

    private static class MyHandler extends FieldPathIteratorHandler {

        @Override
        public ModificationStatus doModify(FieldValue fv) {
            return ModificationStatus.MODIFIED;
        }

        @Override
        public boolean createMissingPath() {
            return true;
        }
    }
}
