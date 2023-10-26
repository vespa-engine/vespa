// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

import java.util.Objects;

/**
 * This function is an instruction to do a lookup in a map attribute, returning the value.
 *
 * The key is either specified explicitly or found via a key source attribute.
 * Two underlying attributes are used to represent the map attribute (the key and value attributes).
 *
 * @author geirst
 */
public class AttributeMapLookupNode extends AttributeNode {

    public static final int classId = registerClass(0x4000 + 145, AttributeMapLookupNode.class, AttributeMapLookupNode::new);
    private String keyAttribute = "";
    private String valueAttribute = "";
    private String key = "";
    private String keySourceAttribute = "";

    private AttributeMapLookupNode(String attributeExpression, String keyAttribute, String valueAttribute,
                                   String key, String keySourceAttribute) {
        super(attributeExpression);
        this.keyAttribute = keyAttribute;
        this.valueAttribute = valueAttribute;
        this.key = key;
        this.keySourceAttribute = keySourceAttribute;
    }

    public AttributeMapLookupNode() {
    }

    public static AttributeMapLookupNode fromKey(String attributeExpression, String keyAttribute, String valueAttribute, String key) {
        return new AttributeMapLookupNode(attributeExpression, keyAttribute, valueAttribute, key, "");
    }

    public static AttributeMapLookupNode fromKeySourceAttribute(String attributeExpression, String keyAttribute, String valueAttribute, String keySourceAttribute) {
        return new AttributeMapLookupNode(attributeExpression, keyAttribute, valueAttribute, "", keySourceAttribute);
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        putUtf8(buf, keyAttribute);
        putUtf8(buf, valueAttribute);
        putUtf8(buf, key);
        putUtf8(buf, keySourceAttribute);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        keyAttribute = getUtf8(buf);
        valueAttribute = getUtf8(buf);
        key = getUtf8(buf);
        keySourceAttribute = getUtf8(buf);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), keyAttribute, valueAttribute, key, keySourceAttribute);
    }

    @Override
    protected boolean equalsFunction(FunctionNode obj) {
        AttributeMapLookupNode that = (AttributeMapLookupNode) obj;
        return super.equalsFunction(obj) &&
                Objects.equals(keyAttribute, that.keyAttribute) &&
                Objects.equals(valueAttribute, that.valueAttribute) &&
                Objects.equals(key, that.key) &&
                Objects.equals(keySourceAttribute, that.keySourceAttribute);
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("keyAttribute", keyAttribute);
        visitor.visit("valueAttribute", valueAttribute);
        visitor.visit("key", key);
        visitor.visit("keySourceAttribute", keySourceAttribute);
    }
}
