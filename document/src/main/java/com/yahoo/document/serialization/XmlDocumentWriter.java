// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.annotation.AnnotationReference;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.BoolFieldValue;
import com.yahoo.document.datatypes.ByteFieldValue;
import com.yahoo.document.datatypes.CollectionFieldValue;
import com.yahoo.document.datatypes.DoubleFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.FloatFieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.MapFieldValue;
import com.yahoo.document.datatypes.PredicateFieldValue;
import com.yahoo.document.datatypes.Raw;
import com.yahoo.document.datatypes.ReferenceFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.datatypes.StructuredFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.vespa.objects.FieldBase;
import com.yahoo.vespa.objects.Serializer;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;

// TODO: Just inline all use of XmlSerializationHelper when the toXml methods in FieldValue subclasses are to be removed
// TODO: More cleanup, the put() methods generate a lot of superfluous objects (write should call put, not the other way around)
// TODO: remove pingpong between XmlSerializationHelper and FieldValue, this will go away when the toXml methods go away
/**
 * Render a Document instance as XML.
 *
 * @author Steinar Knutsen
 */
public final class XmlDocumentWriter implements DocumentWriter {

    private final String indent;
    private XmlStream buffer;
    private final Deque<FieldBase> optionalWrapperMarker = new ArrayDeque<>();

    public static XmlDocumentWriter createWriter(String indent) {
        return new XmlDocumentWriter(indent);
    }

    public XmlDocumentWriter() {
        this("  ");
    }

    private XmlDocumentWriter(String indent) {
        this.indent = indent;
    }

    // this method is silly, what is the intended way of doing this?
    @Override
    public void write(FieldBase field, FieldValue value) {
        Class<?> valueType = value.getClass();
        if (valueType == AnnotationReference.class) {
            write(field, (AnnotationReference) value);
        } else if (valueType == Array.class) {
            write(field, (Array<?>) value);
        } else if (valueType == WeightedSet.class) {
            write(field, (WeightedSet<?>) value);
        } else if (valueType == Document.class) {
            write(field, (Document) value);
        } else if (valueType == Struct.class) {
            write(field, (Struct) value);
        } else if (valueType == ByteFieldValue.class) {
            write(field, (ByteFieldValue) value);
        } else if (valueType == DoubleFieldValue.class) {
            write(field, (DoubleFieldValue) value);
        } else if (valueType == FloatFieldValue.class) {
            write(field, (FloatFieldValue) value);
        } else if (valueType == IntegerFieldValue.class) {
            write(field, (IntegerFieldValue) value);
        } else if (valueType == LongFieldValue.class) {
            write(field, (LongFieldValue) value);
        } else if (valueType == Raw.class) {
            write(field, (Raw) value);
        } else if (valueType == PredicateFieldValue.class) {
            write(field, (PredicateFieldValue) value);
        } else if (valueType == StringFieldValue.class) {
            write(field, (StringFieldValue) value);
        } else {
            throw new UnsupportedOperationException("Cannot serialize a "
                    + valueType.getName());
        }
    }

    @Override
    public void write(FieldBase field, Document value) {
        buffer.beginTag("document");
        buffer.addAttribute("documenttype", value.getDataType().getName());
        buffer.addAttribute("documentid", value.getId());
        final java.lang.Long lastModified = value.getLastModified();
        if (lastModified != null) {
            buffer.addAttribute("lastmodifiedtime", lastModified);
        }
        StructuredFieldValue asStructured = value;
        write(null, asStructured);

        buffer.endTag();
    }

    @Override
    public <T extends FieldValue> void write(FieldBase field, Array<T> value) {
        buffer.beginTag(field.getName());
        XmlSerializationHelper.printArrayXml(value, buffer);
        buffer.endTag();
    }

    private void singleValueTag(FieldBase field, FieldValue value) {
        buffer.beginTag(field.getName());
        value.printXml(buffer);
        buffer.endTag();
    }

    @Override
    public <K extends FieldValue, V extends FieldValue> void write(FieldBase field, MapFieldValue<K, V> map) {
        // TODO Auto-generated method stub
        buffer.beginTag(field.getName());
        XmlSerializationHelper.printMapXml(map, buffer);
        buffer.endTag();
    }

    @Override
    public void write(FieldBase field, ByteFieldValue value) {
        singleValueTag(field, value);
    }

    @Override
    public void write(FieldBase field, BoolFieldValue value) {
        singleValueTag(field, value);
    }

    @Override
    public <T extends FieldValue> void write(FieldBase field,
            CollectionFieldValue<T> value) {
        buffer.beginTag(field.getName());
        for (@SuppressWarnings("unchecked")
        Iterator<FieldValue> i = (Iterator<FieldValue>) value.iterator();
                i.hasNext();) {
            buffer.beginTag("item");
            i.next().printXml(buffer);
            buffer.endTag();
        }
        buffer.endTag();
    }

