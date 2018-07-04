// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

/**
 * This abstract expression node represents a document whose content is accessed depending on the subclass
 * implementation of this.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public abstract class DocumentAccessorNode extends ExpressionNode {

    public static final int classId = registerClass(0x4000 + 48, FunctionNode.class);

    @Override
    protected int onGetClassId() {
        return classId;
    }
}
