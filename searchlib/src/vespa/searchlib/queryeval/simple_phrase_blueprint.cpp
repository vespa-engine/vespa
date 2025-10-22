// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_phrase_blueprint.h"
#include "simple_phrase_search.h"
#include "field_spec.hpp"
#include <vespa/vespalib/objects/visit.hpp>
#include <map>

#include <vespa/log/log.h>
LOG_SETUP(".queryeval.simple_phrase_blueprint");

namespace search::queryeval {

SimplePhraseBlueprint::SimplePhraseBlueprint(const FieldSpec &field, bool expensive)
    : ComplexLeafBlueprint(field),
      _field(field),
      _estimate(),
      _layout(),
      _terms()
{
    if (expensive) {
        set_cost_tier(State::COST_TIER_EXPENSIVE);
    }
}

SimplePhraseBlueprint::~SimplePhraseBlueprint() = default;

FieldSpec
SimplePhraseBlueprint::getNextChildField(const FieldSpec &outer)
{
    return {outer.getName(), outer.getFieldId(), _layout.allocTermField(outer.getFieldId()), false};
}

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
SimplePhraseBlueprint::createSearchImpl(fef::MatchData& md) const {
    fef::TermFieldMatchDataArray tfmda = resolveFields(md);
    assert(tfmda.size() == 1);
    fef::TermFieldMatchDataArray childMatch;
    SimplePhraseSearch::Children children;
    children.reserve(_terms.size());
    std::multimap<uint32_t, uint32_t> order_map;
    for (size_t i = 0; i < _terms.size(); ++i) {
        const State &childState = _terms[i]->getState();
        assert(childState.numFields() == 1);
        auto *child_term_field_match_data = childState.field(0).resolve(md);
        child_term_field_match_data->setNeedInterleavedFeatures(tfmda[0]->needs_interleaved_features());
        LOG(debug, "phrase force need handle=%d\n", childState.field(0).getHandle());
        child_term_field_match_data->setNeedNormalFeatures(true);
        childMatch.add(child_term_field_match_data);
        children.push_back(_terms[i]->createSearch(md));
        order_map.insert(std::make_pair(childState.estimate().estHits, i));
    }
    std::vector<uint32_t> eval_order;
    eval_order.reserve(order_map.size());
    for (const auto & child : order_map) {
        eval_order.push_back(child.second);
    }
    return std::make_unique<SimplePhraseSearch>(std::move(children),
                                                std::unique_ptr<fef::MatchData>(), std::move(childMatch),
                                                std::move(eval_order), *tfmda[0], strict());
}

SearchIterator::UP
SimplePhraseBlueprint::createLeafSearch(const fef::TermFieldMatchDataArray &) const
{
    assert(false); // should not be called
    return {};
}

SearchIterator::UP
SimplePhraseBlueprint::createFilterSearchImpl(FilterConstraint constraint) const
{
    return create_atmost_and_filter(_terms, strict(), constraint);
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
