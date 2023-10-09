// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "predicate.h"
#include "predicate_builder.h"
#include <vespa/vespalib/data/slime/inspector.h>
#include <cassert>

using vespalib::slime::Inspector;

namespace document {

void PredicateBuilder::visitFeatureSet(const Inspector &i) {
    _nodes.push_back(new FeatureSet(i));
}

void PredicateBuilder::visitFeatureRange(const Inspector &i) {
    _nodes.push_back(new FeatureRange(i));
}

void PredicateBuilder::visitNegation(const Inspector &i) {
    visitChildren(i);
    _nodes.back() = new Negation(PredicateNode::UP(_nodes.back()));
}

void PredicateBuilder::visitConjunction(const Inspector &i) {
    std::vector<PredicateNode *> nodes;
    nodes.swap(_nodes);
    visitChildren(i);
    nodes.push_back(new Conjunction(_nodes));
    nodes.swap(_nodes);
}

void PredicateBuilder::visitDisjunction(const Inspector &i) {
    std::vector<PredicateNode *> nodes;
    nodes.swap(_nodes);
    visitChildren(i);
    nodes.push_back(new Disjunction(_nodes));
    nodes.swap(_nodes);
}

void PredicateBuilder::visitTrue(const Inspector &) {
    _nodes.push_back(new TruePredicate);
}

void PredicateBuilder::visitFalse(const Inspector &) {
    _nodes.push_back(new FalsePredicate);
}

std::unique_ptr<PredicateNode>
PredicateBuilder::build(const vespalib::slime::Inspector &i) {
    visit(i);
    assert(_nodes.size() == 1);
    return std::unique_ptr<PredicateNode>(_nodes.front());
}

}  // namespace document
