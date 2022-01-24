// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.document.internal;

import com.yahoo.document.DataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.Field;
import com.yahoo.document.StructDataType;
import com.yahoo.document.datatypes.Struct;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * @author arnej
 **/
public final class GeoPosType extends StructDataType {

    private final boolean useV8json;
    private static final Field F_X = new Field("x", DataType.INT);
    private static final Field F_Y = new Field("y", DataType.INT);

    public GeoPosType(int vespaVersion) {
        super("position");
        this.useV8json = (vespaVersion == 8);
        assert(vespaVersion > 6);
        assert(vespaVersion < 9);
        addField(F_X);
        addField(F_Y);
    }

    public boolean renderJsonAsVespa8() {
        return this.useV8json;
    }

    public double getLatitude(Struct pos) {
        assert(pos.getDataType() == this);
        double ns = PositionDataType.getYValue(pos).getInteger() * 1.0e-6;
        return ns;
    }

    public double getLongitude(Struct pos) {
        assert(pos.getDataType() == this);
        double ew = PositionDataType.getXValue(pos).getInteger() * 1.0e-6;
        return ew;
    }

    private static final DecimalFormat degreeFmt;

    static {
        degreeFmt = new DecimalFormat("0.0#####", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        degreeFmt.setMinimumIntegerDigits(1);
        degreeFmt.setMinimumFractionDigits(1);
        degreeFmt.setMaximumFractionDigits(6);
    }

    static String fmtD(double degrees) {
        return degreeFmt.format(degrees);
    }

    public String fmtLatitude(Struct pos) {
        assert(pos.getDataType() == this);
        double ns = PositionDataType.getYValue(pos).getInteger() * 1.0e-6;
        return fmtD(ns);
    }

    public String fmtLongitude(Struct pos) {
        assert(pos.getDataType() == this);
        double ew = PositionDataType.getXValue(pos).getInteger() * 1.0e-6;
        return fmtD(ew);
    }

}
