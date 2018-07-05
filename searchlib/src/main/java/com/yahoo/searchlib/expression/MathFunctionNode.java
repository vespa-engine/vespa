// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * This function is an instruction to negate its argument.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class MathFunctionNode extends MultiArgFunctionNode {

    // Make sure these match the definition in c++ searchlib/src/searchlib/expression/mathfunctionnode.h.
    public static enum Function {
        EXP(0),
        POW(1),
        LOG(2),
        LOG1P(3),
        LOG10(4),
        SIN(5),
        ASIN(6),
        COS(7),
        ACOS(8),
        TAN(9),
        ATAN(10),
        SQRT(11),
        SINH(12),
        ASINH(13),
        COSH(14),
        ACOSH(15),
        TANH(16),
        ATANH(17),
        CBRT(18),
        HYPOT(19),
        FLOOR(20);

        private final int id;

        private Function(int id) {
            this.id = id;
        }

        private static Function valueOf(int id) {
            for (Function fnc : values()) {
                if (id == fnc.id) {
                    return fnc;
                }
            }
            return null;
        }
    }

    public static final int classId = registerClass(0x4000 + 136, MathFunctionNode.class);
    private Function fnc;

    @SuppressWarnings("UnusedDeclaration")
    public MathFunctionNode() {
        this(Function.LOG);
    }

    public MathFunctionNode(Function fnc) {
        this(null, fnc);
    }

    public MathFunctionNode(ExpressionNode exp, Function fnc) {
        this.fnc = fnc;
        if (exp != null) {
            addArg(exp);
        }
    }

    @Override
    protected boolean onExecute() {
        getArg(0).execute();
        double result = 0.0;
        switch (fnc) {
        case EXP:
            result = Math.exp(getArg(0).getResult().getFloat());
            break;
        case POW:
            result = Math.pow(getArg(0).getResult().getFloat(), getArg(1).getResult().getFloat());
            break;
        case LOG:
            result = Math.log(getArg(0).getResult().getFloat());
            break;
        case LOG1P:
            result = Math.log1p(getArg(0).getResult().getFloat());
            break;
        case LOG10:
            result = Math.log10(getArg(0).getResult().getFloat());
            break;
        case SIN:
            result = Math.sin(getArg(0).getResult().getFloat());
            break;
        case ASIN:
            result = Math.asin(getArg(0).getResult().getFloat());
            break;
        case COS:
            result = Math.cos(getArg(0).getResult().getFloat());
            break;
        case ACOS:
            result = Math.acos(getArg(0).getResult().getFloat());
            break;
        case TAN:
            result = Math.tan(getArg(0).getResult().getFloat());
            break;
        case ATAN:
            result = Math.atan(getArg(0).getResult().getFloat());
            break;
        case SQRT:
            result = Math.sqrt(getArg(0).getResult().getFloat());
            break;
        case SINH:
            result = Math.sinh(getArg(0).getResult().getFloat());
            break;
        case ASINH:
            throw new IllegalArgumentException("Inverse hyperbolic sine(asinh) is not supported in java");
        case COSH:
            result = Math.cosh(getArg(0).getResult().getFloat());
            break;
        case ACOSH:
            throw new IllegalArgumentException("Inverse hyperbolic cosine (acosh) is not supported in java");
        case TANH:
            result = Math.tanh(getArg(0).getResult().getFloat());
            break;
        case ATANH:
            throw new IllegalArgumentException("Inverse hyperbolic tangents (atanh) is not supported in java");
        case FLOOR:
            result = Math.floor(getArg(0).getResult().getFloat());
            break;
        case CBRT:
            result = Math.cbrt(getArg(0).getResult().getFloat());
            break;
        case HYPOT:
            result = Math.hypot(getArg(0).getResult().getFloat(), getArg(1).getResult().getFloat());
            break;
        }
        ((FloatResultNode)getResult()).setValue(result);
        return true;
    }

    @Override
    public void onPrepareResult() {
        setResult(new FloatResultNode());
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        buf.putByte(null, (byte)fnc.id);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        int b = buf.getByte(null);
        fnc = Function.valueOf(b & 0xff);
    }

    @Override
    protected boolean equalsMultiArgFunction(MultiArgFunctionNode obj) {
        return fnc == ((MathFunctionNode)obj).fnc;
    }

    @Override
    public MathFunctionNode clone() {
        MathFunctionNode obj = (MathFunctionNode)super.clone();
        obj.fnc = fnc;
        return obj;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("function", fnc);
    }
}
