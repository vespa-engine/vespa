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

class CreateBlueprintVisitorHelper : public search::query::QueryVisitor
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

    void visitPhrase(search::query::Phrase &n);

    template <typename WS, typename NODE>
    void createWeightedSet(WS *bp, NODE &n);
    void visitWeightedSetTerm(search::query::WeightedSetTerm &n);
    void visitDotProduct(search::query::DotProduct &n);
    void visitWandTerm(search::query::WandTerm &n);

    void handleNumberTermAsText(search::query::NumberTerm &n);

    void illegalVisit() {}

    void visit(search::query::And &) override { illegalVisit(); }
    void visit(search::query::AndNot &) override { illegalVisit(); }
    void visit(search::query::Equiv &) override { illegalVisit(); }
    void visit(search::query::Near &) override { illegalVisit(); }
    void visit(search::query::ONear &) override { illegalVisit(); }
    void visit(search::query::Or &) override { illegalVisit(); }
    void visit(search::query::Rank &) override { illegalVisit(); }
    void visit(search::query::WeakAnd &) override { illegalVisit(); }

    void visit(search::query::Phrase &n) override {
        visitPhrase(n);
    }
    void visit(search::query::WeightedSetTerm &n) override { visitWeightedSetTerm(n); }
    void visit(search::query::DotProduct &n) override { visitDotProduct(n); }
    void visit(search::query::WandTerm &n) override { visitWandTerm(n); }

    void visit(search::query::NumberTerm &n) override = 0;
    void visit(search::query::LocationTerm &n) override = 0;
    void visit(search::query::PrefixTerm &n) override = 0;
    void visit(search::query::RangeTerm &n) override = 0;
    void visit(search::query::StringTerm &n) override = 0;
    void visit(search::query::SubstringTerm &n) override = 0;
    void visit(search::query::SuffixTerm &n) override = 0;
    void visit(search::query::RegExpTerm &n) override = 0;
};

}
