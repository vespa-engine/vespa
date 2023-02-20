// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.FieldPath;
import com.yahoo.document.serialization.DocumentSerializer;
import com.yahoo.document.serialization.DocumentSerializerFactory;
import com.yahoo.document.serialization.FieldReader;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.document.serialization.XmlStream;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.vespa.objects.BufferSerializer;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.Identifiable;
import com.yahoo.vespa.objects.Ids;
import com.yahoo.vespa.objects.Serializer;

/**
 * @author Einar M R Rosenvinge
 */
public abstract class FieldValue extends Identifiable implements Comparable<FieldValue> {

    public static final int classId = registerClass(Ids.document + 9, FieldValue.class);

    public abstract DataType getDataType();

    public static FieldValue create(FieldReader reader, DataType type) {
        FieldValue value = type.createFieldValue();
        value.deserialize(reader);
        return value;
    }

    /**
     * Get XML representation of a single field and all its children, if any.
     *
     * @return XML representation of field in a &lt;value&gt; element
     */
    @Deprecated
    public String toXml() {
        XmlStream xml = new XmlStream();
        xml.setIndent("  ");
        xml.beginTag("value");
        printXml(xml);
        xml.endTag();
        return xml.toString();
    }

    /**
     * Read data from the given buffer to create this field value. As some field values have their type self
     * contained, we need the type manager object to be able to retrieve it.
     */
    final public void deserialize(FieldReader reader) {
        deserialize(null, reader);
    }

    final public void serialize(GrowableByteBuffer buf) {
        serialize(DocumentSerializerFactory.create6(buf));
    }

    @Deprecated
    public abstract void printXml(XmlStream xml);

    public abstract void clear();

    @Override
    public FieldValue clone() {
        return (FieldValue) super.clone();
    }

    boolean checkAssign(Object o) {
        if (o == null) {
            clear();
            return false;
        }

        return true;
    }

    /**
     * Assign this non-fieldvalue value to this field value. This is used to be able
     * to assign ints to Integer field values and List to Array field values and such.
     * <p>
     * Override to accept the specific types that should be legal.
     *
     * @throws IllegalArgumentException If the object given is of wrong type for this field value.
     */
    public abstract void assign(Object o);

    /**
     * Used to retrieve wrapped type for simple types, such that you can use get methods to retrieve ints and floats
     * directly instead of Int/Float field values. Complex types that can't be specified by simple java types just
     * return themself.
     */
    public Object getWrappedValue() {
        return this;
    }

    static class RecursiveIteratorHandler extends FieldPathIteratorHandler {
        FieldValue retVal = null;
        boolean multiValue = false;

        @Override
        public boolean onComplex(FieldValue fv) {
            onPrimitive(fv);
            return false;
        }

        @Override
        public void onPrimitive(FieldValue fv) {
            if (retVal != null) {
                if (multiValue) {
                    ((Array<FieldValue>) retVal).add(fv);
                } else {
                    Array<FieldValue> afv = new Array<>(new ArrayDataType(retVal.getDataType()));
                    afv.add(retVal);
                    afv.add(fv);
                    retVal = afv;
                    multiValue = true;
                }
            } else {
                retVal = fv;
            }
        }
    }

    /**
     * Using the given field path, digs through the document and returns the matching field value.
     * If the field path resolves to multiple values, returns an ArrayFieldValue containing the
     * values.
     */
    public FieldValue getRecursiveValue(String path) {
        return getRecursiveValue(getDataType().buildFieldPath(path));
    }

    public FieldValue getRecursiveValue(FieldPath path) {
        RecursiveIteratorHandler handler = new RecursiveIteratorHandler();
        iterateNested(path, 0, handler);
        return handler.retVal;
    }

    @Override
    public void onSerialize(Serializer target) {
        if (target instanceof FieldWriter) {
            serialize(null, (FieldWriter) target);
        } else if (target instanceof BufferSerializer) {
            serialize(null, DocumentSerializerFactory.create6(((BufferSerializer) target).getBuf()));
        } else {
            DocumentSerializer fw = DocumentSerializerFactory.create6(new GrowableByteBuffer());
            serialize(null, fw);
            target.put(null, fw.getBuf().getByteBuffer());
        }
    }

    @Override
    public void onDeserialize(Deserializer data) {
        if (data instanceof FieldReader) {
            deserialize(null, (FieldReader) data);
        } else {
            throw new IllegalArgumentException("I am not able to deserialize from " + data.getClass().getName());
        }
    }

    /**
     * Iterates through the document using the given fieldpath, calling callbacks in the given iterator
     * handler.
     */
    FieldPathIteratorHandler.ModificationStatus iterateNested(FieldPath fieldPath, int pos, FieldPathIteratorHandler handler) {
        if (pos >= fieldPath.size()) {
            handler.onPrimitive(this);
            return handler.modify(this);
        } else {
            throw new IllegalArgumentException("Primitive types can't be iterated through");
        }
    }

    /**
     * Write out field value to the specified writer
     */
    abstract public void serialize(Field field, FieldWriter writer);

    /**
     * Read a field value from the specified reader
     */
    abstract public void deserialize(Field field, FieldReader reader);

    @Override
    public int compareTo(FieldValue fieldValue) {
        return getDataType().compareTo(fieldValue.getDataType());
    }

}
