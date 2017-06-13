// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.fieldset;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.FieldValue;

import java.lang.String;
import java.util.*;

/**
 * TODO: Move to document and implement
 */
public class FieldSetRepo {

    FieldSet parseSpecialValues(String name)
    {
        if (name.equals("[id]")) { return new DocIdOnly(); }
        else if (name.equals("[all]")) { return (new AllFields()); }
        else if (name.equals("[none]")) { return (new NoFields()); }
        else if (name.equals("[header]")) { return (new HeaderFields()); }
        else if (name.equals("[docid]")) { return (new DocIdOnly()); }
        else if (name.equals("[body]")) { return (new BodyFields()); }
        else {
            throw new IllegalArgumentException(
                    "The only special names (enclosed in '[]') allowed are " +
                    "id, all, none, header, body");
        }
    }

    FieldSet parseFieldCollection(DocumentTypeManager docMan, String docType, String fieldNames) {
        DocumentType type = docMan.getDocumentType(docType);
        if (type == null) {
         throw new IllegalArgumentException("Unknown document type " + docType);
        }

        StringTokenizer tokenizer = new StringTokenizer(fieldNames, ",");
        FieldCollection collection = new FieldCollection(type);

        for (; tokenizer.hasMoreTokens(); ) {
            String token = tokenizer.nextToken();
            Field f = type.getField(token);
            if (f == null) {
                throw new IllegalArgumentException("No such field " + token);
            }
            collection.add(f);
        }

        return collection;
    }

    public FieldSet parse(DocumentTypeManager docMan, String fieldSet) {
        if (fieldSet.length() == 0) {
            throw new IllegalArgumentException("Illegal field set value \"\"");
        }

        if (fieldSet.startsWith("[")) {
            return parseSpecialValues(fieldSet);
        }

        StringTokenizer tokenizer = new StringTokenizer(fieldSet, ":");
        if (tokenizer.countTokens() != 2) {
            throw new IllegalArgumentException(
                    "The field set list must consist of a document type, " +
                    "then a colon (:), then a comma-separated list of field names");
        }

        String type = tokenizer.nextToken();
        String fields = tokenizer.nextToken();

        return parseFieldCollection(docMan, type, fields);
    }

    public String serialize(FieldSet fieldSet) {
        if (fieldSet instanceof Field) {
            return ((Field)fieldSet).getName();
        } else if (fieldSet instanceof FieldCollection) {
            FieldCollection c = ((FieldCollection)fieldSet);

            StringBuffer buffer = new StringBuffer();
            for (Field f : c) {
                if (buffer.length() == 0) {
                    buffer.append(c.getDocumentType().getName());
                    buffer.append(":");
                } else {
                    buffer.append(",");
                }
                buffer.append(f.getName());
            }

            return buffer.toString();
        } else if (fieldSet instanceof AllFields) {
            return "[all]";
        } else if (fieldSet instanceof NoFields) {
            return "[none]";
        } else if (fieldSet instanceof BodyFields) {
            return "[body]";
        } else if (fieldSet instanceof HeaderFields) {
            return "[header]";
        } else if (fieldSet instanceof DocIdOnly) {
            return "[docid]";
        } else {
            throw new IllegalArgumentException("Unknown field set type " + fieldSet);
        }
    }


    /**
     * Copies fields from one document to another based on whether the fields match the given
     * fieldset.
     */
    public void copyFields(Document source, Document target, FieldSet fieldSet) {
        for (Iterator<Map.Entry<Field, FieldValue>> i = source.iterator(); i.hasNext();) {
            Map.Entry<Field, FieldValue> v = i.next();

            if (fieldSet.contains(v.getKey())) {
                target.setFieldValue(v.getKey(), v.getValue());
            }
        }
    }

    /**
     * Strips all fields not wanted by the given field set from the document.
     */
    public void stripFields(Document target, FieldSet fieldSet) {
        List<Field> toStrip = new ArrayList<Field>();
        for (Iterator<Map.Entry<Field, FieldValue>> i = target.iterator(); i.hasNext();) {
            Map.Entry<Field, FieldValue> v = i.next();

            if (!fieldSet.contains(v.getKey())) {
                toStrip.add(v.getKey());
            }
        }

        for (Field f : toStrip) {
            target.removeFieldValue(f);
        }
    }
}
