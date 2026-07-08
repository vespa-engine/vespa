// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.List;

/**
 * Geo distance from a position attribute to a point, in km or miles.
 *
 * @author johsol
 */
public class GeoDistanceFunction extends FunctionNode {

    private final String unit;

    /**
     * Constructs a new instance of this class.
     */
    public GeoDistanceFunction(AttributeFunction pos, GroupingExpression lat,
                               GroupingExpression lon, String unit) {
        this(null, null, pos, lat, lon, unit);
    }

    private GeoDistanceFunction(String label, Integer level, AttributeFunction pos,
                                GroupingExpression lat, GroupingExpression lon, String unit) {
        super("geo_distance", label, level, List.of(pos, lat, lon));
        this.unit = unit;
    }

    /** Returns the output unit. */
    public String getUnit() {
        return unit;
    }

    @Override
    public String toString() {
        return super.toString() + "." + unit;
    }

    @Override
    public GeoDistanceFunction copy() {
        return new GeoDistanceFunction(getLabel(), getLevelOrNull(),
                (AttributeFunction) getArg(0).copy(), getArg(1).copy(), getArg(2).copy(), unit);
    }

}
