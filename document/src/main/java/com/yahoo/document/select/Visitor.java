// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select;

import com.yahoo.document.select.rule.*;

/**
 * This interface can be used to create custom visitors for the selection tree.
 *
 * @author <a href="mailto:lulf@yahoo-inc.com">Ulf Lilleengen</a>
 */

public interface Visitor {
    public void visit(ArithmeticNode node);
    public void visit(AttributeNode node);
    public void visit(ComparisonNode node);
    public void visit(DocumentNode node);
    public void visit(EmbracedNode node);
    public void visit(IdNode node);
    public void visit(LiteralNode node);
    public void visit(LogicNode node);
    public void visit(NegationNode node);
    public void visit(NowNode node);
    public void visit(SearchColumnNode node);
    public void visit(VariableNode node);
}
