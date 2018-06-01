// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    template <class TermNode>
    void visitTerm(TermNode &) {  }

    void visit(ProtonNodeTypes::SameElement &n) override {
        if (n.getView().empty()) return;

        vespalib::string prefix = n.getView() + ".";
        for (auto & child : n.getChildren()) {
            search::query::StringBase * term  = static_cast<search::query::StringBase *>(child);
            const vespalib::string & index = term->getView();
            if (index.find(prefix) != 0) { // This can be removed when qrs does not prefix the sameelemnt children
                term->setView(prefix + index);
            }
        }
    }
};

}

