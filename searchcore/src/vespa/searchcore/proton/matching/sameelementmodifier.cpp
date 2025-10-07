// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sameelementmodifier.h"
#include <vespa/vespalib/util/classname.h>
#include <vespa/log/log.h>
LOG_SETUP(".matching.sameelementmodifier");

using search::query::Term;

namespace proton::matching {

class SameElementDescendantModifier : public search::query::TemplateTermVisitor<SameElementDescendantModifier, ProtonNodeTypes>
{
    const std::string& _same_element_view;
    bool _expose_match_data_for_same_element;
public:
    SameElementDescendantModifier(const std::string& same_element_view);
    ~SameElementDescendantModifier() override;
    void visit_term(Term& term);
    template <class TermNode>
    void visitTerm(TermNode &n) { visit_term(n); }
    bool expose_match_data_for_same_element() const noexcept { return _expose_match_data_for_same_element; }
};


SameElementDescendantModifier::SameElementDescendantModifier(const std::string& same_element_view)
    : search::query::TemplateTermVisitor<SameElementDescendantModifier, ProtonNodeTypes>(),
      _same_element_view(same_element_view),
      _expose_match_data_for_same_element(true)
{
}

SameElementDescendantModifier::~SameElementDescendantModifier() = default;

void
SameElementDescendantModifier::visit_term(Term& term)
{
    if (term.getView().empty()) {
        term.setView(_same_element_view);
        if (term.isRanked() && SameElementModifier::can_hide_match_data_for_same_element) {
            _expose_match_data_for_same_element = false;
        }
    } else {
        term.setView(_same_element_view + "." + term.getView());
    }
}

// TODO: Set to true when features for descendants of sameElement are exposed.
bool SameElementModifier::can_hide_match_data_for_same_element = false;

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
