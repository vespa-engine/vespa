// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaxmlparser;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.annotation.AnnotationReference;
import com.yahoo.document.datatypes.*;
import com.yahoo.document.predicate.Predicate;
import com.yahoo.document.serialization.DeserializationException;
import com.yahoo.document.serialization.FieldReader;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.objects.FieldBase;
import org.apache.commons.codec.binary.Base64;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.Optional;

/**
 * XML parser that reads document fields from an XML stream.
 *
 * All read methods assume that the stream is currently positioned at the start element of the relevant field.
 *
 */
public class VespaXMLFieldReader extends VespaXMLReader implements FieldReader {
    private static final BigInteger UINT_MAX = new BigInteger("4294967296");
    private static final BigInteger ULONG_MAX = new BigInteger("18446744073709551616");

    public VespaXMLFieldReader(String fileName, DocumentTypeManager docTypeManager) throws Exception {
        super(fileName, docTypeManager);
    }

    public VespaXMLFieldReader(InputStream stream, DocumentTypeManager docTypeManager) throws Exception {
        super(stream, docTypeManager);
    }

    public VespaXMLFieldReader(XMLStreamReader reader, DocumentTypeManager docTypeManager) {
        super(reader, docTypeManager);
    }

    /**
     * Optional test and set condition. Common for document/update/remove elements
     * This variable is either set in VespaXMLFieldReader#read (reader for document)
     * or in VespaXMLUpdateReader#read (reader for update).
     */
    private Optional<String> condition = Optional.empty();

    public Optional<String> getCondition() {
        return condition;
    }

    public void read(FieldBase field, Document document) {
        try {
            //workaround for documents inside array <item>
            if (reader.getEventType() != XMLStreamReader.START_ELEMENT || !"document".equals(reader.getName().toString())) {
                while (reader.hasNext()) {
                    if (reader.getEventType() == XMLStreamReader.START_ELEMENT && "document".equals(reader.getName().toString())) {
                        break;
                    }
                    reader.next();
                }
            }

            // First fetch attributes.
            String typeName = null;

            for (int i = 0; i < reader.getAttributeCount(); i++) {
                final String attributeName = reader.getAttributeName(i).toString();
                if ("documentid".equals(attributeName) || "id".equals(attributeName)) {
                    document.setId(new DocumentId(reader.getAttributeValue(i)));
                } else if ("documenttype".equals(attributeName) || "type".equals(attributeName)) {
                    typeName = reader.getAttributeValue(i);
                } else if ("condition".equals(attributeName)) {
                    condition = Optional.of(reader.getAttributeValue(i));
                }
            }

            if (document.getId() != null) {
                if (field == null) {
                    field = new FieldBase(document.getId().toString());
                }
            }

            DocumentType doctype = docTypeManager.getDocumentType(typeName);
            if (doctype == null) {
                throw newDeserializeException(field, "Must specify an existing document type, not '" + typeName + "'");
            } else {
                document.setDataType(doctype);
            }

            // Then fetch fields
            while (reader.hasNext()) {
                int type = reader.next();

                if (type == XMLStreamReader.START_ELEMENT) {
                    Field f = doctype.getField(reader.getName().toString());

                    if (f == null) {
                        throw newDeserializeException(field, "Field " + reader.getName() + " not found.");
                    }

                    FieldValue fv = f.getDataType().createFieldValue();
                    fv.deserialize(f, this);
                    document.setFieldValue(f, fv);
                    skipToEnd(f.getName());
                } else if (type == XMLStreamReader.END_ELEMENT) {
                    return;
                }
            }
        } catch (XMLStreamException e) {
            throw newException(field, e);
        }
    }

    public <T extends FieldValue> void read(FieldBase field, Array<T> value) {
        try {
            while (reader.hasNext()) {
                int type = reader.next();

                if (type == XMLStreamReader.START_ELEMENT) {
                    if ("item".equals(reader.getName().toString())) {
                        FieldValue fv = (value.getDataType()).getNestedType().createFieldValue();
                        deserializeFieldValue(field, fv);
                        // noinspection unchecked
                        value.add((T)fv);
                        skipToEnd("item");
                    }
                } else if (type == XMLStreamReader.END_ELEMENT) {
                    return;
                }
            }
        } catch (XMLStreamException e) {
            throw newException(field, e);
        }
    }

