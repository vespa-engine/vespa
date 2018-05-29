// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "querynodes.h"
#include "blueprintbuilder.h"
#include <vespa/searchlib/query/tree/customtypevisitor.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/equiv_blueprint.h>
#include <vespa/searchlib/queryeval/get_weight_from_node.h>

using namespace search::queryeval;

namespace proton::matching {

namespace {

struct Mixer {
    std::unique_ptr<OrBlueprint> attributes;

    Mixer() : attributes() {}

    void addAttribute(Blueprint::UP attr) {
        if (attributes.get() == 0) {
            attributes.reset(new OrBlueprint());
        }
        attributes->addChild(std::move(attr));
    }

    Blueprint::UP mix(Blueprint::UP indexes) {
        if (attributes.get() == 0) {
            if (indexes.get() == 0) {
                return Blueprint::UP(new EmptyBlueprint());
            }
            return Blueprint::UP(std::move(indexes));
        }
        if (indexes.get() == 0) {
            if (attributes->childCnt() == 1) {
                return attributes->removeChild(0);
            } else {
                return Blueprint::UP(std::move(attributes));
            }
        }
        attributes->addChild(Blueprint::UP(std::move(indexes)));
        return Blueprint::UP(std::move(attributes));
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

    void buildChildren(IntermediateBlueprint &parent,
                       const std::vector<search::query::Node *> &children)
    {
        for (size_t i = 0; i < children.size(); ++i) {
            parent.addChild(BlueprintBuilder::build(_requestContext, *children[i], _context));
        }
    }

    template <typename NodeType>
    void buildIntermediate(IntermediateBlueprint *b, NodeType &n) {
        std::unique_ptr<IntermediateBlueprint> blueprint(b);
        buildChildren(*blueprint, n.getChildren());
        _result.reset(blueprint.release());
    }

    void buildWeakAnd(ProtonWeakAnd &n) {
        WeakAndBlueprint *wand = new WeakAndBlueprint(n.getMinHits());
        Blueprint::UP result(wand);
        for (size_t i = 0; i < n.getChildren().size(); ++i) {
            search::query::Node &node = *n.getChildren()[i];
            uint32_t weight = getWeightFromNode(node).percent();
            wand->addTerm(BlueprintBuilder::build(_requestContext, node, _context), weight);
        }
        _result = std::move(result);
    }

    void buildEquiv(ProtonEquiv &n) {
        double eqw = n.getWeight().percent();
        FieldSpecBaseList specs;
        for (size_t i = 0; i < n.numFields(); ++i) {
            specs.add(n.field(i).fieldSpec());
        }
        EquivBlueprint *eq = new EquivBlueprint(specs, n.children_mdl);
        _result.reset(eq);
        for (size_t i = 0; i < n.getChildren().size(); ++i) {
            search::query::Node &node = *n.getChildren()[i];
            double w = getWeightFromNode(node).percent();
            eq->addTerm(BlueprintBuilder::build(_requestContext, node, _context), w / eqw);
        }
        n.setDocumentFrequency(_result->getState().estimate().estHits, _context.getDocIdLimit());
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
                FieldSpecList attrField;
                attrField.add(field.fieldSpec());
                mixer.addAttribute(_context.getAttributes().createBlueprint(_requestContext, attrField, n));
            } else {
                indexFields.add(field.fieldSpec());
            }
        }
        Blueprint::UP indexBlueprint;
        if (!indexFields.empty()) {
            indexBlueprint = _context.getIndexes().createBlueprint(_requestContext, indexFields, n);
        }
        _result = mixer.mix(std::move(indexBlueprint));
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
    void visit(ProtonSameElement &n) override { buildIntermediate(nullptr /*new SameElementBlueprint())*/, n); }


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
};

} // namespace proton::matching::<unnamed>

search::queryeval::Blueprint::UP
BlueprintBuilder::build(const IRequestContext & requestContext,
                        search::query::Node &node,
                        ISearchContext &context)
{
    BlueprintBuilderVisitor visitor(requestContext, context);
    node.accept(visitor);
    Blueprint::UP result = visitor.build();
    result->setDocIdLimit(context.getDocIdLimit());
    return result;
}

}
