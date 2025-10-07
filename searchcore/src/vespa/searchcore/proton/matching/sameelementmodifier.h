// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "querynodes.h"
#include <vespa/searchlib/query/tree/templatetermvisitor.h>

namespace proton::matching {

/**
 * Prefix the indexname of the terms under the SameElement node.
 *
 */
class SameElementModifier : public search::query::TemplateTermVisitor<SameElementModifier, ProtonNodeTypes>
{
public:
    SameElementModifier();
    ~SameElementModifier() override;
    template <class TermNode>
    void visitTerm(TermNode &) {  }
    void visit(ProtonNodeTypes::SameElement &n) override;
    static bool can_hide_match_data_for_same_element;
};

}