    class KeyAndValue {
        FieldValue key = null;
        FieldValue value = null;
    }

    void readKeyAndValue(FieldBase field, KeyAndValue val, MapDataType dt) throws XMLStreamException {
        while (reader.hasNext()) {
            int type = reader.next();

            if (type == XMLStreamReader.START_ELEMENT) {
                if ("key".equals(reader.getName().toString())) {
                    val.key = dt.getKeyType().createFieldValue();
                    deserializeFieldValue(field, val.key);
                    skipToEnd("key");
                } else if ("value".equals(reader.getName().toString())) {
                    val.value = dt.getValueType().createFieldValue();
                    deserializeFieldValue(field, val.value);
                    skipToEnd("value");
                } else {
                    throw newDeserializeException("Illegal element inside map item: " + reader.getName());
                }
            } else if (type == XMLStreamReader.END_ELEMENT) {
                return;
            }
        }
    }

    public <K extends FieldValue, V extends FieldValue> void read(FieldBase field, MapFieldValue<K, V> map) {
        try {
            MapDataType dt = map.getDataType();

            while (reader.hasNext()) {
                int type = reader.next();

                if (type == XMLStreamReader.START_ELEMENT) {
                    if ("item".equals(reader.getName().toString())) {
                        KeyAndValue kv = new KeyAndValue();
                        readKeyAndValue(field, kv, dt);

                        if (kv.key == null || kv.value == null) {
                            throw newDeserializeException(field, "Map items must specify both key and value");
                        }
                        // noinspection unchecked
                        map.put((K)kv.key, (V)kv.value);
                        skipToEnd("item");
                    } else {
                        throw newDeserializeException(field, "Illegal tag " + reader.getName() + " expected 'item'");
                    }
                } else if (type == XMLStreamReader.END_ELEMENT) {
                    return;
                }
            }
        } catch (XMLStreamException e) {
            throw newException(field, e);
        }
    }

    public void read(FieldBase field, Struct value) {
        try {
            boolean base64 = isBase64EncodedElement(reader);
            boolean foundField = false;
            StringBuilder positionBuilder = null;
            while (reader.hasNext()) {
                int type = reader.next();
                if (type == XMLStreamReader.START_ELEMENT) {
                    Field structField = value.getField(reader.getName().toString());
                    if (structField == null) {
                        throw newDeserializeException(field, "Field " + reader.getName() + " not found.");
                    }
                    FieldValue fieldValue = structField.getDataType().createFieldValue();
                    fieldValue.deserialize(structField, this);
                    value.setFieldValue(structField, fieldValue);
                    skipToEnd(structField.getName());
                    foundField = true;
                } else if (type == XMLStreamReader.CHARACTERS) {
                    if (foundField) {
                        continue;
                    }
                    // The text of an XML element may be output using 1-n CHARACTERS
                    // events, so we have to buffer up until the end of the element to
                    // ensure we get everything.
                    String chars = reader.getText();
                    if (positionBuilder == null) {
                        positionBuilder = new StringBuilder(chars);
                    } else {
                        positionBuilder.append(chars);
                    }
                } else if (type == XMLStreamReader.END_ELEMENT) {
                    if (positionBuilder != null) {
                        assignPositionFieldFromStringIfNonEmpty(value, positionBuilder.toString(), base64);
                    }
                    break;
                }
            }
        } catch (XMLStreamException e) {
            throw newException(field, e);
        }
    }

    private void assignPositionFieldFromStringIfNonEmpty(Struct value, String elementText, boolean base64) {
        String str = base64 ? Utf8.toString(new Base64().decode(elementText)) : elementText;
        str = str.trim();
        if (str.isEmpty()) {
            return;
        }
        DataType valueType = value.getDataType();
        if (valueType.equals(PositionDataType.INSTANCE)) {
            value.assign(PositionDataType.fromString(str));
        }
    }

