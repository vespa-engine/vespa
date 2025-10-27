// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "termdataextractor.h"
#include "querynodes.h"
#include <vespa/searchlib/query/tree/templatetermvisitor.h>
#include <vespa/searchlib/queryeval/same_element_flags.h>

using search::fef::ITermData;
using search::query::Node;
using search::query::TemplateTermVisitor;
using search::queryeval::SameElementFlags;
using std::vector;

namespace proton::matching {

namespace {
class TermDataExtractorVisitor
    : public TemplateTermVisitor<TermDataExtractorVisitor, ProtonNodeTypes>
{
    vector<const ITermData *> &_term_data;

public:
    explicit TermDataExtractorVisitor(vector<const ITermData *> &term_data) noexcept
        : _term_data(term_data) {
    }

    template <class TermType>
    void visitTerm(TermType &n) {
        if (n.isRanked()) {
            _term_data.push_back(&n);
        }
    }

    void visit(ProtonNodeTypes::AndNot &n) override {
        assert(n.getChildren().size() > 0);
        n.getChildren()[0]->accept(*this);
    }

    void visit(ProtonNodeTypes::Near &n) override {
        const auto &list = n.getChildren();
        size_t cnt = list.size() - std::min(n.num_negative_terms(), list.size());
        for (size_t i = 0; i < cnt; ++i) {
            list[i]->accept(*this);
        }
    }

    void visit(ProtonNodeTypes::ONear &n) override {
        const auto &list = n.getChildren();
        size_t cnt = list.size() - std::min(n.num_negative_terms(), list.size());
        for (size_t i = 0; i < cnt; ++i) {
            list[i]->accept(*this);
        }
    }

    void visit(ProtonNodeTypes::Equiv &n) override {
        // XXX: unranked equiv not supported
        _term_data.push_back(&n);
    }

    void visit(ProtonNodeTypes::SameElement &n) override {
        if (n.expose_match_data_for_same_element) {
            visitTerm(n);
        }
        if (SameElementFlags::expose_descendants()) {
            visitChildren(n);
        }
    }

};
}  // namespace

void TermDataExtractor::extractTerms(const Node &node,
                                     vector<const ITermData *> &term_data) {
    TermDataExtractorVisitor visitor(term_data);
    // The visitor doesn't deal with const nodes. However, we are
    // not changing the node, so we can safely remove the const.
    const_cast<Node &>(node).accept(visitor);
}

}
