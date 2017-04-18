// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dot_product_blueprint.h"
#include "get_weight_from_node.h"
#include "wand/parallel_weak_and_blueprint.h"
#include "searchable.h"
#include "simple_phrase_blueprint.h"
#include "split_float.h"
#include "termasstring.h"
#include "weighted_set_term_blueprint.h"
#include <vespa/searchlib/query/tree/intermediatenodes.h>
#include <vespa/searchlib/query/tree/queryvisitor.h>
#include <vespa/searchlib/query/tree/termnodes.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <memory>

namespace search {
namespace queryeval {

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
    CreateBlueprintVisitorHelper(Searchable &searchable, const FieldSpec &field, const IRequestContext & requestContext) :
        _requestContext(requestContext),
        _searchable(searchable),
        _field(field),
        _result()
    {}

    template <typename T>
    std::unique_ptr<T> make_UP(T *p) { return std::unique_ptr<T>(p); }

    template <typename T>
    void setResult(std::unique_ptr<T> result) { _result = std::move(result); }

    Blueprint::UP getResult();

    const FieldSpec &getField() const { return _field; }

    void visitPhrase(search::query::Phrase &n) {
        SimplePhraseBlueprint *phrase = new SimplePhraseBlueprint(_field, _requestContext);
        Blueprint::UP result(phrase);
        for (size_t i = 0; i < n.getChildren().size(); ++i) {
            FieldSpecList fields;
            fields.add(phrase->getNextChildField(_field));
            phrase->addTerm(_searchable.createBlueprint(_requestContext, fields, *n.getChildren()[i]));
        }
        setResult(std::move(result));
    }

    template <typename WS, typename NODE>
    void createWeightedSet(WS *bp, NODE &n) {
        Blueprint::UP result(bp);
        FieldSpecList fields;
        for (size_t i = 0; i < n.getChildren().size(); ++i) {
            fields.clear();
            fields.add(bp->getNextChildField(_field));
            const search::query::Node &node = *n.getChildren()[i];
            uint32_t weight = getWeightFromNode(node).percent();
            bp->addTerm(_searchable.createBlueprint(_requestContext, fields, node), weight);
        }
        setResult(std::move(result));
    }
    void visitWeightedSetTerm(search::query::WeightedSetTerm &n) {
        WeightedSetTermBlueprint *bp = new WeightedSetTermBlueprint(_field);
        createWeightedSet(bp, n);
    }
    void visitDotProduct(search::query::DotProduct &n) {
        DotProductBlueprint *bp = new DotProductBlueprint(_field);
        createWeightedSet(bp, n);
    }
    void visitWandTerm(search::query::WandTerm &n) {
        ParallelWeakAndBlueprint *bp = new ParallelWeakAndBlueprint(_field,
                                                                    n.getTargetNumHits(),
                                                                    n.getScoreThreshold(),
                                                                    n.getThresholdBoostFactor());
        createWeightedSet(bp, n);
    }

    void handleNumberTermAsText(search::query::NumberTerm &n)
    {
        vespalib::string termStr = termAsString(n);
        queryeval::SplitFloat splitter(termStr);
        if (splitter.parts() > 1) {
            query::SimplePhrase phraseNode(n.getView(), n.getId(), n.getWeight());
            phraseNode.setStateFrom(n);
            for (size_t i = 0; i < splitter.parts(); ++i) {
                query::Node::UP nn;
                nn.reset(new query::SimpleStringTerm(splitter.getPart(i), "", 0, query::Weight(0)));
                phraseNode.append(std::move(nn));
            }
            visitPhrase(phraseNode);
        } else {
            if (splitter.parts() == 1) {
                termStr = splitter.getPart(0);
            }
            query::SimpleStringTerm stringNode(termStr, n.getView(), n.getId(), n.getWeight());
            stringNode.setStateFrom(n);
            visit(stringNode);
        }
    }

    void illegalVisit() {}

    virtual void visit(search::query::And &) override { illegalVisit(); }
    virtual void visit(search::query::AndNot &) override { illegalVisit(); }
    virtual void visit(search::query::Equiv &) override { illegalVisit(); }
    virtual void visit(search::query::Near &) override { illegalVisit(); }
    virtual void visit(search::query::ONear &) override { illegalVisit(); }
    virtual void visit(search::query::Or &) override { illegalVisit(); }
    virtual void visit(search::query::Rank &) override { illegalVisit(); }
    virtual void visit(search::query::WeakAnd &) override { illegalVisit(); }

    virtual void visit(search::query::Phrase &n) override {
        visitPhrase(n);
    }
    virtual void visit(search::query::WeightedSetTerm &n) override { visitWeightedSetTerm(n); }
    virtual void visit(search::query::DotProduct &n) override { visitDotProduct(n); }
    virtual void visit(search::query::WandTerm &n) override { visitWandTerm(n); }

    virtual void visit(search::query::NumberTerm &n) override = 0;
    virtual void visit(search::query::LocationTerm &n) override = 0;
    virtual void visit(search::query::PrefixTerm &n) override = 0;
    virtual void visit(search::query::RangeTerm &n) override = 0;
    virtual void visit(search::query::StringTerm &n) override = 0;
    virtual void visit(search::query::SubstringTerm &n) override = 0;
    virtual void visit(search::query::SuffixTerm &n) override = 0;
    virtual void visit(search::query::RegExpTerm &n) override = 0;
};

} // namespace search::queryeval
} // namespace search