    public <T extends FieldValue> void read(FieldBase field, WeightedSet<T> value) {
        try {
            while (reader.hasNext()) {
                int type = reader.next();

                if (type == XMLStreamReader.START_ELEMENT) {
                    if ("item".equals(reader.getName().toString())) {
                        FieldValue fv = value.getDataType().getNestedType().createFieldValue();

                        int weight = 1;
                        for (int i = 0; i < reader.getAttributeCount(); i++) {
                            if ("weight".equals(reader.getAttributeName(i).toString())) {
                                weight = Integer.parseInt(reader.getAttributeValue(i));
                            }
                        }

                        deserializeFieldValue(field, fv);
                        // noinspection unchecked
                        value.put((T)fv, weight);
                        skipToEnd("item");
                    } else {
                        throw newDeserializeException(field, "Illegal tag " + reader.getName() + " expected 'item'");
                    }
                } else if (type == XMLStreamReader.END_ELEMENT) {
                    return;
                }
            }
        } catch (XMLStreamException e) {
            throw newException(field, e);
        }
    }

    public void read(FieldBase field, ByteFieldValue value) {
        try {
            String dataParsed = reader.getElementText();
            try {
                value.assign(Byte.valueOf(dataParsed));
            } catch (Exception e) {
                throw newDeserializeException(field, "Invalid byte \"" + dataParsed + "\".");
            }
        } catch (XMLStreamException e) {
            throw newException(field, e);
        }
    }

    public void read(FieldBase field, DoubleFieldValue value) {
        try {
            String dataParsed = reader.getElementText();
            try {
                value.assign(Double.valueOf(dataParsed));
            } catch (Exception e) {
                throw newDeserializeException(field, "Invalid double \"" + dataParsed + "\".");
            }
        } catch (XMLStreamException e) {
            throw newException(field, e);
        }
    }

    public void read(FieldBase field, FloatFieldValue value) {
        try {
            String dataParsed = reader.getElementText();
            try {
                value.assign(Float.valueOf(dataParsed));
            } catch (Exception e) {
                throw newDeserializeException(field, "Invalid float \"" + dataParsed + "\".");
            }
        } catch (XMLStreamException e) {
            throw newException(field, e);
        }
    }

    private RuntimeException newDeserializeException(FieldBase field, String msg) {
        return newDeserializeException("Field '" + ((field == null) ? "null" : field.getName()) + "': " + msg);
    }
    private RuntimeException newException(FieldBase field, Exception e) {
        return newDeserializeException("Field '" + ((field == null) ? "null" : field.getName()) + "': " + e.getMessage());
    }
    public void read(FieldBase field, IntegerFieldValue value) {
        try {
            String dataParsed = reader.getElementText();

            BigInteger val;
            try {
                if (dataParsed.startsWith("0x")) {
                    val = new BigInteger(dataParsed.substring(2), 16);
                } else if (dataParsed.startsWith("0") && dataParsed.length() > 1) {
                    val = new BigInteger(dataParsed.substring(1), 8);
                } else {
                    val = new BigInteger(dataParsed);
                }
            } catch (Exception e) {
                throw newDeserializeException(field, "Invalid integer \"" + dataParsed + "\".");
            }
            if (val.bitLength() > 32) {
                throw newDeserializeException(field, "Invalid integer \"" + dataParsed + "\". Out of range.");
            }
            if (val.bitLength() == 32) {
                if (val.compareTo(BigInteger.ZERO) == 1) {
                    // Flip to negative
                    val = val.subtract(UINT_MAX);
                } else {
                    throw newDeserializeException(field, "Invalid integer \"" + dataParsed + "\". Out of range.");
                }
            }

            value.assign(val.intValue());
        } catch (XMLStreamException e) {
            throw newException(field, e);
        }
    }

