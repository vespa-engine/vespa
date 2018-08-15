// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

import java.util.Arrays;

/**
 * @author Simon Thoresen Hult
 */
class ResultId {

    private final int[] indexes;
    private final int hashCode;

    private ResultId(int[] indexes) {
        this.indexes = indexes;
        this.hashCode = Arrays.hashCode(indexes);
    }

    public boolean startsWith(int... prefix) {
        if (prefix.length > indexes.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; ++i) {
            if (prefix[i] != indexes[i]) {
                return false;
            }
        }
        return true;
    }

    public ResultId newChildId(int childIdx) {
        int[] arr = Arrays.copyOf(indexes, indexes.length + 1);
        arr[indexes.length] = childIdx;
        return new ResultId(arr);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ResultId && Arrays.equals(indexes, ((ResultId)obj).indexes);
    }

    @Override
    public String toString() {
        return Arrays.toString(indexes);
    }

    public void encode(IntegerEncoder out) {
        out.append(indexes.length);
        for (int i : indexes) {
            out.append(i);
        }
    }

    public static ResultId decode(IntegerDecoder in) {
        int len = in.next();
        int[] arr = new int[len];
        for (int i = 0; i < len; ++i) {
            arr[i] = in.next();
        }
        return new ResultId(arr);
    }

    public static ResultId valueOf(int... indexes) {
        return new ResultId(Arrays.copyOf(indexes, indexes.length));
    }
}
