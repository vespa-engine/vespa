// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchable.h"
#include "termasstring.h"
#include <vespa/searchlib/query/tree/intermediatenodes.h>
#include <vespa/searchlib/query/tree/queryvisitor.h>
#include <vespa/searchlib/query/tree/termnodes.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <memory>

namespace search::queryeval {

class CreateBlueprintVisitorHelper : public query::QueryVisitor
{
private:
    const IRequestContext & _requestContext;
    Searchable            & _searchable;
    FieldSpec               _field;
    Blueprint::UP           _result;

protected:
    const IRequestContext & getRequestContext() const { return _requestContext; }

public:
    CreateBlueprintVisitorHelper(Searchable &searchable, const FieldSpec &field, const IRequestContext & requestContext);
    ~CreateBlueprintVisitorHelper();

    template <typename T>
    std::unique_ptr<T> make_UP(T *p) { return std::unique_ptr<T>(p); }

    template <typename T>
    void setResult(std::unique_ptr<T> result) { _result = std::move(result); }

    Blueprint::UP getResult();

    const FieldSpec &getField() const { return _field; }

    void visitPhrase(query::Phrase &n);
    void visitSameElement(query::SameElement &n);

    template <typename WS, typename NODE>
    void createWeightedSet(WS *bp, NODE &n);
    void visitWeightedSetTerm(query::WeightedSetTerm &n);
    void visitDotProduct(query::DotProduct &n);
    void visitWandTerm(query::WandTerm &n);

    void handleNumberTermAsText(query::NumberTerm &n);

    void illegalVisit() {}

    void visit(query::And &) override { illegalVisit(); }
    void visit(query::AndNot &) override { illegalVisit(); }
    void visit(query::Equiv &) override { illegalVisit(); }
    void visit(query::Near &) override { illegalVisit(); }
    void visit(query::ONear &) override { illegalVisit(); }
    void visit(query::Or &) override { illegalVisit(); }
    void visit(query::Rank &) override { illegalVisit(); }
    void visit(query::WeakAnd &) override { illegalVisit(); }

    void visit(query::Phrase &n) override {
        visitPhrase(n);
    }
    void visit(query::SameElement &n) override {
        visitSameElement(n);
    }
    void visit(query::WeightedSetTerm &n) override { visitWeightedSetTerm(n); }
    void visit(query::DotProduct &n) override { visitDotProduct(n); }
    void visit(query::WandTerm &n) override { visitWandTerm(n); }

    void visit(query::NumberTerm &n) override = 0;
    void visit(query::LocationTerm &n) override = 0;
    void visit(query::PrefixTerm &n) override = 0;
    void visit(query::RangeTerm &n) override = 0;
    void visit(query::StringTerm &n) override = 0;
    void visit(query::SubstringTerm &n) override = 0;
    void visit(query::SuffixTerm &n) override = 0;
    void visit(query::RegExpTerm &n) override = 0;
};

}
