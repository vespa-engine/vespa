// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

import java.util.Objects;

/**
 * This function is an instruction to calculate geo distance from a position attribute to a point.
 *
 * @author johsol
 */
public class GeoDistanceFunctionNode extends MultiArgFunctionNode {

    public enum Unit {
        KM(0),
        MILES(1);

        private final int id;

        Unit(int id) { this.id = id; }

        private static Unit valueOf(int id) {
            for (Unit u : values()) {
                if (id == u.id) return u;
            }
            throw new IllegalArgumentException("Unknown unit id: " + id);
        }
    }

    public static final int classId = registerClass(0x4000 + 181, GeoDistanceFunctionNode.class, GeoDistanceFunctionNode::new);

    private Unit unit = Unit.KM;

    public GeoDistanceFunctionNode() {
    }

    public GeoDistanceFunctionNode(ExpressionNode pos, ExpressionNode lat, ExpressionNode lon, Unit unit) {
        Objects.requireNonNull(pos);
        Objects.requireNonNull(lat);
        Objects.requireNonNull(lon);
        Objects.requireNonNull(unit);
        this.unit = unit;
        addArg(pos);
        addArg(lat);
        addArg(lon);
    }

    public Unit getUnit() { return unit; }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        buf.putByte(null, (byte) unit.id);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        unit = Unit.valueOf(buf.getByte(null));
    }

    @Override
    public int hashCode() {
        return super.hashCode() + unit.hashCode();
    }

    @Override
    protected boolean equalsMultiArgFunction(MultiArgFunctionNode obj) {
        GeoDistanceFunctionNode rhs = (GeoDistanceFunctionNode) obj;
        return unit == rhs.unit;
    }

    @Override
    public GeoDistanceFunctionNode clone() {
        GeoDistanceFunctionNode obj = (GeoDistanceFunctionNode) super.clone();
        obj.unit = unit;
        return obj;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("unit", unit);
    }
}
