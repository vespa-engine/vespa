// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "term_visitor.h"
#include "queryterm.h"
#include <vector>

namespace search::streaming {

/**
 * Example 1: Simple term collector
 * Collects all terms in the query tree.
 */
class TermCollector : public TermVisitor {
    std::vector<QueryTerm*> _terms;

    void addTerm(QueryTerm &term) {
        _terms.push_back(&term);
    }

protected:
    // Implement all term visit methods
    void visit(FuzzyTerm &n) override { addTerm(n); }
    void visit(InTerm &n) override { addTerm(n); }
    void visit(LocationTerm &n) override { addTerm(n); }
    void visit(NearestNeighborQueryNode &n) override { addTerm(n); }
    void visit(NumberTerm &n) override { addTerm(n); }
    void visit(PredicateQuery &n) override { addTerm(n); }
    void visit(PrefixTerm &n) override { addTerm(n); }
    void visit(QueryTerm &n) override { addTerm(n); }
    void visit(RangeTerm &n) override { addTerm(n); }
    void visit(RegexpTerm &n) override { addTerm(n); }
    void visit(StringTerm &n) override { addTerm(n); }
    void visit(SubstringTerm &n) override { addTerm(n); }
    void visit(SuffixTerm &n) override { addTerm(n); }
    void visit(DotProductTerm &n) override { addTerm(n); }
    void visit(WandTerm &n) override { addTerm(n); }
    void visit(WeightedSetTerm &n) override { addTerm(n); }
    void visit(WordAlternatives &n) override { addTerm(n); }

public:
    const std::vector<QueryTerm*>& getTerms() const { return _terms; }
    void clear() { _terms.clear(); }
};

/**
 * Example 2: Term counter
 * Counts the number of terms in the query tree.
 */
class TermCounter : public TermVisitor {
    int _count = 0;

    void countTerm(QueryTerm &term) {
        (void)term;
        _count++;
    }

protected:
    void visit(FuzzyTerm &n) override { countTerm(n); }
    void visit(InTerm &n) override { countTerm(n); }
    void visit(LocationTerm &n) override { countTerm(n); }
    void visit(NearestNeighborQueryNode &n) override { countTerm(n); }
    void visit(NumberTerm &n) override { countTerm(n); }
    void visit(PredicateQuery &n) override { countTerm(n); }
    void visit(PrefixTerm &n) override { countTerm(n); }
    void visit(QueryTerm &n) override { countTerm(n); }
    void visit(RangeTerm &n) override { countTerm(n); }
    void visit(RegexpTerm &n) override { countTerm(n); }
    void visit(StringTerm &n) override { countTerm(n); }
    void visit(SubstringTerm &n) override { countTerm(n); }
    void visit(SuffixTerm &n) override { countTerm(n); }
    void visit(DotProductTerm &n) override { countTerm(n); }
    void visit(WandTerm &n) override { countTerm(n); }
    void visit(WeightedSetTerm &n) override { countTerm(n); }
    void visit(WordAlternatives &n) override { countTerm(n); }

public:
    int getCount() const { return _count; }
    void reset() { _count = 0; }
};

/**
 * Example 3: Index analyzer
 * Collects statistics about which indexes are used.
 */
class IndexAnalyzer : public TermVisitor {
    std::map<std::string, int> _index_counts;

    void analyzeIndex(QueryTerm &term) {
        const auto& index = term.getIndex();
        _index_counts[index]++;
    }

protected:
    void visit(FuzzyTerm &n) override { analyzeIndex(n); }
    void visit(InTerm &n) override { analyzeIndex(n); }
    void visit(LocationTerm &n) override { analyzeIndex(n); }
    void visit(NearestNeighborQueryNode &n) override { analyzeIndex(n); }
    void visit(NumberTerm &n) override { analyzeIndex(n); }
    void visit(PredicateQuery &n) override { analyzeIndex(n); }
    void visit(PrefixTerm &n) override { analyzeIndex(n); }
    void visit(QueryTerm &n) override { analyzeIndex(n); }
    void visit(RangeTerm &n) override { analyzeIndex(n); }
    void visit(RegexpTerm &n) override { analyzeIndex(n); }
    void visit(StringTerm &n) override { analyzeIndex(n); }
    void visit(SubstringTerm &n) override { analyzeIndex(n); }
    void visit(SuffixTerm &n) override { analyzeIndex(n); }
    void visit(DotProductTerm &n) override { analyzeIndex(n); }
    void visit(WandTerm &n) override { analyzeIndex(n); }
    void visit(WeightedSetTerm &n) override { analyzeIndex(n); }
    void visit(WordAlternatives &n) override { analyzeIndex(n); }

public:
    const std::map<std::string, int>& getIndexCounts() const {
        return _index_counts;
    }
    void clear() { _index_counts.clear(); }
};

/**
 * Example 4: Term type classifier
 * Counts different types of terms (prefix, substring, etc.)
 */
class TermTypeClassifier : public TermVisitor {
    int _prefix_terms = 0;
    int _suffix_terms = 0;
    int _substring_terms = 0;
    int _exact_terms = 0;
    int _other_terms = 0;

protected:
    void visit(PrefixTerm &n) override { (void)n; _prefix_terms++; }
    void visit(SuffixTerm &n) override { (void)n; _suffix_terms++; }
    void visit(SubstringTerm &n) override { (void)n; _substring_terms++; }
    void visit(StringTerm &n) override { (void)n; _exact_terms++; }

    // All other term types count as "other"
    void visit(FuzzyTerm &n) override { (void)n; _other_terms++; }
    void visit(InTerm &n) override { (void)n; _other_terms++; }
    void visit(LocationTerm &n) override { (void)n; _other_terms++; }
    void visit(NearestNeighborQueryNode &n) override { (void)n; _other_terms++; }
    void visit(NumberTerm &n) override { (void)n; _other_terms++; }
    void visit(PredicateQuery &n) override { (void)n; _other_terms++; }
    void visit(QueryTerm &n) override { (void)n; _other_terms++; }
    void visit(RangeTerm &n) override { (void)n; _other_terms++; }
    void visit(RegexpTerm &n) override { (void)n; _other_terms++; }
    void visit(DotProductTerm &n) override { (void)n; _other_terms++; }
    void visit(WandTerm &n) override { (void)n; _other_terms++; }
    void visit(WeightedSetTerm &n) override { (void)n; _other_terms++; }
    void visit(WordAlternatives &n) override { (void)n; _other_terms++; }

public:
    int getPrefixTerms() const { return _prefix_terms; }
    int getSuffixTerms() const { return _suffix_terms; }
    int getSubstringTerms() const { return _substring_terms; }
    int getExactTerms() const { return _exact_terms; }
    int getOtherTerms() const { return _other_terms; }

    void reset() {
        _prefix_terms = 0;
        _suffix_terms = 0;
        _substring_terms = 0;
        _exact_terms = 0;
        _other_terms = 0;
    }
};

}
