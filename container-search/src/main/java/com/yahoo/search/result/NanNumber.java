// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

/**
 * A class representing unset or undefined numeric values.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
@SuppressWarnings("serial")
public final class NanNumber extends Number {
    public static final NanNumber NaN = new NanNumber();

    private NanNumber() {
    }

    @Override
    public double doubleValue() {
        return Double.NaN;
    }

    @Override
    public float floatValue() {
        return Float.NaN;
    }

    @Override
    public int intValue() {
        return 0;
    }

    @Override
    public long longValue() {
        return 0L;
    }

    @Override
    public String toString() {
        return "";
    }

}
