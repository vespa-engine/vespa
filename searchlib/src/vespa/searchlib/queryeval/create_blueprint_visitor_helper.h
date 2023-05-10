// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "field_spec.h"
#include "termasstring.h"
#include <vespa/searchlib/query/tree/intermediatenodes.h>
#include <vespa/searchlib/query/tree/queryvisitor.h>
#include <vespa/searchlib/query/tree/termnodes.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchcommon/attribute/search_context_params.h>
#include <memory>

namespace search::queryeval {

class IRequestContext;
class Searchable;
class Blueprint;

class CreateBlueprintVisitorHelper : public query::QueryVisitor
{
private:
    const IRequestContext & _requestContext;
    Searchable            & _searchable;
    FieldSpec               _field;
    std::unique_ptr<Blueprint>  _result;

protected:
    const IRequestContext & getRequestContext() const { return _requestContext; }
    attribute::SearchContextParams createContextParams() const;
    attribute::SearchContextParams createContextParams(bool isFilter) const;
public:
    CreateBlueprintVisitorHelper(Searchable &searchable, const FieldSpec &field, const IRequestContext & requestContext);
    ~CreateBlueprintVisitorHelper() override;

    template <typename T>
    void setResult(std::unique_ptr<T> result) { _result = std::move(result); }

    std::unique_ptr<Blueprint> getResult();

    const FieldSpec &getField() const { return _field; }

    void visitPhrase(query::Phrase &n);

    template <typename WS, typename NODE>
    void createWeightedSet(std::unique_ptr<WS> bp, NODE &n);
    void visitWeightedSetTerm(query::WeightedSetTerm &n);
    void visitDotProduct(query::DotProduct &n);
    void visitWandTerm(query::WandTerm &n);
    void visitNearestNeighborTerm(query::NearestNeighborTerm &n);

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
    void visit(query::SameElement &) override { illegalVisit(); }

    void visit(query::Phrase &n) override {
        visitPhrase(n);
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
    void visit(query::NearestNeighborTerm &n) override = 0;
    void visit(query::FuzzyTerm &n) override = 0;

    void visit(query::TrueQueryNode &) final override;
    void visit(query::FalseQueryNode &) final override;
};

}
