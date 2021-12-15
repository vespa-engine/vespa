// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select;

import com.yahoo.document.select.rule.ArithmeticNode;
import com.yahoo.document.select.rule.AttributeNode;
import com.yahoo.document.select.rule.ComparisonNode;
import com.yahoo.document.select.rule.DocumentNode;
import com.yahoo.document.select.rule.DocumentTypeNode;
import com.yahoo.document.select.rule.EmbracedNode;
import com.yahoo.document.select.rule.IdNode;
import com.yahoo.document.select.rule.LiteralNode;
import com.yahoo.document.select.rule.LogicNode;
import com.yahoo.document.select.rule.NegationNode;
import com.yahoo.document.select.rule.NowNode;
import com.yahoo.document.select.rule.VariableNode;

/**
 * A visitor of the document selection tree.
 *
 * @author Ulf Lilleengen
 */
public interface Visitor {

    void visit(ArithmeticNode node);
    void visit(AttributeNode node);
    void visit(ComparisonNode node);
    void visit(DocumentNode node);
    void visit(DocumentTypeNode node);
    void visit(EmbracedNode node);
    void visit(IdNode node);
    void visit(LiteralNode node);
    void visit(LogicNode node);
    void visit(NegationNode node);
    void visit(NowNode node);
    void visit(VariableNode node);

}
