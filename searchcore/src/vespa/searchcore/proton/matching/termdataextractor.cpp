// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "termdataextractor.h"
#include "querynodes.h"
#include <vespa/searchlib/query/tree/templatetermvisitor.h>

using search::fef::ITermData;
using search::query::Node;
using search::query::TemplateTermVisitor;
using std::vector;

namespace proton {
namespace matching {

namespace {
class TermDataExtractorVisitor
    : public TemplateTermVisitor<TermDataExtractorVisitor, ProtonNodeTypes>
{
    vector<const ITermData *> &_term_data;

public:
    TermDataExtractorVisitor(vector<const ITermData *> &term_data)
        : _term_data(term_data) {
    }

    template <class TermType>
    void visitTerm(TermType &n) {
        if (n.isRanked()) {
            _term_data.push_back(&n);
        }
    }

    void visit(ProtonLocationTerm &) override {}

    virtual void visit(ProtonNodeTypes::AndNot &n) override {
        assert(n.getChildren().size() > 0);
        n.getChildren()[0]->accept(*this);
    }

    virtual void visit(ProtonNodeTypes::Equiv &n) override {
        // XXX: unranked equiv not supported
        _term_data.push_back(&n);
    }

    virtual void visit(ProtonNodeTypes::SameElement &) override {}
};
}  // namespace

void TermDataExtractor::extractTerms(const Node &node,
                                     vector<const ITermData *> &term_data) {
    TermDataExtractorVisitor visitor(term_data);
    // The visitor doesn't deal with const nodes. However, we are
    // not changing the node, so we can safely remove the const.
    const_cast<Node &>(node).accept(visitor);
}

}  // namespace matching
}  // namespace proton
