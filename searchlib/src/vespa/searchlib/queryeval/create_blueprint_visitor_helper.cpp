// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include "create_blueprint_visitor_helper.h"
#include "leaf_blueprints.h"
#include "dot_product_blueprint.h"
#include "get_weight_from_node.h"
#include "wand/parallel_weak_and_blueprint.h"
#include "simple_phrase_blueprint.h"
#include "weighted_set_term_blueprint.h"
#include "split_float.h"
#include "irequestcontext.h"

namespace search::queryeval {

CreateBlueprintVisitorHelper::CreateBlueprintVisitorHelper(Searchable &searchable, const FieldSpec &field, const IRequestContext & requestContext)
    : _requestContext(requestContext),
      _searchable(searchable),
      _field(field),
      _result()
{}

CreateBlueprintVisitorHelper::~CreateBlueprintVisitorHelper() = default;

bool
CreateBlueprintVisitorHelper::is_search_multi_threaded() const noexcept {
    return getRequestContext().thread_bundle().size() > 1;
}

attribute::SearchContextParams
CreateBlueprintVisitorHelper::createContextParams() const {
    return attribute::SearchContextParams().metaStoreReadGuard(_requestContext.getMetaStoreReadGuard());
}
attribute::SearchContextParams
CreateBlueprintVisitorHelper::createContextParams(bool useBitVector) const {
    return createContextParams().useBitVector(useBitVector);
}

Blueprint::UP
CreateBlueprintVisitorHelper::getResult()
{
    return _result
        ? std::move(_result)
        : std::make_unique<EmptyBlueprint>(_field);
}

void
CreateBlueprintVisitorHelper::visitPhrase(query::Phrase &n) {
    auto phrase = std::make_unique<SimplePhraseBlueprint>(_field, n.is_expensive());
    for (const query::Node * child : n.getChildren()) {
        FieldSpecList fields;
        fields.add(phrase->getNextChildField(_field));
        phrase->addTerm(_searchable.createBlueprint(_requestContext, fields, *child));
    }
    setResult(std::move(phrase));
}

void
CreateBlueprintVisitorHelper::handleNumberTermAsText(query::NumberTerm &n)
{
    vespalib::string termStr = termAsString(n);
    queryeval::SplitFloat splitter(termStr);
    if (splitter.parts() > 1) {
        query::SimplePhrase phraseNode(n.getView(), n.getId(), n.getWeight());
        phraseNode.setStateFrom(n);
        for (size_t i = 0; i < splitter.parts(); ++i) {
            phraseNode.append(std::make_unique<query::SimpleStringTerm>(splitter.getPart(i), "", 0, query::Weight(0)));
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

template <typename WS, typename NODE>
void
CreateBlueprintVisitorHelper::createWeightedSet(std::unique_ptr<WS> bp, NODE &n) {
    bp->reserve(n.getNumTerms());
    Blueprint::HitEstimate estimate;
    FieldSpec childField(_field);
    for (size_t i = 0; i < n.getNumTerms(); ++i) {
        auto term = n.getAsString(i);
        query::SimpleStringTerm node(term.first, n.getView(), 0, term.second); // TODO Temporary
        childField.setBase(bp->getNextChildField(_field));
        bp->addTerm(_searchable.createBlueprint(_requestContext, childField, node), term.second.percent(), estimate);
    }
    bp->complete(estimate);
    setResult(std::move(bp));
}

void
CreateBlueprintVisitorHelper::visitWeightedSetTerm(query::WeightedSetTerm &n)
{
    createWeightedSet(std::make_unique<WeightedSetTermBlueprint>(_field), n);
}

void
CreateBlueprintVisitorHelper::visitDotProduct(query::DotProduct &n)
{
    createWeightedSet(std::make_unique<DotProductBlueprint>(_field), n);
}

void
CreateBlueprintVisitorHelper::visitWandTerm(query::WandTerm &n)
{
    createWeightedSet(std::make_unique<ParallelWeakAndBlueprint>(_field, n.getTargetNumHits(),
                                                                 n.getScoreThreshold(), n.getThresholdBoostFactor(),
                                                                 is_search_multi_threaded()),
                      n);
}

void
CreateBlueprintVisitorHelper::visitInTerm(query::InTerm &n)
{
    createWeightedSet(std::make_unique<WeightedSetTermBlueprint>(_field), n);
}

void CreateBlueprintVisitorHelper::visit(query::TrueQueryNode &) {
    setResult(std::make_unique<AlwaysTrueBlueprint>());
}

void CreateBlueprintVisitorHelper::visit(query::FalseQueryNode &) {
    setResult(std::make_unique<EmptyBlueprint>());
}

}
