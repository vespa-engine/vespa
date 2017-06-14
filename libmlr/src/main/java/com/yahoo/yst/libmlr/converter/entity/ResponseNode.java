// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yst.libmlr.converter.entity;

public class ResponseNode extends TreeNode {

    private double response;

    public ResponseNode(String i, String c, double r) {
        super(i, c);
        response = r;
    }

    public double getResponse() {
        return response;
    }

    public void setResponse(double response) {
        this.response = response;
    }

}
