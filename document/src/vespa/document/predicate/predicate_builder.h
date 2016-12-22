// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "predicate_slime_visitor.h"
#include <memory>
#include <vector>

namespace document {
class PredicateNode;

class PredicateBuilder : private PredicateSlimeVisitor {
    std::vector<PredicateNode *>_nodes;

    virtual void visitFeatureSet(const vespalib::slime::Inspector &i);
    virtual void visitFeatureRange(const vespalib::slime::Inspector &i);
    virtual void visitNegation(const vespalib::slime::Inspector &i);
    virtual void visitConjunction(const vespalib::slime::Inspector &i);
    virtual void visitDisjunction(const vespalib::slime::Inspector &i);
    virtual void visitTrue(const vespalib::slime::Inspector &i);
    virtual void visitFalse(const vespalib::slime::Inspector &i);

public:
    std::unique_ptr<PredicateNode> build(const vespalib::slime::Inspector &i);
};

}  // namespace document

