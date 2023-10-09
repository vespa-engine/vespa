// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "predicate.h"
#include "predicate_slime_visitor.h"
#include <vespa/vespalib/data/slime/inspector.h>

using vespalib::slime::Inspector;

namespace document {

void PredicateSlimeVisitor::visit(const Inspector &inspector) {
    switch (inspector[Predicate::NODE_TYPE].asLong()) {
    case Predicate::TYPE_CONJUNCTION: visitConjunction(inspector); break;
    case Predicate::TYPE_DISJUNCTION: visitDisjunction(inspector); break;
    case Predicate::TYPE_NEGATION: visitNegation(inspector); break;
    case Predicate::TYPE_FEATURE_SET: visitFeatureSet(inspector); break;
    case Predicate::TYPE_FEATURE_RANGE: visitFeatureRange(inspector); break;
    case Predicate::TYPE_TRUE: visitTrue(inspector); break;
    case Predicate::TYPE_FALSE: visitFalse(inspector); break;
    default: break;
    }
}

void PredicateSlimeVisitor::visitChildren(const Inspector &inspector) {
    for (size_t i = 0; i < inspector[Predicate::CHILDREN].children(); ++i) {
        visit(inspector[Predicate::CHILDREN][i]);
    }
}

}  // namespace document
