// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "customtypevisitor.h"
#include "intermediate.h"

namespace search::query {

template <class NodeTypes>
class CustomTypeTermVisitor : public CustomTypeVisitor<NodeTypes>
{
protected:
    void visitChildren(Intermediate &n) {
        for (size_t i = 0; i < n.getChildren().size(); ++i) {
            n.getChildren()[i]->accept(*this);
        }
    }

private:
    void visit(typename NodeTypes::And &n) override { visitChildren(n); }
    void visit(typename NodeTypes::AndNot &n) override { visitChildren(n); }
    void visit(typename NodeTypes::Equiv &n) override { visitChildren(n); }
    void visit(typename NodeTypes::Near &n) override { visitChildren(n); }
    void visit(typename NodeTypes::ONear &n) override { visitChildren(n); }
    void visit(typename NodeTypes::Or &n) override { visitChildren(n); }
    void visit(typename NodeTypes::Rank &n) override { visitChildren(n); }
    void visit(typename NodeTypes::WeakAnd &n) override { visitChildren(n); }
    void visit(typename NodeTypes::SameElement &n) override { visitChildren(n); }

    // phrases and weighted set terms are conceptual leaf nodes and
    // should be handled that way.
};

}
