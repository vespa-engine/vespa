// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * Visitor interface used to resolve the underlying type of a value
 * represented by an Inspector.
 */
public interface Visitor {

    /**
     * Called when the visited Inspector is not valid.
     */
    void visitInvalid();
    void visitNix();
    void visitBool(boolean bit);
    void visitLong(long l);
    void visitDouble(double d);
    void visitString(String str);
    void visitString(byte[] utf8);
    void visitData(byte[] data);
    void visitArray(Inspector arr);
    void visitObject(Inspector obj);
}
