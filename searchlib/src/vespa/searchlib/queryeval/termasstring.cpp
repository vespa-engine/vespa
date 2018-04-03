// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
using search::query::Equiv;
using search::query::NumberTerm;
using search::query::LocationTerm;
using search::query::Near;
using search::query::Node;
using search::query::ONear;
using search::query::Or;
using search::query::Phrase;
using search::query::PredicateQuery;
using search::query::PrefixTerm;
using search::query::QueryVisitor;
using search::query::RangeTerm;
using search::query::Rank;
using search::query::RegExpTerm;
using search::query::StringTerm;
using search::query::SubstringTerm;
using search::query::SuffixTerm;
using search::query::WeakAnd;
using search::query::WeightedSetTerm;
using search::query::DotProduct;
using search::query::WandTerm;
using vespalib::string;

namespace search::queryeval {

vespalib::string termAsString(double float_term) {
    vespalib::asciistream os;
    return (os << float_term).str();
}

vespalib::string termAsString(int64_t int_term) {
    vespalib::asciistream os;
    return (os << int_term).str();
}

vespalib::string termAsString(const search::query::Range &term) {
    vespalib::asciistream os;
    return (os << term).str();
}

vespalib::string termAsString(const search::query::Location &term) {
    vespalib::asciistream os;
    return (os << term).str();
}

namespace {
struct TermAsStringVisitor : public QueryVisitor {
    string term;
    bool isSet;

    TermAsStringVisitor() : term(), isSet(false) {}

    template <class TermNode>
    void visitTerm(TermNode &n) {
        term = termAsString(n.getTerm());
        isSet = true;
    }

    void illegalVisit() {
        term.clear();
        isSet = false;
    }

    void visit(And &) override {illegalVisit(); }
    void visit(AndNot &) override {illegalVisit(); }
    void visit(Equiv &) override {illegalVisit(); }
    void visit(Near &) override {illegalVisit(); }
    void visit(ONear &) override {illegalVisit(); }
    void visit(Or &) override {illegalVisit(); }
    void visit(Phrase &) override {illegalVisit(); }
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
    void visit(PredicateQuery &) override {illegalVisit(); }
};
}  // namespace

string termAsString(const Node &term_node) {
    TermAsStringVisitor visitor;
    const_cast<Node &>(term_node).accept(visitor);
    if (!visitor.isSet) {
        vespalib::string err(vespalib::make_string("Trying to convert a non-term node ('%s') to a term string.", typeid(term_node).name()));
        LOG(warning, "%s", err.c_str());
        throw vespalib::IllegalArgumentException(err, VESPA_STRLOC);
    }
    return visitor.term;
}

}
