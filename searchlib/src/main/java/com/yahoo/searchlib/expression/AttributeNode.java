// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * This function is an instruction to retrieve the value of a named attribute.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class AttributeNode extends FunctionNode {

    public static final int classId = registerClass(0x4000 + 55, AttributeNode.class, AttributeNode::new);
    private String attribute;

    /**
     * Constructs an empty result node. <b>NOTE:</b> This instance is broken until non-optional member data is set.
     */
    public AttributeNode() { }

    /**
     * Constructs an instance of this class with given attribute name.
     *
     * @param attribute The attribute to retrieve.
     */
    public AttributeNode(String attribute) {
        setAttributeName(attribute);
    }

    /**
     * Returns the name of the attribute whose value this function is to retrieve.
     *
     * @return The attribute name.
     */
    public String getAttributeName() {
        return attribute;
    }

    /**
     * Sets the name of the attribute whose value this function is to retrieve.
     *
     * @param attribute The attribute to retrieve.
     * @return This, to allow chaining.
     */
    public AttributeNode setAttributeName(String attribute) {
        if (attribute == null) {
            throw new IllegalArgumentException("Attribute name can not be null.");
        }
        this.attribute = attribute;
        return this;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        putUtf8(buf, attribute);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        attribute = getUtf8(buf);
    }

    @Override
    public int hashCode() {
        return super.hashCode() + attribute.hashCode();
    }

    @Override
    protected boolean equalsFunction(FunctionNode obj) {
        return attribute.equals(((AttributeNode)obj).attribute);
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("attribute", attribute);
    }
}
