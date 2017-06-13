// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yst.libmlr.converter.entity;

public class FuncPolytransform implements Function {
    /*
     * The following parameters are type String to preserve precision.
     */
    protected String a0;
    protected String a1;
    protected String a2;
    protected String a3;

    public FuncPolytransform() {}

    public String getA0() {
        return a0;
    }

    public void setA0(String a0) {
        this.a0 = a0;
    }

    public String getA1() {
        return a1;
    }

    public void setA1(String a1) {
        this.a1 = a1;
    }

    public String getA2() {
        return a2;
    }

    public void setA2(String a2) {
        this.a2 = a2;
    }

    public String getA3() {
        return a3;
    }

    public void setA3(String a3) {
        this.a3 = a3;
    }

    public boolean validateParams() {
        return (a0 != null && a1 != null && a2 != null && a3 != null);
    }
}
