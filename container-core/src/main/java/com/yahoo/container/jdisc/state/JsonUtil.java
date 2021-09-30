// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

/**
 * @author bjorncs
 */
class JsonUtil {

    private JsonUtil() {}

    static double sanitizeDouble(double value) {
        return (((Double) value).isNaN() || ((Double) value).isInfinite()) ? 0.0 : value;
    }

}
