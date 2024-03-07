package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.ObjectVisitor;

/*
 * Streaming search version of InterpolatedLookupNode.
 */
public class InterpolatedDocumentFieldLookupNode extends InterpolatedLookupNode {
    public static final int classId = registerClass(0x4000 + 166, InterpolatedDocumentFieldLookupNode.class, InterpolatedDocumentFieldLookupNode::new);

    /**
     * Constructs an empty result node.
     * <b>NOTE:</b> This instance is broken until non-optional member data is set.
     */
    public InterpolatedDocumentFieldLookupNode() {
        super();
    }

    /**
     * Constructs an instance of this class with given field name
     * and lookup argument.
     *
     * @param field The field to retrieve.
     * @param arg Expression evaluating to the lookup argument.
     */
    public InterpolatedDocumentFieldLookupNode(String field, ExpressionNode arg) {
        super(field, arg);
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    public String getFieldName() { return super.getAttributeName(); }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitSuperMembers(visitor);
        visitor.visit("field", getFieldName());
    }
}
