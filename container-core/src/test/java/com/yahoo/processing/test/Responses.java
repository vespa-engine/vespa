// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.test;

import com.yahoo.processing.response.Data;
import com.yahoo.processing.response.DataList;

/**
 * Static utilities
 *
 * @author bratseth
 * @since 5.1.13
 */
public class Responses {

    /**
     * Returns a data item as a recursively indented string
     */
    public static String recursiveToString(Data data) {
        StringBuilder b = new StringBuilder();
        asString(data, b, "");
        return b.toString();
    }

    private static void asString(Data data, StringBuilder b, String indent) {
        b.append(indent).append(data).append("\n");
        if (!(data instanceof DataList)) return;
        for (Data childData : ((DataList<? extends Data>) data).asList()) {
            asString(childData, b, indent.concat("    "));
        }
    }

}
