// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

namespace search {
namespace queryeval {

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

    virtual void visit(And &) { illegalVisit(); }
    virtual void visit(AndNot &) { illegalVisit(); }
    virtual void visit(Equiv &) { illegalVisit(); }
    virtual void visit(Near &) { illegalVisit(); }
    virtual void visit(ONear &) { illegalVisit(); }
    virtual void visit(Or &) { illegalVisit(); }
    virtual void visit(Phrase &) { illegalVisit(); }
    virtual void visit(Rank &) { illegalVisit(); }
    virtual void visit(WeakAnd &) { illegalVisit(); }
    virtual void visit(WeightedSetTerm &) { illegalVisit(); }
    virtual void visit(DotProduct &) { illegalVisit(); }
    virtual void visit(WandTerm &) { illegalVisit(); }

    virtual void visit(NumberTerm &n) { visitTerm(n); }
    virtual void visit(LocationTerm &n) { visitTerm(n); }
    virtual void visit(PrefixTerm &n) { visitTerm(n); }
    virtual void visit(RangeTerm &n) { visitTerm(n); }
    virtual void visit(StringTerm &n) { visitTerm(n); }
    virtual void visit(SubstringTerm &n) { visitTerm(n); }
    virtual void visit(SuffixTerm &n) { visitTerm(n); }
    virtual void visit(RegExpTerm &n) { visitTerm(n); }

    virtual void visit(PredicateQuery &) { illegalVisit(); }
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

} // namespace search::queryeval
} // namespace search
