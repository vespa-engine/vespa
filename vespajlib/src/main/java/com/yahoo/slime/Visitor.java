// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * Visitor interface used to resolve the underlying type of a value
 * represented by an Inspector.
 **/
public interface Visitor {
    /**
     * Called when the visited Inspector is not valid.
     **/
    public void visitInvalid();
    public void visitNix();
    public void visitBool(boolean bit);
    public void visitLong(long l);
    public void visitDouble(double d);
    public void visitString(String str);
    public void visitString(byte[] utf8);
    public void visitData(byte[] data);
    public void visitArray(Inspector arr);
    public void visitObject(Inspector obj);
}
