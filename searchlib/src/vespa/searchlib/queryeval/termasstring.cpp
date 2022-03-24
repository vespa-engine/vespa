// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "termasstring.h"
#include <vespa/searchlib/query/tree/node.h>
#include <vespa/searchlib/query/tree/queryvisitor.h>
#include <vespa/searchlib/query/tree/termnodes.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <typeinfo>
#include <vespa/log/log.h>

LOG_SETUP(".termasstring");

using search::query::And;
using search::query::AndNot;
using search::query::DotProduct;
using search::query::Equiv;
using search::query::FalseQueryNode;
using search::query::FuzzyTerm;
using search::query::LocationTerm;
using search::query::Near;
using search::query::NearestNeighborTerm;
using search::query::Node;
using search::query::NumberTerm;
using search::query::ONear;
using search::query::Or;
using search::query::Phrase;
using search::query::PredicateQuery;
using search::query::PrefixTerm;
using search::query::QueryVisitor;
using search::query::RangeTerm;
using search::query::Rank;
using search::query::RegExpTerm;
using search::query::SameElement;
using search::query::StringTerm;
using search::query::SubstringTerm;
using search::query::SuffixTerm;
using search::query::TrueQueryNode;
using search::query::WandTerm;
using search::query::WeakAnd;
using search::query::WeightedSetTerm;
using vespalib::string;
using vespalib::stringref;

namespace search::queryeval {

namespace {

stringref
termAsString(const search::query::Range &term, string & scratchPad) {
    vespalib::asciistream os;
    scratchPad = (os << term).str();
    return scratchPad;
}

stringref
termAsString(const search::query::Location &term, string & scratchPad) {
    vespalib::asciistream os;
    scratchPad = (os << term).str();
    return scratchPad;
}

stringref
termAsString(const string &term, string &) {
    return term;
}

struct TermAsStringVisitor : public QueryVisitor {
    string  & _scratchPad;
    stringref term;
    bool      isSet;

    TermAsStringVisitor(string & scratchPad) : _scratchPad(scratchPad), term(), isSet(false) {}

    template <class TermNode>
    void visitTerm(TermNode &n) {
        term = termAsString(n.getTerm(), _scratchPad);
        isSet = true;
    }

    void illegalVisit() {
        term = stringref();
        isSet = false;
    }

    void visit(And &) override {illegalVisit(); }
    void visit(AndNot &) override {illegalVisit(); }
    void visit(Equiv &) override {illegalVisit(); }
    void visit(Near &) override {illegalVisit(); }
    void visit(ONear &) override {illegalVisit(); }
    void visit(Or &) override {illegalVisit(); }
    void visit(Phrase &) override {illegalVisit(); }
    void visit(SameElement &) override {illegalVisit(); }
    void visit(Rank &) override {illegalVisit(); }
    void visit(WeakAnd &) override {illegalVisit(); }
    void visit(WeightedSetTerm &) override {illegalVisit(); }
    void visit(DotProduct &) override {illegalVisit(); }
    void visit(WandTerm &) override {illegalVisit(); }

    void visit(NumberTerm &n) override {visitTerm(n); }
    void visit(LocationTerm &n) override {visitTerm(n); }
    void visit(PrefixTerm &n) override {visitTerm(n); }
    void visit(RangeTerm &n) override {visitTerm(n); }
    void visit(StringTerm &n) override {visitTerm(n); }
    void visit(SubstringTerm &n) override {visitTerm(n); }
    void visit(SuffixTerm &n) override {visitTerm(n); }
    void visit(RegExpTerm &n) override {visitTerm(n); }
    void visit(FuzzyTerm &n) override { visitTerm(n); }
    void visit(PredicateQuery &) override {illegalVisit(); }
    void visit(NearestNeighborTerm &) override { illegalVisit(); }
    void visit(TrueQueryNode &) override { illegalVisit(); }
    void visit(FalseQueryNode &) override { illegalVisit(); }
};

void throwFailure(const search::query::Node &term_node) __attribute((noinline));

void
throwFailure(const search::query::Node &term_node) {
    string err(vespalib::make_string("Trying to convert a non-term node ('%s') to a term string.", typeid(term_node).name()));
    LOG(warning, "%s", err.c_str());
    throw vespalib::IllegalArgumentException(err, VESPA_STRLOC);
}

}  // namespace

string
termAsString(const Node &term_node) {
    string scratchPad;
    return termAsString(term_node, scratchPad);
}

stringref
termAsString(const search::query::Node &term_node, string & scratchPad) {
    TermAsStringVisitor visitor(scratchPad);
    const_cast<Node &>(term_node).accept(visitor);
    if (!visitor.isSet) {
        throwFailure(term_node);
    }
    return visitor.term;
}

}
