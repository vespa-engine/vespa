// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.proxy;

import com.yahoo.docproc.Accesses;
import com.yahoo.docproc.impl.DocumentOperationWrapper;
import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.FieldPath;
import com.yahoo.document.datatypes.FieldPathIteratorHandler;
import com.yahoo.document.datatypes.FieldPathIteratorHandler.ModificationStatus;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.serialization.DocumentReader;
import com.yahoo.document.serialization.DocumentWriter;
import com.yahoo.document.serialization.FieldReader;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.document.serialization.SerializationException;
import com.yahoo.document.serialization.XmlStream;
import com.yahoo.vespa.objects.Serializer;

import java.io.OutputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * This is a facade to a Document, with two purposes:
 * <ul>
 *     <li>Getters and setters for field data may take into account a schema map of field names.
 *     <li>Mapping into struct fields of arbitrary depth using from→mystruct.mystruct.myfield
 * </ul>
 *
 * This also enforces the @Accesses annotation(s) of the doc proc which uses this.
 *
 * @author Vegard Havdal
 */
public class ProxyDocument extends Document implements DocumentOperationWrapper {

    private final Map<String, String> fieldMap;
    private final Set<String> fieldsAllowed = new HashSet<>();
    private final String docProcName;
    private Document doc;

    public ProxyDocument(DocumentProcessor docProc, Document doc, Map<String, String> fieldMap) {
        super(doc);
        if (docProc.getClass().getAnnotation(Accesses.class)!=null) {
            for (com.yahoo.docproc.Accesses.Field field : docProc.getClass().getAnnotation(Accesses.class).value()) {
                String name = field.name();
                if (fieldMap!=null && fieldMap.get(name) !=null) name = fieldMap.get(name);
                fieldsAllowed.add(name);
            }
        }
        this.fieldMap = fieldMap;
        this.docProcName = docProc.toString();
        this.doc=doc;
    }

    private void checkAccess(Field field) {
        if (!fieldsAllowed.isEmpty() && !fieldsAllowed.contains(field.getName())) {
            throw new IllegalArgumentException("Processor '" + docProcName + "' is not allowed to access field '" +
                                               field.getName() + "'.");
        }
    }

    /**
     * note that the returned Field may not be in this Document
     * directly, but may refer to a field in a struct contained in it,
     * in which case the returned Field is only useful for obtaining
     * the field type; it can't be used for get() and set().
     */
    @Override
    public Field getField(String fieldName) {
        if (fieldMap != null && fieldMap.containsKey(fieldName)) {
            fieldName = fieldMap.get(fieldName);
        }
        FieldPath path = getFieldPath(fieldName);
        Field ret = path.get(path.size() - 1).getFieldRef();
        checkAccess(ret);
        return ret;
    }

    @Override
    public FieldValue getFieldValue(String fieldName) {
        return getRecursiveValue(getFieldPath(fieldName));
    }

    @Override
    public FieldValue getFieldValue(Field field) {
        //checkAccess(field);
        return doc.getFieldValue(field);
    }

    @Override
    public FieldValue setFieldValue(String fieldName, FieldValue fieldValue) {
        SetHandler handler = new SetHandler(fieldValue);
        FieldPath path = getFieldPath(fieldName);
        iterateNested(path, 0, handler);
        if (!handler.doModifyCalled) {
            //the value in question was not found
            throw new IllegalArgumentException("Field '" + fieldName + "' mapped by '" + path + "' was not found.");
        }
        return handler.prevVal;
    }

    @Override
    public FieldValue setFieldValue(Field field, FieldValue fieldValue) {
        checkAccess(field);
        return doc.setFieldValue(field, fieldValue);
    }

    @Override
    public FieldValue removeFieldValue(String fieldName) {
        RemoveHandler handler = new RemoveHandler();
        FieldPath path = getFieldPath(fieldName);
        iterateNested(path, 0, handler);
        if (!handler.doModifyCalled) {
            //the value in question was not found
            throw new IllegalArgumentException("Field '" + fieldName + "' mapped by '" + path + "' was not found.");
        }
        return handler.prevVal;
    }

    @Override
    public FieldValue removeFieldValue(Field field) {
        checkAccess(field);
        return doc.removeFieldValue(field);
    }

