// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include "create_blueprint_visitor_helper.h"
#include "leaf_blueprints.h"
#include "dot_product_blueprint.h"
#include "get_weight_from_node.h"
#include "wand/parallel_weak_and_blueprint.h"
#include "simple_phrase_blueprint.h"
#include "weighted_set_term_blueprint.h"
#include "split_float.h"

namespace search::queryeval {

CreateBlueprintVisitorHelper::CreateBlueprintVisitorHelper(Searchable &searchable, const FieldSpec &field, const IRequestContext & requestContext)
    : _requestContext(requestContext),
      _searchable(searchable),
      _field(field),
      _result()
{}

CreateBlueprintVisitorHelper::~CreateBlueprintVisitorHelper() = default;

Blueprint::UP
CreateBlueprintVisitorHelper::getResult()
{
    return _result
        ? std::move(_result)
        : Blueprint::UP(new EmptyBlueprint(_field));
}

void
CreateBlueprintVisitorHelper::visitPhrase(query::Phrase &n) {
    SimplePhraseBlueprint *phrase = new SimplePhraseBlueprint(_field, _requestContext);
    Blueprint::UP result(phrase);
    for (size_t i = 0; i < n.getChildren().size(); ++i) {
        FieldSpecList fields;
        fields.add(phrase->getNextChildField(_field));
        phrase->addTerm(_searchable.createBlueprint(_requestContext, fields, *n.getChildren()[i]));
    }
    setResult(std::move(result));
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

template <typename WS, typename NODE>
void
CreateBlueprintVisitorHelper::createWeightedSet(WS *bp, NODE &n) {
    Blueprint::UP result(bp);
    FieldSpecList fields;
    for (size_t i = 0; i < n.getChildren().size(); ++i) {
        fields.clear();
        fields.add(bp->getNextChildField(_field));
        const query::Node &node = *n.getChildren()[i];
        uint32_t weight = getWeightFromNode(node).percent();
        bp->addTerm(_searchable.createBlueprint(_requestContext, fields, node), weight);
    }
    setResult(std::move(result));
}
void
CreateBlueprintVisitorHelper::visitWeightedSetTerm(query::WeightedSetTerm &n) {
    WeightedSetTermBlueprint *bp = new WeightedSetTermBlueprint(_field);
    createWeightedSet(bp, n);
}
void
CreateBlueprintVisitorHelper::visitDotProduct(query::DotProduct &n) {
    DotProductBlueprint *bp = new DotProductBlueprint(_field);
    createWeightedSet(bp, n);
}
void
CreateBlueprintVisitorHelper::visitWandTerm(query::WandTerm &n) {
    ParallelWeakAndBlueprint *bp = new ParallelWeakAndBlueprint(_field,
                                                                n.getTargetNumHits(),
                                                                n.getScoreThreshold(),
                                                                n.getThresholdBoostFactor());
    createWeightedSet(bp, n);
}

}
