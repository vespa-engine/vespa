// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yst.libmlr.converter.entity;

public class TreeNode {

    private String id;
    private String comment;
    private int idx;

    public TreeNode(String i, String c) {
        id = i;
        comment = c;
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

    public int getIndex() {
        return idx;
    }

    public void setIndex(int idx) {
        this.idx = idx;
    }

}
