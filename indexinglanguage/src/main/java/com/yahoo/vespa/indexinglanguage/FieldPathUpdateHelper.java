// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.Field;
import com.yahoo.document.FieldPath;
import com.yahoo.document.FieldPathEntry;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldPathIteratorHandler;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.fieldpathupdate.AddFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.AssignFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.FieldPathUpdate;
import com.yahoo.document.fieldpathupdate.RemoveFieldPathUpdate;

/**
 * @author Simon Thoresen Hult
 */
public abstract class FieldPathUpdateHelper {

    /** Returns true if this update can be represented as a set of document field values. */
    public static boolean isFieldValues(FieldPathUpdate update) {
        if (!(update instanceof AssignFieldPathUpdate)) return false;
        // Only consider field path updates that touch a top-level field as 'complete',
        // as these may be converted to regular field value updates.
        return ((update.getFieldPath().size() == 1)
                && update.getFieldPath().get(0).getType() == FieldPathEntry.Type.STRUCT_FIELD);
    }

    /**
     * Returns true if this update assigns a value to one element of a top-level array field,
     * e.g. <code>my_array[3]</code>. Arithmetic and conditional assigns are not element assigns,
     * as they are computed from the stored document and cannot be processed up front.
     */
    public static boolean isElementAssign(FieldPathUpdate update) {
        if (!(update instanceof AssignFieldPathUpdate assign)) {
            return false;
        }
        if (assign.isArithmetic()) {
            return false;
        }
        if (update.getOriginalWhereClause() != null && !update.getOriginalWhereClause().isEmpty()) {
            return false;
        }
        FieldPath path = update.getFieldPath();
        return path.size() == 2
               && path.get(0).getType() == FieldPathEntry.Type.STRUCT_FIELD
               && path.get(0).getFieldRef().getDataType() instanceof ArrayDataType
               && path.get(1).getType() == FieldPathEntry.Type.ARRAY_INDEX;
    }

    public static void applyUpdate(FieldPathUpdate update, Document doc) {
        if (update instanceof AddFieldPathUpdate) {
            update.applyTo(doc);
        } else if (update instanceof AssignFieldPathUpdate assign) {
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

    /**
     * Creates a partial document holding the element assigned by the given element assign as a
     * single-element array in the target field. The update itself cannot be applied to an empty
     * document, as assigning to an index beyond the array size is a no-op.
     */
    @SuppressWarnings("unchecked")
    public static Document newElementAssignPartialDocument(DocumentId docId, AssignFieldPathUpdate update) {
        Document doc = new Document(update.getDocumentType(), docId);
        Field field = update.getFieldPath().get(0).getFieldRef();
        // isElementAssign guarantees the field is an array, and an ARRAY_INDEX path entry is only
        // ever built by ArrayDataType, so the field value created for the field is an Array.
        Array<FieldValue> array = (Array<FieldValue>)field.getDataType().createFieldValue();
        array.add(update.getNewValue().clone());
        doc.setFieldValue(field, array);
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