    private FieldPath getFieldPath(String fieldName) {
        if (fieldMap != null && fieldMap.containsKey(fieldName)) {
            fieldName = fieldMap.get(fieldName);
        }
        checkAccess(new Field(fieldName));
        FieldPath path = FieldPath.newInstance(getDataType(), fieldName);
        if (path == null || path.size() == 0) {
            throw new IllegalArgumentException("Malformed schema mapping '" + fieldName + "'.");
        }
        return path;
    }

    public DocumentOperation getWrappedDocumentOperation() {
        return new DocumentPut(this);
    }

    private static class SetHandler extends FieldPathIteratorHandler {

        private final FieldValue nextVal;
        private FieldValue prevVal;
        private boolean doModifyCalled = false;

        public SetHandler(FieldValue fieldValue) {
            nextVal = fieldValue;
        }

        @Override
        public boolean onComplex(FieldValue fieldVal) {
            return false;
        }

        @Override
        public boolean createMissingPath() {
            return true;
        }


        @Override
        public ModificationStatus doModify(FieldValue fieldVal) {
            doModifyCalled = true;
            prevVal = fieldVal.clone();
            fieldVal.assign(nextVal);
            return ModificationStatus.MODIFIED;
        }
    }

    @Override
    public boolean equals(Object o) {
        return doc.equals(o);
    }

    @Override
    public String toString() {
        return doc.toString();
    }

    @Override
    public int hashCode() {
        return doc.hashCode();
    }

    @Override
    public Document clone() {
        return doc.clone();
    }

    @Override
    public void clear() {
        doc.clear();
    }

    @Override
    public Iterator<Entry<Field, FieldValue>> iterator() {
        return doc.iterator();
    }

    @Override
    public DocumentId getId() {
        return doc.getId();
    }

    @Override
    public void setLastModified(Long lastModified) {
        doc.setLastModified(lastModified);
    }

    @Override
    public Long getLastModified() {
        return doc.getLastModified();
    }

    @Override
    public void setId(DocumentId id) {
        doc.setId(id);
    }

    @Override
    public void assign(Object o) {
        doc.assign(o);
    }

    @Override
    public void setDataType(DataType type) {
        doc.setDataType(type);
    }

    @Override
    public int getSerializedSize() throws SerializationException {
        return doc.getSerializedSize();
    }

    @Override
    public void serialize(OutputStream out) throws SerializationException {
        doc.serialize(out);
    }

    @Override
    protected void doSetFieldValue(Field field, FieldValue value) {
        super.doSetFieldValue(field, value);
    }

    @Override
    public String toXML(String indent) {
        return doc.toXML(indent);
    }

    @Override
    public String toXml() {
        return doc.toXml();
    }

    @Override
    public void printXml(XmlStream xml) {
        doc.printXml(xml);
    }

    @Override
    public String toJson() {
        return doc.toJson();
    }

    @Override
    public void onSerialize(Serializer target) throws SerializationException {
        doc.onSerialize(target);
    }

    @Override
    public DocumentType getDataType() {
        return doc.getDataType();
    }

    @Override
    public int getFieldCount() {
        return doc.getFieldCount();
    }

    @Override
    public void serialize(DocumentWriter writer) {
        doc.serialize(writer);
    }

    @Override
    public void deserialize(DocumentReader reader) {
        doc.deserialize(reader);
    }

    @Override
    public void serialize(Field field, FieldWriter writer) {
        doc.serialize(field, writer);
    }

    @Override
    public void deserialize(Field field, FieldReader reader) {
        doc.deserialize(field, reader);
    }

    @Override
    public int compareTo(FieldValue fieldValue) {
        return super.compareTo(fieldValue);
    }

    @Override
    public ModificationStatus iterateNested(FieldPath fieldPath, int pos,
            FieldPathIteratorHandler handler) {
        return doc.iterateNested(fieldPath, pos, handler);
    }

    private static class RemoveHandler extends FieldPathIteratorHandler {
        private boolean doModifyCalled = false;
        private FieldValue prevVal;

        @Override
        public boolean onComplex(FieldValue fieldVal) {
            return false;
        }

        @Override
        public ModificationStatus doModify(FieldValue fieldVal) {
            doModifyCalled = true;
            prevVal = fieldVal.clone();
            return ModificationStatus.REMOVED;
        }
    }

    /**
     * The {@link Document} which this proxies
     * @return The proxied Document
     */
    public Document getDocument() {
        return doc;
    }

}
