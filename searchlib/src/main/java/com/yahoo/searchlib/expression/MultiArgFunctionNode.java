// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.*;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>This is an abstract super-class for all functions that accepts multiple arguments. This node implements the
 * necessary API for manipulating arguments.</p>
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public abstract class MultiArgFunctionNode extends FunctionNode {

    public static final int classId = registerClass(0x4000 + 45, MultiArgFunctionNode.class);
    private List<ExpressionNode> args = new ArrayList<ExpressionNode>();

    /**
     * <p>Adds the given argument to this function.</p>
     *
     * @param arg The argument to add.
     * @return This, to allow chaining.
     */
    public MultiArgFunctionNode addArg(ExpressionNode arg) {
        arg.getClass(); // throws NullPointerException
        args.add(arg);
        return this;
    }

    /**
     * <p>Returns the argument at the given index.</p>
     *
     * @param i The index of the argument to return.
     * @return The argument.
     */
    public ExpressionNode getArg(int i) {
        return args.get(i);
    }

    /**
     * <p>Returns the number of arguments this function has.</p>
     *
     * @return The size of the argument list.
     */
    public int getNumArgs() {
        return args.size();
    }

    @Override
    protected boolean onExecute() {
        for (ExpressionNode arg : args) {
            arg.execute();
        }
        return calculate(args, getResult());
    }

    @Override
    protected void onPrepare() {
        for (ExpressionNode arg : args) {
            arg.prepare();
        }
        prepareResult();
    }

    /**
     * <p>Perform the appropriate calculation of the arguments into a result node.</p>
     *
     * @param args   A list of operands.
     * @param result Place to put the result.
     * @return True if successful, false if not.
     */
    private boolean calculate(final List<ExpressionNode> args, ResultNode result) {
        return onCalculate(args, result);
    }

    private void prepareResult() {
        onPrepareResult();
    }

    protected boolean onCalculate(final List<ExpressionNode> args, ResultNode result) {
        result.set(args.get(0).getResult());
        for (int i = 1; i < args.size(); i++) {
            executeIterative(args.get(i).getResult(), result);
        }
        return true;
    }

    protected void onPrepareResult() {
        if (args.size() == 1) {
            setResult(ArithmeticTypeConversion.getType(args.get(0).getResult()));
        } else if (args.size() > 1) {
            setResult((ResultNode)args.get(0).getResult().clone());
            for (int i = 1; i < args.size(); i++) {
                if (args.get(i).getResult() != null) {
                    setResult(ArithmeticTypeConversion.getType(getResult(), args.get(i).getResult()));
                }
            }
        }
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        int numArgs = args.size();
        buf.putInt(null, numArgs);
        for (ExpressionNode node : args) {
            serializeOptional(buf, node); // TODO: Not optional.
        }
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        args.clear();
        int numArgs = buf.getInt(null);
        for (int i = 0; i < numArgs; i++) {
            ExpressionNode node = (ExpressionNode)deserializeOptional(buf); // TODO: Not optional.
            args.add(node);
        }
    }

    @Override
    public int hashCode() {
        int ret = super.hashCode();
        for (ExpressionNode node : args) {
            ret += node.hashCode();
        }
        return ret;
    }

    @Override
    protected final boolean equalsFunction(FunctionNode obj) {
        MultiArgFunctionNode rhs = (MultiArgFunctionNode)obj;
        if (!args.equals(rhs.args)) {
            return false;
        }
        if (!equalsMultiArgFunction(rhs)) {
            return false;
        }
        return true;
    }

    protected abstract boolean equalsMultiArgFunction(MultiArgFunctionNode obj);

    @Override
    public MultiArgFunctionNode clone() {
        MultiArgFunctionNode obj = (MultiArgFunctionNode)super.clone();
        obj.args = new ArrayList<ExpressionNode>();
        for (ExpressionNode node : args) {
            obj.args.add(node.clone());
        }
        return obj;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("args", args);
    }

    @Override
    public void selectMembers(ObjectPredicate predicate, ObjectOperation operation) {
        super.selectMembers(predicate, operation);
        for (ExpressionNode arg : args) {
            arg.select(predicate, operation);
        }
    }
}
