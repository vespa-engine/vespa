// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sameelementmodifier.h"
#include <vespa/searchlib/queryeval/same_element_flags.h>
#include <vespa/vespalib/util/classname.h>
#include <algorithm>
#include <vespa/log/log.h>
LOG_SETUP(".matching.sameelementmodifier");

using search::query::Node;
using search::query::Term;
using search::queryeval::SameElementFlags;

namespace proton::matching {

class SameElementDescendantModifier : public search::query::TemplateTermVisitor<SameElementDescendantModifier, ProtonNodeTypes>
{
    const std::string& _same_element_view;
    uint32_t                 _hidden_terms;
    bool _expose_match_data_for_same_element;
    bool hidden_terms() const noexcept { return _hidden_terms != 0u; }
    void visit_nearlike_children(const std::vector<Node*>& children, size_t num_negative_terms);
public:
    SameElementDescendantModifier(const std::string& same_element_view);
    ~SameElementDescendantModifier() override;
    void visit_term(Term& term);
    template <class TermNode>
    void visitTerm(TermNode &n) { visit_term(n); }
    void visit(ProtonNodeTypes::AndNot& n) override;
    void visit(ProtonNodeTypes::Near& n) override;
    void visit(ProtonNodeTypes::ONear& n) override;
    bool expose_match_data_for_same_element() const noexcept { return _expose_match_data_for_same_element; }
};


SameElementDescendantModifier::SameElementDescendantModifier(const std::string& same_element_view)
    : search::query::TemplateTermVisitor<SameElementDescendantModifier, ProtonNodeTypes>(),
      _same_element_view(same_element_view),
      _hidden_terms(0u),
      _expose_match_data_for_same_element(true)
{
}

SameElementDescendantModifier::~SameElementDescendantModifier() = default;

void
SameElementDescendantModifier::visit_nearlike_children(const std::vector<Node*>& children, size_t num_negative_terms)
{
    size_t cnt = children.size() - std::min(num_negative_terms, children.size());
    for (size_t i = 0; i < cnt; ++i) {
        children[i]->accept(*this);
    }
    if (cnt < children.size()) {
        ++_hidden_terms;
        for (size_t i = cnt; i < children.size(); ++i) {
            children[i]->accept(*this);
        }
        --_hidden_terms;
    }
}

void
SameElementDescendantModifier::visit_term(Term& term)
{
    if (term.getView().empty()) {
        term.setView(_same_element_view);
        if (term.isRanked() && !hidden_terms() && SameElementFlags::expose_descendants()) {
            _expose_match_data_for_same_element = false;
        }
    } else {
        term.setView(_same_element_view + "." + term.getView());
    }
}

void
SameElementDescendantModifier::visit(ProtonNodeTypes::AndNot& n)
{
    auto& children = n.getChildren();
    if (!children.empty()) {
        children[0]->accept(*this);
        ++_hidden_terms;
        for (uint32_t i = 1; i < children.size(); ++i) {
            children[i]->accept(*this);
        }
        --_hidden_terms;
    }
}

void
SameElementDescendantModifier::visit(ProtonNodeTypes::Near &n)
{
    visit_nearlike_children(n.getChildren(), n.num_negative_terms());
}

void
SameElementDescendantModifier::visit(ProtonNodeTypes::ONear &n)
{
    visit_nearlike_children(n.getChildren(), n.num_negative_terms());
}

SameElementModifier::SameElementModifier()
    : search::query::TemplateTermVisitor<SameElementModifier, ProtonNodeTypes>()
{
}

SameElementModifier::~SameElementModifier() = default;

void
SameElementModifier::visit(ProtonNodeTypes::SameElement &n)
{
    if (n.getView().empty()) {
        return;
    }
    SameElementDescendantModifier descendant_modifier(n.getView());
    for (search::query::Node * child : n.getChildren()) {
        child->accept(descendant_modifier);
    }
    n.expose_match_data_for_same_element = descendant_modifier.expose_match_data_for_same_element();
}

}
