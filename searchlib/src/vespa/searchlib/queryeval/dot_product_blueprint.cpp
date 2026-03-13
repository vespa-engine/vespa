// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dot_product_blueprint.h"
#include "dot_product_search.h"
#include "flow_tuning.h"
#include "field_spec.hpp"
#include <vespa/vespalib/objects/visit.hpp>

namespace search::queryeval {

DotProductBlueprint::DotProductBlueprint(const FieldSpec &field)
    : ComplexLeafBlueprint(field),
      _layout(),
      _weights(),
      _terms()
{ }

DotProductBlueprint::~DotProductBlueprint() = default;

void
DotProductBlueprint::reserve(size_t num_children) {
    _weights.reserve(num_children);
    _terms.reserve(num_children);
    _layout.reserve(num_children);
}

void
DotProductBlueprint::addTerm(Blueprint::UP term, int32_t weight, HitEstimate & estimate)
{
    HitEstimate childEst = term->getState().estimate();
    if (! childEst.empty) {
        if (estimate.empty) {
            estimate = childEst;
        } else {
            estimate.estHits += childEst.estHits;
        }
    }
    _weights.push_back(weight);
    _terms.push_back(std::move(term));
}

void
DotProductBlueprint::sort(InFlow in_flow)
{
    in_flow.force_strict();
    resolve_strict(in_flow);
    for (auto &term: _terms) {
        term->sort(in_flow);
    }
}

FlowStats
DotProductBlueprint::calculate_flow_stats(uint32_t docid_limit) const
{
    for (auto &term: _terms) {
        term->update_flow_stats(docid_limit);
    }
    double est = OrFlow::estimate_of(_terms);
    return {est, OrFlow::cost_of(_terms, false),
            OrFlow::cost_of(_terms, true) + flow::heap_cost(est, _terms.size())};
}

SearchIterator::UP
DotProductBlueprint::createLeafSearch(const search::fef::TermFieldMatchDataArray &tfmda) const
{
    assert(tfmda.size() == 1);
    assert(getState().numFields() == 1);
    fef::MatchData::UP md = _layout.createMatchData();
    std::vector<fef::TermFieldMatchData*> childMatch;
    std::vector<SearchIterator*> children(_terms.size());
    for (size_t i = 0; i < _terms.size(); ++i) {
        const State &childState = _terms[i]->getState();
        assert(childState.numFields() == 1);
        childMatch.push_back(childState.field(0).resolve(*md));
        // TODO: pass ownership with unique_ptr
        children[i] = _terms[i]->createSearch(*md).release();
    }
    bool field_is_filter = getState().fields()[0].isFilter();
    return DotProductSearch::create(children, *tfmda[0], field_is_filter, childMatch, _weights, std::move(md));
}

SearchIterator::UP
DotProductBlueprint::createFilterSearchImpl(FilterConstraint constraint) const
{
    return create_or_filter(_terms, constraint);
}

void
DotProductBlueprint::fetchPostings(const ExecuteInfo &execInfo)
{
    for (size_t i = 0; i < _terms.size(); ++i) {
        _terms[i]->fetchPostings(execInfo);
    }    
}

void
DotProductBlueprint::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    LeafBlueprint::visitMembers(visitor);
    visit(visitor, "_weights", _weights);
    visit(visitor, "_terms", _terms);
}

}
