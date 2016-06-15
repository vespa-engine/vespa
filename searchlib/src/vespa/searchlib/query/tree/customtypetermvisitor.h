// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/query/tree/customtypevisitor.h>
#include <vespa/searchlib/query/tree/intermediate.h>

namespace search {
namespace query {

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
    virtual void visit(typename NodeTypes::And &n) { visitChildren(n); }
    virtual void visit(typename NodeTypes::AndNot &n) { visitChildren(n); }
    virtual void visit(typename NodeTypes::Equiv &n) { visitChildren(n); }
    virtual void visit(typename NodeTypes::Near &n) { visitChildren(n); }
    virtual void visit(typename NodeTypes::ONear &n) { visitChildren(n); }
    virtual void visit(typename NodeTypes::Or &n) { visitChildren(n); }
    virtual void visit(typename NodeTypes::Rank &n) { visitChildren(n); }
    virtual void visit(typename NodeTypes::WeakAnd &n) { visitChildren(n); }

    // phrases and weighted set terms are conceptual leaf nodes and
    // should be handled that way.
};

}  // namespace query
}  // namespace search

