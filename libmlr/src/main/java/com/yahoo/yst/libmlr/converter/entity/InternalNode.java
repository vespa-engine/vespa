// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yst.libmlr.converter.entity;


public class InternalNode extends TreeNode {

    private String feature;
    private String op;
    private String value;
    private TreeNode left; // true
    private TreeNode right; // false

    public InternalNode(String i, String c, String f, String v, TreeNode lf, TreeNode rt) {
        super(i, c);
        feature = f;
        value = v;
        left = lf;
        right = rt;
    }

    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public TreeNode getLeftNode() {
        return left;
    }

    public void setLeftNode(TreeNode left) {
        this.left = left;
    }

    public TreeNode getRightNode() {
        return right;
    }

    public void setRightNode(TreeNode right) {
        this.right = right;
    }

}
