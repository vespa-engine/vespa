// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yst.libmlr.converter.entity;

public enum Operator {
    EQ("eq"),
    NEQ("neq"),
    GT("gt"),
    GEQ("geq"),
    LT("lt"),
    LEQ("leq");

    private final String id;

    Operator(String id) {
        this.id = id;
    }

    public static Operator parse(String str) {
        for (Operator op : Operator.values()) {
            if (op.id.equals(str))
                return op;
        }
        throw new IllegalArgumentException();
    }

    public String getId() {
        return id;
    }

    public static void main(String[] args) {
        Operator op = Operator.parse("gt");
        System.out.println("operator.toString = " + op.toString());
        System.out.println("operator = " + op.getId());
    }

}
