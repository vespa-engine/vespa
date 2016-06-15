// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yst.libmlr.converter.entity;


public class Tree {

    private String id;
    private String comment;

    private InternalNode root;
    private int nInternalNodes; // number of internal nodes


    public Tree() {}

    public Tree(String id, String comment) {
        this.id = id;
        this.comment = comment;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getComment() {
        return (comment == null ? "" : comment);
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public InternalNode getRoot() {
        return root;
    }

    public void setRoot(InternalNode root) {
        this.root = root;
    }

    public int getNumInternalNodes() {
        return nInternalNodes;
    }

    public void incrInteralNodes() {
        nInternalNodes++;
    }

    public void setNumInternalNodes(int n) {
        nInternalNodes = n;
    }

}
