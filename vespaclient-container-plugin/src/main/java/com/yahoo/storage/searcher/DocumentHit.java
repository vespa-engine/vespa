// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.storage.searcher;

import com.yahoo.document.Document;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.search.result.Hit;

import java.util.Iterator;
import java.util.Map;

public class DocumentHit extends Hit {

    private Document document;
    private int index;

    public DocumentHit(Document document, int index) {
        super(document.getId().toString());
        this.document = document;
        this.index = index;
    }

    public void populateHitFields() {
        // Create hit fields for all document fields
        Iterator<Map.Entry<Field, FieldValue>> fieldIter = document.iterator();
        while (fieldIter.hasNext()) {
            Map.Entry<Field, FieldValue> field = fieldIter.next();
            setField(field.getKey().getName(), field.getValue());
        }

        // Assign an explicit document id field
        setField("documentid", document.getId().toString());
    }

    public Document getDocument() {
        return document;
    }

    public int getIndex() {
        return index;
    }

}
