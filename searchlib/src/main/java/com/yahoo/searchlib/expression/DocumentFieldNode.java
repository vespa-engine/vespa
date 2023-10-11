// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * The node is a request to retrieve the content of a document field.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class DocumentFieldNode extends DocumentAccessorNode {

    public static final int classId = registerClass(0x4000 + 56, DocumentFieldNode.class, DocumentFieldNode::new);
    private String fieldName;
    private ResultNode result;

    /**
     * Constructs an empty result node. <b>NOTE:</b> This instance is broken until non-optional member data is set.
     */
    public DocumentFieldNode() {
        super();
    }

    /**
     * Constructs an instance of this class with given field name.
     *
     * @param fieldName The field whose value to retrieve.
     */
    public DocumentFieldNode(String fieldName) {
        super();
        setDocumentFieldName(fieldName);
    }

    /**
     * Returns the name of the field whose value to retrieve.
     *
     * @return The field name.
     */
    public String getDocumentFieldName() {
        return fieldName;
    }

    /**
     * Sets the name of the field whose value to retrieve.
     *
     * @param fieldName The field name to set.
     * @return This, to allow chaining.
     */
    public DocumentFieldNode setDocumentFieldName(String fieldName) {
        if (fieldName == null) {
            throw new IllegalArgumentException("Field name can not be null.");
        }
        this.fieldName = fieldName;
        return this;
    }

    @Override
    public ResultNode getResult() {
        return result;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        putUtf8(buf, fieldName);
        serializeOptional(buf, result);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        fieldName = getUtf8(buf);
        result = (ResultNode)deserializeOptional(buf);
    }

    @Override
    public int hashCode() {
        return super.hashCode() + fieldName.hashCode();
    }

    @Override
    protected boolean equalsExpression(ExpressionNode obj) {
        DocumentFieldNode rhs = (DocumentFieldNode)obj;
        if (!fieldName.equals(rhs.fieldName)) {
            return false;
        }
        if (!equals(result, rhs.result)) {
            return false;
        }
        return true;
    }

    @Override
    public DocumentFieldNode clone() {
        DocumentFieldNode obj = (DocumentFieldNode)super.clone();
        if (result != null) {
            obj.result = (ResultNode)result.clone();
        }
        return obj;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("fieldName", fieldName);
        visitor.visit("result", result);
    }
}
