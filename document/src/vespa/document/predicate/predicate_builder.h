// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "predicate_slime_visitor.h"
#include <memory>
#include <vector>

namespace document {
struct PredicateNode;

class PredicateBuilder : private PredicateSlimeVisitor {
    std::vector<PredicateNode *>_nodes;

    void visitFeatureSet(const Inspector &i) override;
    void visitFeatureRange(const Inspector &i) override;
    void visitNegation(const Inspector &i) override;
    void visitConjunction(const Inspector &i) override;
    void visitDisjunction(const Inspector &i) override;
    void visitTrue(const Inspector &i) override;
    void visitFalse(const Inspector &i) override;

public:
    std::unique_ptr<PredicateNode> build(const Inspector &i);
};

}  // namespace document

