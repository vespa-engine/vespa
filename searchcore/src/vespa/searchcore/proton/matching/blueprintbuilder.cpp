// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "blueprintbuilder.h"
#include "querynodes.h"
#include "same_element_builder.h"
#include <vespa/searchcorespi/index/indexsearchable.h>
#include <vespa/searchlib/query/tree/customtypevisitor.h>
#include <vespa/searchlib/queryeval/create_blueprint_params.h>
#include <vespa/searchlib/queryeval/equiv_blueprint.h>
#include <vespa/searchlib/queryeval/get_weight_from_node.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/vespalib/util/issue.h>

using namespace search::queryeval;
using search::query::Node;

namespace proton::matching {

namespace {

struct Mixer {
    std::unique_ptr<OrBlueprint> attributes;

    Mixer() noexcept: attributes() {}

    void addAttribute(Blueprint::UP attr) {
        if ( ! attributes) {
            attributes = std::make_unique<OrBlueprint>();
        }
        attributes->addChild(std::move(attr));
    }

    Blueprint::UP mix(Blueprint::UP indexes) {
        if ( ! attributes) {
            if ( ! indexes) {
                return std::make_unique<EmptyBlueprint>();
            }
            return indexes;
        }
        if ( ! indexes) {
            if (attributes->childCnt() == 1) {
                return attributes->removeChild(0);
            } else {
                return std::move(attributes);
            }
        }
        attributes->addChild(std::move(indexes));
        return std::move(attributes);
    }
};

/**
 * requires that match data space has been reserved
 */
class BlueprintBuilderVisitor :
        public search::query::CustomTypeVisitor<ProtonNodeTypes>
{
private:
    const IRequestContext & _requestContext;
    ISearchContext &_context;
    Blueprint::UP   _result;

    void buildChildren(IntermediateBlueprint &parent, const std::vector<Node *> &children);
    bool is_search_multi_threaded() const noexcept {
        return _requestContext.thread_bundle().size() > 1;
    }

    template <typename NodeType>
    void buildIntermediate(IntermediateBlueprint *b, NodeType &n) __attribute__((noinline));

    void buildWeakAnd(ProtonWeakAnd &n) {
        auto *wand = new WeakAndBlueprint(n.getTargetNumHits(),
                                          _requestContext.get_create_blueprint_params().weakand_range,
                                          _requestContext.get_create_blueprint_params().weakand_stop_word_strategy,
                                          is_search_multi_threaded());
        Blueprint::UP result(wand);
        for (auto node : n.getChildren()) {
            uint32_t weight = getWeightFromNode(*node).percent();
            wand->addTerm(build(_requestContext, *node, _context), weight);
        }
        _result = std::move(result);
    }

    void buildEquiv(ProtonEquiv &n) {
        double eqw = n.getWeight().percent();
        FieldSpecBaseList specs;
        specs.reserve(n.numFields());
        for (size_t i = 0; i < n.numFields(); ++i) {
            specs.add(n.field(i).fieldSpec());
        }
        auto *eq = new EquivBlueprint(std::move(specs), n.children_mdl);
        _result.reset(eq);
        for (auto node : n.getChildren()) {
            double w = getWeightFromNode(*node).percent();
            eq->addTerm(build(_requestContext, *node, _context), w / eqw);
        }
        _result->setDocIdLimit(_context.getDocIdLimit());
        n.setDocumentFrequency(_result->getState().estimate().estHits, _context.getDocIdLimit());
    }

    void buildSameElement(ProtonSameElement &n) {
        if (n.numFields() == 1) {
            SameElementBuilder builder(_requestContext, _context, n.field(0).fieldSpec(), n.is_expensive());
            for (Node *node: n.getChildren()) {
                builder.add_child(*node);
            }
            _result = builder.build();
        } else {
            vespalib::Issue::report("SameElement operator searches in unexpected number of fields. Expected 1 but was %zu", n.numFields());
            _result = std::make_unique<EmptyBlueprint>();
        }
    }

    template <typename NodeType>
    void buildTerm(NodeType &n) {
        FieldSpecList indexFields;
        Mixer mixer;
        for (size_t i = 0; i < n.numFields(); ++i) {
            const ProtonTermData::FieldEntry &field = n.field(i);
            assert(field.getFieldId() != search::fef::IllegalFieldId);
            assert(field.getHandle() != search::fef::IllegalHandle);
            if (field.attribute_field) {
                mixer.addAttribute(_context.getAttributes().createBlueprint(_requestContext, field.fieldSpec(), n));
            } else {
                indexFields.add(field.fieldSpec());
            }
        }
        Blueprint::UP indexBlueprint;
        if (!indexFields.empty()) {
            indexBlueprint = _context.getIndexes().createBlueprint(_requestContext, indexFields, n);
        }
        _result = mixer.mix(std::move(indexBlueprint));
        _result->setDocIdLimit(_context.getDocIdLimit());
        n.setDocumentFrequency(_result->getState().estimate().estHits, _context.getDocIdLimit());
    }

protected:
    void visit(ProtonAnd &n)         override { buildIntermediate(new AndBlueprint(), n); }
    void visit(ProtonAndNot &n)      override { buildIntermediate(new AndNotBlueprint(), n); }
    void visit(ProtonOr &n)          override { buildIntermediate(new OrBlueprint(), n); }
    void visit(ProtonWeakAnd &n)     override { buildWeakAnd(n); }
    void visit(ProtonEquiv &n)       override { buildEquiv(n); }
    void visit(ProtonRank &n)        override { buildIntermediate(new RankBlueprint(), n); }
    void visit(ProtonNear &n)        override { buildIntermediate(new NearBlueprint(n.getDistance()), n); }
    void visit(ProtonONear &n)       override { buildIntermediate(new ONearBlueprint(n.getDistance()), n); }
    void visit(ProtonSameElement &n) override { buildSameElement(n); }

    void visit(ProtonWeightedSetTerm &n) override { buildTerm(n); }
    void visit(ProtonDotProduct &n)      override { buildTerm(n); }
    void visit(ProtonWandTerm &n)        override { buildTerm(n); }

    void visit(ProtonPhrase &n)          override { buildTerm(n); }
    void visit(ProtonNumberTerm &n)      override { buildTerm(n); }
    void visit(ProtonLocationTerm &n)    override { buildTerm(n); }
    void visit(ProtonPrefixTerm &n)      override { buildTerm(n); }
    void visit(ProtonRangeTerm &n)       override { buildTerm(n); }
    void visit(ProtonStringTerm &n)      override { buildTerm(n); }
    void visit(ProtonSubstringTerm &n)   override { buildTerm(n); }
    void visit(ProtonSuffixTerm &n)      override { buildTerm(n); }
    void visit(ProtonPredicateQuery &n)  override { buildTerm(n); }
    void visit(ProtonRegExpTerm &n)      override { buildTerm(n); }
    void visit(ProtonNearestNeighborTerm &n) override { buildTerm(n); }
    void visit(ProtonTrue &) override {
        _result = std::make_unique<AlwaysTrueBlueprint>();
    }
    void visit(ProtonFalse &) override {
        _result = std::make_unique<EmptyBlueprint>();
    }
    void visit(ProtonFuzzyTerm &n)      override { buildTerm(n); }
    void visit(ProtonInTerm& n)         override { buildTerm(n); }

public:
    BlueprintBuilderVisitor(const IRequestContext & requestContext, ISearchContext &context) :
        _requestContext(requestContext),
        _context(context),
        _result()
    { }
    Blueprint::UP build() {
        assert(_result);
        return std::move(_result);
    }
    static Blueprint::UP build(const IRequestContext & requestContext, Node &node, ISearchContext &context) {
        BlueprintBuilderVisitor visitor(requestContext, context);
        node.accept(visitor);
        Blueprint::UP result = visitor.build();
        return result;

    }
};

void
BlueprintBuilderVisitor::buildChildren(IntermediateBlueprint &parent, const std::vector<Node *> &children)
{
    parent.reserve(children.size());
    for (auto child : children) {
        parent.addChild(build(_requestContext, *child, _context));
    }
}

template <typename NodeType>
void
BlueprintBuilderVisitor::buildIntermediate(IntermediateBlueprint *b, NodeType &n) {
    std::unique_ptr<IntermediateBlueprint> blueprint(b);
    buildChildren(*blueprint, n.getChildren());
    _result = std::move(blueprint);
}

IntermediateBlueprint *
asRankOrAndNot(Blueprint * blueprint) {
    return ((blueprint->isAndNot() || blueprint->isRank()))
           ? blueprint->asIntermediate()
           : nullptr;
}

IntermediateBlueprint *
lastConsequtiveRankOrAndNot(Blueprint * blueprint) {
    IntermediateBlueprint * prev = nullptr;
    IntermediateBlueprint * curr = asRankOrAndNot(blueprint);
    while (curr != nullptr) {
        prev =  curr;
        curr = asRankOrAndNot(&curr->getChild(0));
    }
    return prev;
}

} // namespace proton::matching::<unnamed>

Blueprint::UP
BlueprintBuilder::build(const IRequestContext & requestContext,
                        Node &node, Blueprint::UP whiteList, ISearchContext &context)
{
    auto blueprint = BlueprintBuilderVisitor::build(requestContext, node, context);
    if (whiteList) {
        auto andBlueprint = std::make_unique<AndBlueprint>();
        IntermediateBlueprint * rankOrAndNot = lastConsequtiveRankOrAndNot(blueprint.get());
        if (rankOrAndNot != nullptr) {
            (*andBlueprint)
                    .addChild(rankOrAndNot->removeChild(0))
                    .addChild(std::move(whiteList));
            rankOrAndNot->insertChild(0, std::move(andBlueprint));
        } else {
            (*andBlueprint)
                    .addChild(std::move(blueprint))
                    .addChild(std::move(whiteList));
            blueprint = std::move(andBlueprint);
        }
    }
    blueprint->setDocIdLimit(context.getDocIdLimit());
    return blueprint;
}

}
