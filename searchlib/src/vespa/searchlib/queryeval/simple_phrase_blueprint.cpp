// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_phrase_blueprint.h"
#include "simple_phrase_search.h"
#include "field_spec.hpp"
#include <vespa/vespalib/objects/visit.hpp>
#include <map>

namespace search::queryeval {

SimplePhraseBlueprint::SimplePhraseBlueprint(const FieldSpec &field, bool expensive)
    : ComplexLeafBlueprint(field),
      _field(field),
      _estimate(),
      _terms()
{
    if (expensive) {
        set_cost_tier(State::COST_TIER_EXPENSIVE);
    }
}

SimplePhraseBlueprint::~SimplePhraseBlueprint() = default;

void
SimplePhraseBlueprint::addTerm(Blueprint::UP term)
{
    const State &childState = term->getState();
    assert(childState.numFields() == 1);
    const FieldSpecBase &childField = childState.field(0);
    assert(childField.getFieldId() == _field.getFieldId());
    (void) childField;

    HitEstimate childEst = childState.estimate();
    if (_terms.empty() ||  childEst < _estimate) {
        _estimate = childEst;
    }
    setEstimate(_estimate);
    _terms.push_back(std::move(term));
}

void
SimplePhraseBlueprint::sort(InFlow in_flow)
{
    resolve_strict(in_flow);
    for (auto &term: _terms) {
        term->sort(in_flow);
    }
}

FlowStats
SimplePhraseBlueprint::calculate_flow_stats(uint32_t docid_limit) const
{
    for (auto &term: _terms) {
        term->update_flow_stats(docid_limit);
    }
    double est = AndFlow::estimate_of(_terms);
    return {est,
            AndFlow::cost_of(_terms, false) + est * _terms.size(),
            AndFlow::cost_of(_terms, true) + est * _terms.size()};
}

SearchIterator::UP
SimplePhraseBlueprint::createLeafSearch(const fef::TermFieldMatchDataArray &) const {
    assert(false);
    return {};
}

SearchIterator::UP
SimplePhraseBlueprint::createLeafSearch(const fef::TermFieldMatchDataArray &tfmda, fef::MatchData &global_md) const {
    assert(tfmda.size() == 1);
    fef::TermFieldMatchDataArray childMatch;
    SimplePhraseSearch::Children children;
    children.reserve(_terms.size());
    std::multimap<uint32_t, uint32_t> order_map;
    for (size_t i = 0; i < _terms.size(); ++i) {
        const State &childState = _terms[i]->getState();
        assert(childState.numFields() == 1);
        auto *child_term_field_match_data = childState.field(0).resolve(global_md);
        child_term_field_match_data->setNeedInterleavedFeatures(tfmda[0]->needs_interleaved_features());
        child_term_field_match_data->setNeedNormalFeatures(true);
        childMatch.add(child_term_field_match_data);
        children.push_back(_terms[i]->createSearch(global_md));
        order_map.insert(std::make_pair(childState.estimate().estHits, i));
    }
    std::vector<uint32_t> eval_order;
    eval_order.reserve(order_map.size());
    for (const auto & child : order_map) {
        eval_order.push_back(child.second);
    }
    std::unique_ptr<fef::MatchData> md;
    return std::make_unique<SimplePhraseSearch>(std::move(children),
                                                std::move(md), std::move(childMatch),
                                                std::move(eval_order), *tfmda[0], strict());
}

SearchIterator::UP
SimplePhraseBlueprint::createFilterSearchImpl(FilterConstraint constraint) const
{
    return create_atmost_and_filter(_terms, constraint);
}

void
SimplePhraseBlueprint::fetchPostings(const ExecuteInfo &execInfo)
{
    for (auto & term : _terms) {
        term->fetchPostings(execInfo);
    }
}

void
SimplePhraseBlueprint::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    LeafBlueprint::visitMembers(visitor);
    visit(visitor, "terms", _terms);
}

}
