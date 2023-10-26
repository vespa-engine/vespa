// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.fieldpathupdate;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.CollectionFieldValue;
import com.yahoo.document.datatypes.FieldPathIteratorHandler;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.serialization.DocumentUpdateReader;
import com.yahoo.document.serialization.VespaDocumentSerializer6;

/**
 * @author Thomas Gundersen
 */
public class AddFieldPathUpdate extends FieldPathUpdate {

    class IteratorHandler extends FieldPathIteratorHandler {
        Array newValues;

        IteratorHandler(Array newValues) {
            this.newValues = newValues;
        }

        @SuppressWarnings({ "unchecked" })
        @Override
        public ModificationStatus doModify(FieldValue fv) {
            for (Object newValue : newValues.getValues()) {
                ((CollectionFieldValue)fv).add((FieldValue) newValue);
            }
            return ModificationStatus.MODIFIED;
        }

        @Override
        public boolean createMissingPath() {
            return true;
        }

        @Override
        public boolean onComplex(FieldValue fv) {
            return false;
        }

        public Array getNewValues() {
            return newValues;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            IteratorHandler that = (IteratorHandler) o;

            if (!newValues.equals(that.newValues)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return newValues.hashCode();
        }
    }

    IteratorHandler handler;

    public AddFieldPathUpdate(DocumentType type, String fieldPath, String whereClause, Array newValues) {
        super(FieldPathUpdate.Type.ADD, type, fieldPath, whereClause);
        setNewValues(newValues);
    }

    public AddFieldPathUpdate(DocumentType type, String fieldPath, Array newValues) {
        super(FieldPathUpdate.Type.ADD, type, fieldPath, null);
        setNewValues(newValues);
    }

    public AddFieldPathUpdate(DocumentType type, DocumentUpdateReader reader) {
        super(FieldPathUpdate.Type.ADD, type, reader);
        reader.read(this);
    }

    public AddFieldPathUpdate(DocumentType type, String fieldPath) {
        super(FieldPathUpdate.Type.ADD, type, fieldPath, null);
    }

    public void setNewValues(Array value) {
        handler = new IteratorHandler(value);
    }

    public Array getNewValues() {
        return handler.getNewValues();
    }

    public FieldPathIteratorHandler getIteratorHandler(Document doc) {
        return handler;
    }

    @Override
    public void serialize(VespaDocumentSerializer6 data) {
        data.write(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        AddFieldPathUpdate that = (AddFieldPathUpdate) o;

        if (!handler.equals(that.handler)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + handler.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Add: " + super.toString() + " : " + handler.getNewValues();
    }
}
