// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "node.h"
#include "queryvisitor.h"

namespace search::query {

class TrueQueryNode : public Node {
    ~TrueQueryNode();
    void accept(QueryVisitor &visitor) override { visitor.visit(*this); }
};

class FalseQueryNode : public Node {
    ~FalseQueryNode();
    void accept(QueryVisitor &visitor) override { visitor.visit(*this); }
};

}
