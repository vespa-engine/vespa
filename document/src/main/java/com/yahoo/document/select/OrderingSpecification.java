// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select;

public class OrderingSpecification {
    public static int ASCENDING = 0;
    public static int DESCENDING = 1;

    public final int order;
    public final long orderingStart;
    public final short widthBits;
    public final short divisionBits;

    public OrderingSpecification() {
        this(ASCENDING, (long)0, (short)0, (short)0);
    }

    public OrderingSpecification(int order) {
        this(order, (long)0, (short)0, (short)0);
    }

    public OrderingSpecification(int order, long orderingStart, short widthBits, short divisionBits) {
        this.order = order;
        this.orderingStart = orderingStart;
        this.widthBits = widthBits;
        this.divisionBits = divisionBits;
    }

    public int getOrder() { return order; }
    public long getOrderingStart() { return orderingStart; }
    public short getWidthBits() { return widthBits; }
    public short getDivisionBits() { return divisionBits; }

    @Override
    public boolean equals(Object other) {
        OrderingSpecification o = (OrderingSpecification)other;
        if (o == null) return false;

        return (order == o.order && orderingStart == o.orderingStart && widthBits == o.widthBits && divisionBits == o.divisionBits);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(order, orderingStart, widthBits, divisionBits);
    }

    public String toString() {
        return "O: " + order + " S:" + orderingStart + " W:" + widthBits + " D:" + divisionBits;
    }
}