    @Override
    public void write(FieldBase field, DoubleFieldValue value) {
        singleValueTag(field, value);
    }

    @Override
    public void write(FieldBase field, FloatFieldValue value) {
        singleValueTag(field, value);
    }

    @Override
    public void write(FieldBase field, IntegerFieldValue value) {
        singleValueTag(field, value);
    }

    @Override
    public void write(FieldBase field, LongFieldValue value) {
        singleValueTag(field, value);
    }

    @Override
    public void write(FieldBase field, Raw value) {
        buffer.beginTag(field.getName());
        XmlSerializationHelper.printRawXml(value, buffer);
        buffer.endTag();
    }

    @Override
    public void write(FieldBase field, PredicateFieldValue value) {
        singleValueTag(field, value);
    }

    @Override
    public void write(FieldBase field, StringFieldValue value) {
        buffer.beginTag(field.getName());
        XmlSerializationHelper.printStringXml(value, buffer);
        buffer.endTag();
    }

    @Override
    public void write(FieldBase field, TensorFieldValue value) {
        throw new IllegalArgumentException("write() for tensor field value not implemented yet");
    }

    @Override
    public void write(FieldBase field, ReferenceFieldValue value) {
        throw new IllegalArgumentException("write() for reference field value not implemented yet");
    }

    private void optionalWrapperStart(FieldBase field) {
        if (field == null) {
            return;
        }

        optionalWrapperMarker.addFirst(field);

        buffer.beginTag(field.getName());
    }

    private void optionalWrapperEnd(FieldBase field) {
        if (field == null) {
            return;
        }

        if (optionalWrapperMarker.removeFirst() != field) {
            throw new IllegalStateException("Unbalanced optional wrapper tags.");
        }

        buffer.endTag();
    }

    @Override
    public void write(FieldBase field, Struct value) {
        StructuredFieldValue asStructured = value;
        write(field, asStructured);
    }

    @Override
    public void write(FieldBase field, StructuredFieldValue value) {
        optionalWrapperStart(field);
        Iterator<Map.Entry<Field, FieldValue>> i = value.iterator();
        while (i.hasNext()) {
            Map.Entry<Field, FieldValue> v = i.next();
            buffer.beginTag(v.getKey().getName());
            v.getValue().printXml(buffer);
            buffer.endTag();
        }
        optionalWrapperEnd(field);
    }

    @Override
    public <T extends FieldValue> void write(FieldBase field,
            WeightedSet<T> value) {
        buffer.beginTag(field.getName());
        XmlSerializationHelper.printWeightedSetXml(value, buffer);
        buffer.endTag();
    }

    @Override
    public void write(FieldBase field, AnnotationReference value) {
        // TODO Auto-generated method stub

    }

    @Override
    public Serializer putByte(FieldBase field, byte value) {
        singleValueTag(field, new ByteFieldValue(value));
        return this;
    }

    @Override
    public Serializer putShort(FieldBase field, short value) {
        singleValueTag(field, new IntegerFieldValue(value));
        return this;
    }

    @Override
    public Serializer putInt(FieldBase field, int value) {
        singleValueTag(field, new IntegerFieldValue(value));
        return this;
    }

    @Override
    public Serializer putLong(FieldBase field, long value) {
        singleValueTag(field, new LongFieldValue(value));
        return this;
    }

    @Override
    public Serializer putFloat(FieldBase field, float value) {
        singleValueTag(field, new FloatFieldValue(value));
        return this;
    }

    @Override
    public Serializer putDouble(FieldBase field, double value) {
        singleValueTag(field, new DoubleFieldValue(value));
        return this;
    }

    @Override
    public Serializer put(FieldBase field, byte[] value) {
        write(field, new Raw(value));
        return this;
    }

    @Override
    public Serializer put(FieldBase field, ByteBuffer value) {
        write(field, new Raw(value));
        return this;
    }

    @Override
    public Serializer put(FieldBase field, String value) {
        write(field, new StringFieldValue(value));
        return this;
    }

    @Override
    public void write(Document document) {
        buffer = new XmlStream();
        buffer.setIndent(indent);
        optionalWrapperMarker.clear();
        write(new Field(document.getDataType().getName(), 0, document.getDataType()), document);
    }

    @Override
    public void write(DocumentId id) {
        throw new UnsupportedOperationException("Writing a DocumentId as XML is not implemented.");
    }

    @Override
    public void write(DocumentType type) {
        throw new UnsupportedOperationException("Writing a DocumentId as XML is not implemented.");

    }

    public String lastRendered() {
        return buffer.toString();
    }

}
