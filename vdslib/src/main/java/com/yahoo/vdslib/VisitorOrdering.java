// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib;

public class VisitorOrdering {
    public static int ASCENDING = 0;
    public static int DESCENDING = 1;

    public int order;
    public long orderingStart;
    public short widthBits;
    public short divisionBits;

    public VisitorOrdering() {
        this(ASCENDING, (long)0, (short)0, (short)0);
    }

    public VisitorOrdering(int order) {
        this(order, (long)0, (short)0, (short)0);
    }

    public VisitorOrdering(int order, long orderingStart, short widthBits, short divisionBits) {
        this.order = order;
        this.orderingStart = orderingStart;
        this.widthBits = widthBits;
        this.divisionBits = divisionBits;
    }

    public int getOrder() { return order; }
    public long getOrderingStart() { return orderingStart; }
    public short getWidthBits() { return widthBits; }
    public short getDivisionBits() { return divisionBits; }

    public String toString() {
        String out = (order == ASCENDING ? "+" : "-") +
                     "," + widthBits +
                     "," + divisionBits +
                     "," + orderingStart;
        return out;
    }
}
