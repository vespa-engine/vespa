// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * <p>This function assign a fixed width bucket to each input value.</p>
 *
 * @author <a href="mailto:havardpe@yahoo-inc.com">Haavard Pettersen</a>
 * @author Simon Thoresen Hult
 */
public class TimeStampFunctionNode extends UnaryFunctionNode {

    public static enum TimePart {
        Year(0),
        Month(1),
        MonthDay(2),
        WeekDay(3),
        Hour(4),
        Minute(5),
        Second(6),
        YearDay(7),
        IsDST(8);

        private final int id;

        private TimePart(int id) {
            this.id = id;
        }

        private static TimePart valueOf(int id) {
            for (TimePart part : values()) {
                if (id == part.id) {
                    return part;
                }
            }
            return null;
        }
    }

    public static final int classId = registerClass(0x4000 + 75, TimeStampFunctionNode.class, TimeStampFunctionNode::new);
    private TimePart timePart = TimePart.Year;
    private boolean isGmt = false;

    public TimeStampFunctionNode() {}

    /**
     * <p>Create a bucket expression with the given width and the given subexpression.</p>
     *
     * @param arg  The argument for this function.
     * @param part The part of time to retrieve.
     * @param gmt  Whether or not to treat time as GMT.
     */
    public TimeStampFunctionNode(ExpressionNode arg, TimePart part, boolean gmt) {
        addArg(arg);
        timePart = part;
        isGmt = gmt;
    }

    public TimePart getTimePart() {
        return timePart;
    }

    public boolean isGmt() {
        return isGmt;
    }

    public boolean isLocal() {
        return !isGmt;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        buf.putByte(null, (byte)(timePart.id | (isGmt ? 0x80 : 0)));
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        int b = buf.getByte(null);
        timePart = TimePart.valueOf(b & 0x7f);
        isGmt = (b & 0x80) != 0;
    }

    @Override
    protected boolean equalsUnaryFunction(UnaryFunctionNode obj) {
        TimeStampFunctionNode rhs = (TimeStampFunctionNode)obj;
        return timePart == rhs.timePart && isGmt == rhs.isGmt;
    }

    @Override
    public TimeStampFunctionNode clone() {
        TimeStampFunctionNode obj = (TimeStampFunctionNode)super.clone();
        obj.timePart = timePart;
        obj.isGmt = isGmt;
        return obj;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("timepart", timePart);
        visitor.visit("islocal", isGmt);
    }
}