    public void read(FieldBase field, LongFieldValue value) {
        try {
            String dataParsed = reader.getElementText();

            BigInteger val;
            try {
                if (dataParsed.startsWith("0x")) {
                    val = new BigInteger(dataParsed.substring(2), 16);
                } else if (dataParsed.startsWith("0") && dataParsed.length() > 1) {
                    val = new BigInteger(dataParsed.substring(1), 8);
                } else {
                    val = new BigInteger(dataParsed);
                }
            } catch (Exception e) {
                throw newDeserializeException(field, "Invalid long \"" + dataParsed + "\".");
            }
            if (val.bitLength() > 64) {
                throw newDeserializeException(field, "Invalid long \"" + dataParsed + "\". Out of range.");
            }
            if (val.compareTo(BigInteger.ZERO) == 1 && val.bitLength() == 64) {
                // Flip to negative
                val = val.subtract(ULONG_MAX);
            }
            value.assign(val.longValue());
        } catch (XMLStreamException e) {
            throw newException(field, e);
        }
    }

    public void read(FieldBase field, Raw value) {
        try {
            if (isBase64EncodedElement(reader)) {
                value.assign(new Base64().decode(reader.getElementText()));
            } else {
                value.assign(reader.getElementText().getBytes());
            }
        } catch (XMLStreamException e) {
            throw newException(field, e);
        }
    }

    @Override
    public void read(FieldBase field, PredicateFieldValue value) {
        try {
            if (isBase64EncodedElement(reader)) {
                value.assign(Predicate.fromBinary(new Base64().decode(reader.getElementText())));
            } else {
                value.assign(Predicate.fromString(reader.getElementText()));
            }
        } catch (XMLStreamException e) {
            throw newException(field, e);
        }
    }

    public void read(FieldBase field, StringFieldValue value) {
        try {
            if (isBase64EncodedElement(reader)) {
                throw new IllegalArgumentException("Attribute binaryencoding=base64 is not allowed for fields of type 'string'. To represent binary data, use type 'raw'.");
            } else {
                value.assign(reader.getElementText());
            }
        } catch (XMLStreamException | IllegalArgumentException e) {
            throw newException(field, e);
        }
    }

    @Override
    public void read(FieldBase field, TensorFieldValue value) {
        throwOnlyJsonSupportedException(field, "TENSOR");
    }

    @Override
    public void read(FieldBase field, ReferenceFieldValue value) {
        throwOnlyJsonSupportedException(field, "REFERENCE");
    }

    private static void throwOnlyJsonSupportedException(FieldBase field, String fieldType) {
        throw new DeserializationException("Field '"+ (field != null ? field.getName() : "null") + "': "
                + "XML input for fields of type " + fieldType + " is not supported. Please use JSON input instead.");
    }

    public void read(FieldBase field, AnnotationReference value) {
        System.out.println("Annotation value read!");
    }

    private void deserializeFieldValue(FieldBase field, FieldValue value) {
        value.deserialize(field instanceof Field ? (Field)field : null, this);
    }

    /***********************************************************************/
    /*                   UNUSED METHODS                                    */
    /***********************************************************************/

    @SuppressWarnings("UnusedDeclaration")
    public DocumentId readDocumentId() {
        return null;
    }

    @SuppressWarnings("UnusedDeclaration")
    public DocumentType readDocumentType() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @SuppressWarnings("UnusedDeclaration")
    public DocumentTypeManager getDocumentTypeManager() {
        return docTypeManager;
    }

    @Override
    public <T extends FieldValue> void read(FieldBase field, CollectionFieldValue<T> value) {
        System.out.println("Should not be called!!!");
    }

    @Override
    public void read(FieldBase field, StructuredFieldValue value) {
        System.out.println("Should not be called!!!");
    }

    @Override
    public void read(FieldBase field, FieldValue value) {
        System.out.println("SHOULD NEVER BE CALLED? " + field.toString());
    }

    @Override
    public byte getByte(FieldBase fieldBase) {
        return 0;
    }

    @Override
    public short getShort(FieldBase fieldBase) {
        return 0;
    }

    @Override
    public int getInt(FieldBase fieldBase) {
        return 0;
    }

    @Override
    public long getLong(FieldBase fieldBase) {
        return 0;
    }

    @Override
    public float getFloat(FieldBase fieldBase) {
        return 0;
    }

    @Override
    public double getDouble(FieldBase fieldBase) {
        return 0;
    }

    @Override
    public byte[] getBytes(FieldBase fieldBase, int i) {
        return new byte[0];
    }

    @Override
    public String getString(FieldBase fieldBase) {
        return null;
    }
}
