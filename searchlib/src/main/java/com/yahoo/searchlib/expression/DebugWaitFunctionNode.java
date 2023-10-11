// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * This is a debug wait function node that waits for a specified amount of time before executing its expression.
 *
 * @author Ulf Lilleengen
 */
public class DebugWaitFunctionNode extends UnaryFunctionNode {

    public static final int classId = registerClass(0x4000 + 144, DebugWaitFunctionNode.class, DebugWaitFunctionNode::new);
    private double waitTime;
    private boolean busyWait;

    public DebugWaitFunctionNode() { }

    /**
     * Constructs an instance of this class with given argument and wait parameters.
     *
     * @param arg      The argument for this function.
     * @param waitTime The time to wait before executing expression.
     * @param busyWait true if busy wait, false if not.
     */
    public DebugWaitFunctionNode(ExpressionNode arg, double waitTime, boolean busyWait) {
        addArg(arg);
        this.waitTime = waitTime;
        this.busyWait = busyWait;
    }

    @Override
    public void onPrepare() {
        super.onPrepare();
    }

    @Override
    public boolean onExecute() {
        // TODO: Add wait code.
        double millis = waitTime * 1000.0;
        long start = System.currentTimeMillis();
        try {
            while ((System.currentTimeMillis() - start) < millis) {
                if (busyWait) {
                    for (int i = 0; i < 1000; i++) {
                        ;
                    }
                } else {
                    long rem = (long)(millis - (System.currentTimeMillis() - start));
                    Thread.sleep(rem);
                }
            }
        } catch (InterruptedException ie) {
            // Not critical
        }
        getArg().execute();
        getResult().set(getArg().getResult());
        return true;
    }

    @Override
    public int hashCode() {
        return super.hashCode() + (int)waitTime + (busyWait ? 1 : 0);
    }

    @Override
    protected boolean equalsUnaryFunction(UnaryFunctionNode obj) {
        DebugWaitFunctionNode rhs = (DebugWaitFunctionNode)obj;
        return waitTime == rhs.waitTime && busyWait == rhs.busyWait;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        buf.putDouble(null, waitTime);
        byte tmp = busyWait ? (byte)1 : (byte)0;
        buf.putByte(null, tmp);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        waitTime = buf.getDouble(null);
        byte tmp = buf.getByte(null);
        busyWait = (tmp != 0);
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("waitTime", waitTime);
        visitor.visit("busyWait", busyWait);
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }
}
