// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.proxy;
import com.yahoo.docproc.impl.DocumentOperationWrapper;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.serialization.DocumentUpdateWriter;
import com.yahoo.document.update.FieldUpdate;

import java.util.Collection;
import java.util.Map;

/**
 * Schema mapped facade to a DocumentUpdate
 * 
 * @author vegardh
 */
public class ProxyDocumentUpdate extends DocumentUpdate implements DocumentOperationWrapper {

    private DocumentUpdate docU;

    /**
     * The field name map for schema mapping. The key is the field name that the docproc uses. 
     * The value is the actual name of the field in the document.
     */
    private Map<String, String> fieldMap;

    public ProxyDocumentUpdate(DocumentUpdate docUpd, Map<String, String> fieldMap) {
        super(docUpd.getType(), docUpd.getId().toString()+"-schemamappedupdate");
        this.docU=docUpd;
        this.fieldMap=fieldMap;
    }

    @Override
    public DocumentType getDocumentType() {
        return docU.getDocumentType();
    }

    @Override
    public FieldUpdate getFieldUpdate(Field field) {
        return docU.getFieldUpdate(field);
    }

    @Override
    public FieldUpdate getFieldUpdate(String fieldName) {
        String mapped = fieldMap.get(fieldName);
        if (mapped==null) {
            return docU.getFieldUpdate(fieldName);
        }
        // TODO how about structs here?
        return docU.getFieldUpdate(mapped);
    }

    @Override
    public Collection<FieldUpdate> fieldUpdates() {
        return docU.fieldUpdates();
    }
    @Override
    public DocumentId getId() {
        return docU.getId();
    }

    @Override
    public DocumentType getType() {
        return docU.getType();
    }

    @Override
    public DocumentUpdate addFieldUpdate(FieldUpdate fieldUpdate) {
        return docU.addFieldUpdate(fieldUpdate);
    }

    @Override
    public DocumentUpdate applyTo(Document doc) {
        return docU.applyTo(doc);
    }

    @Override
    public boolean equals(Object o) {
        return docU.equals(o);
    }

    @Override
    public int hashCode() {
        return docU.hashCode();
    }

    @Override
    public void serialize(DocumentUpdateWriter data) {
        docU.serialize(data);
    }

    @Override
    public int size() {
        return docU.size();
    }

    @Override
    public String toString() {
        return docU.toString();
    }

    @Override
    public DocumentOperation getWrappedDocumentOperation() {
        DocumentOperation innermostDocOp = docU;
        while (innermostDocOp instanceof DocumentOperationWrapper) {
            innermostDocOp = ((DocumentOperationWrapper) innermostDocOp).getWrappedDocumentOperation();
        }
        return innermostDocOp;
    }

}
