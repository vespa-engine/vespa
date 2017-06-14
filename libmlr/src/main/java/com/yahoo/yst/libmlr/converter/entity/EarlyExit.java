// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yst.libmlr.converter.entity;


public class EarlyExit {
    protected int treeId;
    protected Operator operator;
    protected String value;

    public EarlyExit(int tid, Operator op, String val) {
        treeId = tid;
        operator = op;
        value = val;
    }

    public int getTreeId() {
        return treeId;
    }

    public String getValue() {
        return value;
    }

    public Operator getOperator() {
        return operator;
    }

}
