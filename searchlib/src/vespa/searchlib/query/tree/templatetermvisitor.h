// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/query/tree/customtypetermvisitor.h>

namespace search {
namespace query {

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

    virtual void visit(typename NodeTypes::NumberTerm &n) { myVisit(n); }
    virtual void visit(typename NodeTypes::LocationTerm &n) { myVisit(n); }
    virtual void visit(typename NodeTypes::PrefixTerm &n) { myVisit(n); }
    virtual void visit(typename NodeTypes::RangeTerm &n) { myVisit(n); }
    virtual void visit(typename NodeTypes::StringTerm &n) { myVisit(n); }
    virtual void visit(typename NodeTypes::SubstringTerm &n) { myVisit(n); }
    virtual void visit(typename NodeTypes::SuffixTerm &n) { myVisit(n); }
    virtual void visit(typename NodeTypes::PredicateQuery &n) { myVisit(n); }
    virtual void visit(typename NodeTypes::RegExpTerm &n) { myVisit(n); }

    // Phrases are terms with children. This visitor will not visit
    // the phrase's children, unless this member function is
    // overridden to do so.
    virtual void visit(typename NodeTypes::Phrase &n) { myVisit(n); }

    // WeightedSetTerms are terms with children. This visitor will not visit
    // the weighted set's children, unless this member function is
    // overridden to do so.
    virtual void visit(typename NodeTypes::WeightedSetTerm &n) { myVisit(n); }

    // DotProducts have children. This visitor will not visit the dot
    // product's children, unless this member function is overridden
    // to do so.
    virtual void visit(typename NodeTypes::DotProduct &n) { myVisit(n); }

    // WandTerms have children. This visitor will not visit the wand
    // term's children, unless this member function is overridden
    // to do so.
    virtual void visit(typename NodeTypes::WandTerm &n) { myVisit(n); }
};

}  // namespace query
}  // namespace search

