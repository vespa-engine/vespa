// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * This function is a request to use the Unicode Collation Algorithm specification when sorting this field.
 *
 * @author baldersheim
 */
public class UcaFunctionNode extends UnaryFunctionNode {

    public static final int classId = registerClass(0x4000 + 140, UcaFunctionNode.class, UcaFunctionNode::new);
    private String locale = "en-US";
    private String strength = "TERTIARY";

    public UcaFunctionNode() {}

    /**
     * Create an UCA node with a specific locale.
     *
     * @param arg    The argument for this function.
     * @param locale The locale to use.
     */
    public UcaFunctionNode(ExpressionNode arg, String locale) {
        this(arg, locale, "TERTIARY");
    }

    /**
     * Create an UCA node with a specific locale and strength setting.
     *
     * @param arg      The argument for this function.
     * @param locale   The locale to use.
     * @param strength The strength setting to use.
     */
    public UcaFunctionNode(ExpressionNode arg, String locale, String strength) {
        addArg(arg);
        this.locale = locale;
        this.strength = strength;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        putUtf8(buf, locale);
        putUtf8(buf, strength);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        locale = getUtf8(buf);
        strength = getUtf8(buf);
    }

    @Override
    protected boolean equalsUnaryFunction(UnaryFunctionNode obj) {
        return true;
    }

    @Override
    public UcaFunctionNode clone() {
        return (UcaFunctionNode)super.clone();
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("locale", locale);
        visitor.visit("strength", strength);
    }
}
