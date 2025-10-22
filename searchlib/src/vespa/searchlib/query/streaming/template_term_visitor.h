// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "term_visitor.h"

namespace search::streaming {

/**
 * Use this class to visit all streaming term nodes by deriving from this class
 * and implementing a single template member function:
 *   template <class TermType> void visitTerm(TermType &n);
 *
 * This class uses the curiously recurring template pattern (CRTP) to know
 * its own derived class that has the visitTerm template member function.
 *
 * Example usage:
 *
 * class MyTermCollector : public TemplateTermVisitor<MyTermCollector> {
 * public:
 *     std::vector<std::string> term_strings;
 *
 *     template <class TermType>
 *     void visitTerm(TermType &term) {
 *         term_strings.push_back(term.getTermString());
 *     }
 * };
 *
 * MyTermCollector collector;
 * query_tree_root->accept(collector);
 * // collector.term_strings now contains all term strings from the query
 */
template <class Self>
class TemplateTermVisitor : public TermVisitor {
    template <class TermNode>
    void myVisit(TermNode &n) {
        static_cast<Self &>(*this).visitTerm(n);
    }

protected:
    // All term nodes delegate to the template visitTerm method
    void visit(FuzzyTerm &n) override { myVisit(n); }
    void visit(InTerm &n) override { myVisit(n); }
    void visit(LocationTerm &n) override { myVisit(n); }
    void visit(NearestNeighborQueryNode &n) override { myVisit(n); }
    void visit(NumberTerm &n) override { myVisit(n); }
    void visit(PredicateQuery &n) override { myVisit(n); }
    void visit(PrefixTerm &n) override { myVisit(n); }
    void visit(QueryTerm &n) override { myVisit(n); }
    void visit(RangeTerm &n) override { myVisit(n); }
    void visit(RegexpTerm &n) override { myVisit(n); }
    void visit(StringTerm &n) override { myVisit(n); }
    void visit(SubstringTerm &n) override { myVisit(n); }
    void visit(SuffixTerm &n) override { myVisit(n); }
    void visit(DotProductTerm &n) override { myVisit(n); }
    void visit(WandTerm &n) override { myVisit(n); }
    void visit(WeightedSetTerm &n) override { myVisit(n); }
    void visit(WordAlternatives &n) override { myVisit(n); }
};

}
