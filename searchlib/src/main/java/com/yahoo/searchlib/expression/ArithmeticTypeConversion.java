// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import java.util.HashMap;
import java.util.Map;

/**
 * This class implements a lookup table for result node type conversion.
 *
 * @author Ulf Lilleengen
 */
public class ArithmeticTypeConversion {
    private static final Map<Integer, Map<Integer, Integer>> types = new HashMap<Integer, Map<Integer, Integer>>();

    static {
        add(IntegerResultNode.classId, IntegerResultNode.classId, IntegerResultNode.classId);
        add(IntegerResultNode.classId, FloatResultNode.classId, FloatResultNode.classId);
        add(IntegerResultNode.classId, StringResultNode.classId, IntegerResultNode.classId);
        add(IntegerResultNode.classId, RawResultNode.classId, IntegerResultNode.classId);
        add(FloatResultNode.classId, IntegerResultNode.classId, FloatResultNode.classId);
        add(FloatResultNode.classId, FloatResultNode.classId, FloatResultNode.classId);
        add(FloatResultNode.classId, StringResultNode.classId, FloatResultNode.classId);
        add(FloatResultNode.classId, RawResultNode.classId, FloatResultNode.classId);
        add(StringResultNode.classId, IntegerResultNode.classId, IntegerResultNode.classId);
        add(StringResultNode.classId, FloatResultNode.classId, FloatResultNode.classId);
        add(StringResultNode.classId, StringResultNode.classId, StringResultNode.classId);
        add(StringResultNode.classId, RawResultNode.classId, StringResultNode.classId);
        add(RawResultNode.classId, IntegerResultNode.classId, IntegerResultNode.classId);
        add(RawResultNode.classId, FloatResultNode.classId, FloatResultNode.classId);
        add(RawResultNode.classId, StringResultNode.classId, StringResultNode.classId);
        add(RawResultNode.classId, RawResultNode.classId, RawResultNode.classId);
    }

    private static void add(int a, int b, int c) {
        Map<Integer, Integer> entry;
        if (types.containsKey(a)) {
            entry = types.get(a);
        } else {
            entry = new HashMap<Integer, Integer>();
        }
        entry.put(b, c);
        types.put(a, entry);
    }

    public static ResultNode getType(ResultNode arg) {
        return (ResultNode)ResultNode.createFromId(getBaseType(arg));
    }

    public static ResultNode getType(ResultNode arg1, ResultNode arg2) {
        return (ResultNode)ResultNode.createFromId(types.get(getBaseType(arg1)).get(getBaseType(arg2)));
    }

    public static int getBaseType(ResultNode arg) {
        if (arg instanceof IntegerResultNode) {
            return IntegerResultNode.classId;
        } else if (arg instanceof FloatResultNode) {
            return FloatResultNode.classId;
        } else if (arg instanceof StringResultNode) {
            return StringResultNode.classId;
        } else if (arg instanceof RawResultNode) {
            return RawResultNode.classId;
        } else {
            return ResultNode.classId;
        }
    }
}
