// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * Position field access for streaming search.
 * <p>
 * Reads pos.x and pos.y from the document and encodes as zcurve integer.
 *
 * @author johsol
 */
public class PositionDocumentFieldNode extends DocumentAccessorNode {

    public static final int classId = registerClass(0x4000 + 182, PositionDocumentFieldNode.class, PositionDocumentFieldNode::new);
    private String fieldName = "";
    private final IntegerResultNode result = new IntegerResultNode(0);

    public PositionDocumentFieldNode() {
    }

    public PositionDocumentFieldNode(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
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
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        fieldName = getUtf8(buf);
    }

    @Override
    public int hashCode() {
        return super.hashCode() + fieldName.hashCode();
    }

    @Override
    protected boolean equalsExpression(ExpressionNode obj) {
        return fieldName.equals(((PositionDocumentFieldNode) obj).fieldName);
    }

    @Override
    public PositionDocumentFieldNode clone() {
        PositionDocumentFieldNode obj = (PositionDocumentFieldNode) super.clone();
        obj.fieldName = fieldName;
        return obj;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("fieldName", fieldName);
    }
}
