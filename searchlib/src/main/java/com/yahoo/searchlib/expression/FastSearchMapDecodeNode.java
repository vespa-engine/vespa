// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

import java.util.Objects;

/**
 * Decodes the entries of a map, or array of struct, with fast map search.
 *
 * The backend reads the hidden attribute "fieldname$lookupname", and decodes each entry into a string on the form
 * key + separator + value, where the separator is ":" unless set. The values are decoded according to the value type,
 * which is resolved from the schema by the container. The attribute name, value type and separator are serialized.
 *
 * @author arnej
 */
public class FastSearchMapDecodeNode extends FunctionNode {

    /** The type of the values in the map. The ids are serialized; keep them in sync with the C++ implementation. */
    public enum ValueType {

        UNKNOWN(0), INT(1), LONG(2), FLOAT(3), DOUBLE(4), STRING(5);

        private final byte id;

        ValueType(int id) { this.id = (byte)id; }

        /** Returns the id this is serialized as. */
        public byte id() { return id; }

        /** Returns the value type with the given serialized id, or UNKNOWN if there is none. */
        public static ValueType fromId(byte id) {
            for (ValueType type : values())
                if (type.id == id) return type;
            return UNKNOWN;
        }

    }

    public static final int classId = registerClass(0x4000 + 185, FastSearchMapDecodeNode.class, FastSearchMapDecodeNode::new);
    public static final String DEFAULT_SEPARATOR = ":";
    private String attributeName;
    private ValueType valueType = ValueType.UNKNOWN;
    private String separator = DEFAULT_SEPARATOR;

    /**
     * Constructs an empty node. <b>NOTE:</b> This instance is broken until non-optional member data is set.
     */
    public FastSearchMapDecodeNode() { }

    /**
     * Constructs a node decoding the given fast map lookup attribute.
     *
     * @param attributeName the hidden attribute of the fast map lookup, on the form "fieldname$lookupname"
     * @param valueType     the type of the values in the map
     */
    public FastSearchMapDecodeNode(String attributeName, ValueType valueType) {
        this(attributeName, valueType, DEFAULT_SEPARATOR);
    }

    /**
     * Constructs a node decoding the given fast map lookup attribute, with the given separator between key and value.
     *
     * @param attributeName the hidden attribute of the fast map lookup, on the form "fieldname$lookupname"
     * @param valueType     the type of the values in the map
     * @param separator     the separator between key and value in the decoded entries
     */
    public FastSearchMapDecodeNode(String attributeName, ValueType valueType, String separator) {
        if (attributeName == null) {
            throw new IllegalArgumentException("Fast map lookup attribute name cannot be null.");
        }
        if (valueType == null) {
            throw new IllegalArgumentException("Fast map lookup value type cannot be null.");
        }
        this.attributeName = attributeName;
        this.valueType = valueType;
        setSeparator(separator);
    }

    /** Returns the name of the hidden attribute of the fast map lookup, on the form "fieldname$lookupname". */
    public String getAttributeName() {
        return attributeName;
    }

    /** Returns the type of the values in the map. */
    public ValueType getValueType() {
        return valueType;
    }

    /** Returns the separator between key and value in the decoded entries. */
    public String getSeparator() {
        return separator;
    }

    /**
     * Sets the separator between key and value in the decoded entries.
     *
     * @param separator the separator, ":" by default
     * @return this, to allow chaining
     */
    public FastSearchMapDecodeNode setSeparator(String separator) {
        if (separator == null) {
            throw new IllegalArgumentException("Fast map lookup separator cannot be null.");
        }
        this.separator = separator;
        return this;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        putUtf8(buf, attributeName);
        buf.putByte(null, valueType.id());
        putUtf8(buf, separator);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        attributeName = getUtf8(buf);
        valueType = ValueType.fromId(buf.getByte(null));
        separator = getUtf8(buf);
    }

    @Override
    public int hashCode() {
        return super.hashCode() + Objects.hash(attributeName, valueType, separator);
    }

    @Override
    protected boolean equalsFunction(FunctionNode obj) {
        FastSearchMapDecodeNode rhs = (FastSearchMapDecodeNode)obj;
        return attributeName.equals(rhs.attributeName) && valueType == rhs.valueType && separator.equals(rhs.separator);
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("attributeName", attributeName);
        visitor.visit("valueType", valueType.name().toLowerCase());
        visitor.visit("separator", separator);
    }

}
