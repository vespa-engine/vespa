// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "weighted_set_term_blueprint.h"
#include "weighted_set_term_search.h"
#include "orsearch.h"
#include "matching_elements_search.h"
#include "flow_tuning.h"
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/searchlib/common/matching_elements_fields.h>
#include <vespa/vespalib/objects/visit.hpp>

namespace search::queryeval {

class WeightedSetTermMatchingElementsSearch : public MatchingElementsSearch
{
    fef::TermFieldMatchData                _tfmd;
    fef::TermFieldMatchDataArray           _tfmda;
    std::string                       _field_name;
    const std::vector<Blueprint::UP>      &_terms;
    std::unique_ptr<WeightedSetTermSearch> _search;

public:
    WeightedSetTermMatchingElementsSearch(const WeightedSetTermBlueprint& bp, const std::string& field_name, const std::vector<Blueprint::UP> &terms);
    ~WeightedSetTermMatchingElementsSearch() override;
    void find_matching_elements(uint32_t docid, MatchingElements& result) override;
    void initRange(uint32_t begin_id, uint32_t end_id) override;
};

WeightedSetTermMatchingElementsSearch::WeightedSetTermMatchingElementsSearch(const WeightedSetTermBlueprint& bp, const std::string& field_name, const std::vector<Blueprint::UP> &terms)
    : _tfmd(),
      _tfmda(),
      _field_name(field_name),
      _terms(terms),
      _search()
{
    _tfmda.add(&_tfmd);
    _search.reset(static_cast<WeightedSetTermSearch *>(bp.createLeafSearch(_tfmda).release()));

}

WeightedSetTermMatchingElementsSearch::~WeightedSetTermMatchingElementsSearch() = default;

void
WeightedSetTermMatchingElementsSearch::find_matching_elements(uint32_t docid, MatchingElements& result)
{
    _matching_elements.clear();
    _search->seek(docid);
    _search->find_matching_elements(docid, _terms, _matching_elements);
    if (!_matching_elements.empty()) {
        std::sort(_matching_elements.begin(), _matching_elements.end());
        _matching_elements.resize(std::unique(_matching_elements.begin(), _matching_elements.end()) - _matching_elements.begin());
        result.add_matching_elements(docid, _field_name, _matching_elements);
    }
}

void
WeightedSetTermMatchingElementsSearch::initRange(uint32_t begin_id, uint32_t end_id)
{
    _search->initRange(begin_id, end_id);
}

WeightedSetTermBlueprint::WeightedSetTermBlueprint(const FieldSpec &field)
    : ComplexLeafBlueprint(field),
      _layout(),
      _children_field(field.getName(), field.getFieldId(), _layout.allocTermField(field.getFieldId()), field.isFilter()),
      _weights(),
      _terms()
{
    set_allow_termwise_eval(true);
}

WeightedSetTermBlueprint::~WeightedSetTermBlueprint() = default;

void
WeightedSetTermBlueprint::reserve(size_t num_children) {
    _weights.reserve(num_children);
    _terms.reserve(num_children);
    _layout.reserve(num_children);
}

void
WeightedSetTermBlueprint::addTerm(Blueprint::UP term, int32_t weight, HitEstimate & estimate)
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
WeightedSetTermBlueprint::sort(InFlow in_flow)
{
    in_flow.force_strict();
    resolve_strict(in_flow);
    for (auto &term: _terms) {
        term->sort(in_flow);
    }
}

FlowStats
WeightedSetTermBlueprint::calculate_flow_stats(uint32_t docid_limit) const
{
    for (auto &term: _terms) {
        term->update_flow_stats(docid_limit);
    }
    double est = OrFlow::estimate_of(_terms);
    return {est, OrFlow::cost_of(_terms, false),
            OrFlow::cost_of(_terms, true) + flow::heap_cost(est, _terms.size())};
}

SearchIterator::UP
WeightedSetTermBlueprint::createLeafSearch(const fef::TermFieldMatchDataArray &tfmda) const
{
    assert(tfmda.size() == 1);
    if ((_terms.size() == 1) && tfmda[0]->isNotNeeded()) {
        if (const LeafBlueprint * leaf = _terms[0]->asLeaf(); leaf != nullptr) {
            // Always returnin a strict iterator independently of what was required,
            // as that is what we do with all the children when there are more.
            return leaf->createLeafSearch(tfmda);
        }
    }
    fef::MatchData::UP md = _layout.createMatchData();
    std::vector<SearchIterator*> children;
    children.reserve(_terms.size());
    for (const auto & _term : _terms) {
        // TODO: pass ownership with unique_ptr
        children.push_back(_term->createSearch(*md).release());
    }
    return WeightedSetTermSearch::create(children, *tfmda[0], _children_field.isFilter(), _weights, std::move(md));
}

SearchIterator::UP
WeightedSetTermBlueprint::createFilterSearchImpl(FilterConstraint constraint) const
{
    return create_or_filter(_terms, constraint);
}

std::unique_ptr<MatchingElementsSearch>
WeightedSetTermBlueprint::create_matching_elements_search(const MatchingElementsFields &fields) const
{
    if (fields.has_field(_children_field.getName())) {
        return std::make_unique<WeightedSetTermMatchingElementsSearch>(*this, fields.enclosing_field(_children_field.getName()), _terms);
    } else {
        return {};
    }
}

void
WeightedSetTermBlueprint::fetchPostings(const ExecuteInfo &execInfo)
{
    for (const auto & _term : _terms) {
        _term->fetchPostings(execInfo);
    }
}

void
WeightedSetTermBlueprint::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    LeafBlueprint::visitMembers(visitor);
    visit(visitor, "_weights", _weights);
    visit(visitor, "_terms", _terms);
}

}
