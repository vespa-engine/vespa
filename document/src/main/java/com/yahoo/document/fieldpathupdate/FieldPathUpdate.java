// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.fieldpathupdate;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.FieldPath;
import com.yahoo.document.datatypes.FieldPathIteratorHandler;
import com.yahoo.document.select.DocumentSelector;
import com.yahoo.document.select.Result;
import com.yahoo.document.select.ResultList;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.document.serialization.DocumentUpdateReader;
import com.yahoo.document.serialization.VespaDocumentSerializer6;
import java.util.ListIterator;

/**
 * @author Thomas Gundersen
 */
public abstract class FieldPathUpdate {

    public enum Type {
        ASSIGN(0),
        REMOVE(1),
        ADD(2);

        private final int code;

        private Type(int code) {
            this.code = code;
        }

        public static Type valueOf(int code) {
            for (Type type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Field path update type " + code + " not supported.");
        }

        public int getCode() {
            return code;
        }
    }

    private FieldPath fieldPath;
    private DocumentSelector selector;
    private String originalFieldPath;
    private String whereClause;
    private Type updType;
    private DocumentType docType;

    public FieldPathUpdate(Type updType, DocumentType docType, String fieldPath, String whereClause) {
        this.updType = updType;
        this.docType = docType;

        try {
            setWhereClause(whereClause);
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
        setFieldPath(fieldPath);
    }

    public FieldPathUpdate(Type updType, DocumentType docType, DocumentUpdateReader reader) {
        this(updType, docType);
        reader.read(this);
    }

    public FieldPathUpdate(Type updType, DocumentType docType) {
        this.updType = updType;
        this.docType = docType;
    }

    public Type getUpdateType() {
        return updType;
    }

    public DocumentType getDocumentType() {
        return docType;
    }

    public void setFieldPath(String fieldPath) {
        originalFieldPath = fieldPath;
        this.fieldPath = docType.buildFieldPath(fieldPath);
    }

    public FieldPath getFieldPath() {
        return fieldPath;
    }

    public String getOriginalFieldPath() {
        return originalFieldPath;
    }

    public void setWhereClause(String whereClause) throws ParseException {
        this.whereClause = whereClause;
        selector = null;
        if (whereClause != null && !whereClause.equals("")) {
            selector = new DocumentSelector(whereClause);
        }
    }

    public DocumentSelector getWhereClause() {
        return selector;
    }

    public String getOriginalWhereClause() {
        return whereClause;
    }

    public void applyTo(Document doc) {
        if (selector == null) {
            FieldPathIteratorHandler handler = getIteratorHandler(doc);
            doc.iterateNested(fieldPath, 0, handler);
        } else {
            ResultList results = selector.getMatchingResultList(new DocumentPut(doc));
            ListIterator<ResultList.ResultPair> resultIter = results.getResults().listIterator(results.getResults().size());
            while (resultIter.hasPrevious()) {
                ResultList.ResultPair rp = resultIter.previous();
                if (rp.getResult() == Result.TRUE) {
                    FieldPathIteratorHandler handler = getIteratorHandler(doc);
                    handler.getVariables().clear();
                    handler.getVariables().putAll(rp.getVariables());

                    doc.iterateNested(fieldPath, 0, handler);
                }
            }
        }
    }

    public void serialize(VespaDocumentSerializer6 data) {
        data.write(this);
    }

    public static FieldPathUpdate create(Type type, DocumentType docType, DocumentUpdateReader reader) {
        switch (type) {
        case ASSIGN:
            return new AssignFieldPathUpdate(docType, reader);
        case ADD:
            return new AddFieldPathUpdate(docType, reader);
        case REMOVE:
            return new RemoveFieldPathUpdate(docType, reader);
        }
        throw new IllegalArgumentException("Field path update type '" + type + "' not supported.");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FieldPathUpdate that = (FieldPathUpdate) o;

        if (docType != null ? !docType.equals(that.docType) : that.docType != null) return false;
        if (originalFieldPath != null ? !originalFieldPath.equals(that.originalFieldPath) : that.originalFieldPath != null)
            return false;
        if (whereClause != null ? !whereClause.equals(that.whereClause) : that.whereClause != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = originalFieldPath != null ? originalFieldPath.hashCode() : 0;
        result = 31 * result + (whereClause != null ? whereClause.hashCode() : 0);
        result = 31 * result + (docType != null ? docType.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "fieldpath=\"" + originalFieldPath + "\"" + (whereClause != null ? " where=\"" + whereClause + "\"" : "");
    }

    abstract FieldPathIteratorHandler getIteratorHandler(Document doc);
}
