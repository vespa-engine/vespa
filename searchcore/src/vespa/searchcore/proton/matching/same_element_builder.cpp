// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "same_element_builder.h"
#include "querynodes.h"
#include <vespa/searchlib/query/tree/customtypevisitor.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/same_element_blueprint.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchcorespi/index/indexsearchable.h>

using search::queryeval::Blueprint;
using search::queryeval::EmptyBlueprint;
using search::queryeval::AndBlueprint;
using search::queryeval::FieldSpecList;
using search::queryeval::FieldSpec;
using search::queryeval::IRequestContext;
using search::queryeval::SameElementBlueprint;
using search::queryeval::Searchable;

namespace proton::matching {

namespace {

FieldSpec createfilterSpec(const FieldSpec & fs) {
    return FieldSpec(fs.getName(),fs.getFieldId(), fs.getHandle(), true);
}

class SameElementBuilderVisitor : public search::query::CustomTypeVisitor<ProtonNodeTypes>
{
private:
    const IRequestContext &_requestContext;
    ISearchContext        &_context;
    SameElementBlueprint  &_sameElement;
    AndBlueprint          &_andFilter;

public:
    SameElementBuilderVisitor(const IRequestContext &requestContext, ISearchContext &context, SameElementBlueprint &sameElement, AndBlueprint & andFilter)
        : _requestContext(requestContext),
          _context(context),
          _sameElement(sameElement),
          _andFilter(andFilter)
    {}

    template <class TermNode>
    void visitTerm(const TermNode &n) {
        if (n.numFields() == 1) {
            const ProtonTermData::FieldEntry &field = n.field(0);
            assert(field.getFieldId() != search::fef::IllegalFieldId);
            assert(field.getHandle() == search::fef::IllegalHandle);
            FieldSpecList field_spec, filter_field_spec;
            field_spec.add(_sameElement.getNextChildField(field.field_name, field.getFieldId()));
            Searchable &searchable = field.attribute_field ? _context.getAttributes() : _context.getIndexes();
            _sameElement.addTerm(searchable.createBlueprint(_requestContext, field_spec, n));
            filter_field_spec.add(createfilterSpec(field_spec[0]));
            _andFilter.addChild(searchable.createBlueprint(_requestContext, filter_field_spec, n));
        }
    }

    void visit(ProtonAnd &) override {}
    void visit(ProtonAndNot &) override {}
    void visit(ProtonNear &) override {}
    void visit(ProtonONear &) override {}
    void visit(ProtonOr &) override {}
    void visit(ProtonRank &) override {}
    void visit(ProtonWeakAnd &) override {}
    void visit(ProtonSameElement &) override {}

    void visit(ProtonWeightedSetTerm &) override {}
    void visit(ProtonDotProduct &) override {}
    void visit(ProtonWandTerm &) override {}
    void visit(ProtonPhrase &) override {}
    void visit(ProtonEquiv &) override {}

    void visit(ProtonNumberTerm &n) override { visitTerm(n); }
    void visit(ProtonLocationTerm &n) override { visitTerm(n); }
    void visit(ProtonPrefixTerm &n) override { visitTerm(n); }
    void visit(ProtonRangeTerm &n) override { visitTerm(n); }
    void visit(ProtonStringTerm &n) override { visitTerm(n); }
    void visit(ProtonSubstringTerm &n) override { visitTerm(n); }
    void visit(ProtonSuffixTerm &n) override { visitTerm(n); }
    void visit(ProtonPredicateQuery &) override {}
    void visit(ProtonRegExpTerm &n) override { visitTerm(n); }
};

} // namespace proton::matching::<unnamed>

SameElementBuilder::SameElementBuilder(const search::queryeval::IRequestContext &requestContext, ISearchContext &context)
    : _requestContext(requestContext),
      _context(context),
      _sameElement(std::make_unique<SameElementBlueprint>()),
      _andFilter(std::make_unique<AndBlueprint>())
{
}

SameElementBuilder::~SameElementBuilder() = default;
void
SameElementBuilder::add_child(search::query::Node &node)
{
    SameElementBuilderVisitor visitor(_requestContext, _context, *_sameElement, *_andFilter);
    node.accept(visitor);
}

Blueprint::UP
SameElementBuilder::build()
{
    if (!_sameElement || _sameElement->terms().empty()) {
        return std::make_unique<EmptyBlueprint>();
    }
    _andFilter->addChild(std::move(_sameElement));
    return std::move(_andFilter);
}

} // namespace proton::matching
