// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * Decodes the entries of a map, or array of struct, with fast map search.
 *
 * The node is named "fieldname.lookupname". The backend reads the hidden attribute "fieldname$lookupname",
 * and decodes each entry into a string on the form key + separator + value, where the separator is ":" unless set.
 * The name and separator are serialized, while the backend finds the key and value types itself.
 *
 * @author arnej
 */
public class DecodeFastMapLookupNode extends FunctionNode {

    public static final int classId = registerClass(0x4000 + 185, DecodeFastMapLookupNode.class, DecodeFastMapLookupNode::new);
    public static final String DEFAULT_SEPARATOR = ":";
    private String name;
    private String separator = DEFAULT_SEPARATOR;

    /**
     * Constructs an empty node. <b>NOTE:</b> This instance is broken until non-optional member data is set.
     */
    public DecodeFastMapLookupNode() { }

    /**
     * Constructs a node decoding the given fast map lookup.
     *
     * @param name the fast map lookup, on the form "fieldname.lookupname"
     */
    public DecodeFastMapLookupNode(String name) {
        this(name, DEFAULT_SEPARATOR);
    }

    /**
     * Constructs a node decoding the given fast map lookup, with the given separator between key and value.
     *
     * @param name      the fast map lookup, on the form "fieldname.lookupname"
     * @param separator the separator between key and value in the decoded entries
     */
    public DecodeFastMapLookupNode(String name, String separator) {
        if (name == null) {
            throw new IllegalArgumentException("Fast map lookup name cannot be null.");
        }
        this.name = name;
        setSeparator(separator);
    }

    /** Returns the name of the fast map lookup, on the form "fieldname.lookupname". */
    public String getName() {
        return name;
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
    public DecodeFastMapLookupNode setSeparator(String separator) {
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
        putUtf8(buf, name);
        putUtf8(buf, separator);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        name = getUtf8(buf);
        separator = getUtf8(buf);
    }

    @Override
    public int hashCode() {
        return super.hashCode() + name.hashCode() + separator.hashCode();
    }

    @Override
    protected boolean equalsFunction(FunctionNode obj) {
        DecodeFastMapLookupNode rhs = (DecodeFastMapLookupNode)obj;
        return name.equals(rhs.name) && separator.equals(rhs.separator);
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("name", name);
        visitor.visit("separator", separator);
    }

}
