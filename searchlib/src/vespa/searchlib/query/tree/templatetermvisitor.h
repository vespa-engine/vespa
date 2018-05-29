// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "customtypetermvisitor.h"

namespace search::query {

/**
 * Use this class to visit all term nodes by deriving from this class
 * and implementing a single template member function:
 * template <class TermType> void visitTerm(TermType &n);
 *
 * This class uses the curiously recurring template pattern to know
 * its own derived class that has the visitTerm template member
 * function.
 */
template <class Self, class NodeTypes>
class TemplateTermVisitor : public CustomTypeTermVisitor<NodeTypes> {
    template <class TermNode>
    void myVisit(TermNode &n) {
        static_cast<Self &>(*this).template visitTerm(n);
    }

    void visit(typename NodeTypes::NumberTerm &n) override { myVisit(n); }
    void visit(typename NodeTypes::LocationTerm &n) override { myVisit(n); }
    void visit(typename NodeTypes::PrefixTerm &n) override { myVisit(n); }
    void visit(typename NodeTypes::RangeTerm &n) override { myVisit(n); }
    void visit(typename NodeTypes::StringTerm &n) override { myVisit(n); }
    void visit(typename NodeTypes::SubstringTerm &n) override { myVisit(n); }
    void visit(typename NodeTypes::SuffixTerm &n) override { myVisit(n); }
    void visit(typename NodeTypes::PredicateQuery &n) override { myVisit(n); }
    void visit(typename NodeTypes::RegExpTerm &n) override { myVisit(n); }

    // Phrases are terms with children. This visitor will not visit
    // the phrase's children, unless this member function is
    // overridden to do so.
    void visit(typename NodeTypes::Phrase &n) override { myVisit(n); }

    // WeightedSetTerms are terms with children. This visitor will not visit
    // the weighted set's children, unless this member function is
    // overridden to do so.
    void visit(typename NodeTypes::WeightedSetTerm &n) override { myVisit(n); }

    // DotProducts have children. This visitor will not visit the dot
    // product's children, unless this member function is overridden
    // to do so.
    void visit(typename NodeTypes::DotProduct &n) override { myVisit(n); }

    // WandTerms have children. This visitor will not visit the wand
    // term's children, unless this member function is overridden
    // to do so.
    void visit(typename NodeTypes::WandTerm &n) override { myVisit(n); }
};

}
