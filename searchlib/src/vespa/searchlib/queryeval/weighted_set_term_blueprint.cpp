// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "weighted_set_term_blueprint.h"
#include "weighted_set_term_search.h"
#include "orsearch.h"
#include "matching_elements_search.h"
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/searchlib/common/matching_elements_fields.h>
#include <vespa/vespalib/objects/visit.hpp>

namespace search::queryeval {

class WeightedSetTermMatchingElementsSearch : public MatchingElementsSearch
{
    fef::TermFieldMatchData                _tfmd;
    fef::TermFieldMatchDataArray           _tfmda;
    vespalib::string                       _field_name;
    const std::vector<Blueprint::UP>      &_terms;
    std::unique_ptr<WeightedSetTermSearch> _search;
    
public:
    WeightedSetTermMatchingElementsSearch(const WeightedSetTermBlueprint& bp, const vespalib::string& field_name, const std::vector<Blueprint::UP> &terms);
    ~WeightedSetTermMatchingElementsSearch() override;
    void find_matching_elements(uint32_t docid, MatchingElements& result) override;
    void initRange(uint32_t begin_id, uint32_t end_id) override;
};

WeightedSetTermMatchingElementsSearch::WeightedSetTermMatchingElementsSearch(const WeightedSetTermBlueprint& bp, const vespalib::string& field_name, const std::vector<Blueprint::UP> &terms)
    : _tfmd(),
      _tfmda(),
      _field_name(field_name),
      _terms(terms),
      _search()
{
    _tfmda.add(&_tfmd);
    auto generic_search = bp.createLeafSearch(_tfmda, false);
    auto weighted_set_term_search = dynamic_cast<WeightedSetTermSearch *>(generic_search.get());
    generic_search.release();
    _search.reset(weighted_set_term_search);
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
      _estimate(),
      _layout(),
      _children_field(field.getName(), field.getFieldId(), _layout.allocTermField(field.getFieldId()), field.isFilter()),
      _weights(),
      _terms()
{
    set_allow_termwise_eval(true);
}

WeightedSetTermBlueprint::~WeightedSetTermBlueprint() = default;

void
WeightedSetTermBlueprint::addTerm(Blueprint::UP term, int32_t weight)
{
    HitEstimate childEst = term->getState().estimate();
    if (! childEst.empty) {
        if (_estimate.empty) {
            _estimate = childEst;
        } else {
            _estimate.estHits += childEst.estHits;
        }
        setEstimate(_estimate);
    }
    _weights.push_back(weight);
    _terms.push_back(std::move(term));
}


SearchIterator::UP
WeightedSetTermBlueprint::createLeafSearch(const fef::TermFieldMatchDataArray &tfmda, bool) const
{
    assert(tfmda.size() == 1);
    fef::MatchData::UP md = _layout.createMatchData();
    std::vector<SearchIterator*> children(_terms.size());
    for (size_t i = 0; i < _terms.size(); ++i) {
        // TODO: pass ownership with unique_ptr
        children[i] = _terms[i]->createSearch(*md, true).release();
    }
    return SearchIterator::UP(WeightedSetTermSearch::create(children, *tfmda[0], _children_field.isFilter(), _weights, std::move(md)));
}

SearchIterator::UP
WeightedSetTermBlueprint::createFilterSearch(bool strict, FilterConstraint constraint) const
{
    return create_or_filter(_terms, strict, constraint);
}

std::unique_ptr<MatchingElementsSearch>
WeightedSetTermBlueprint::create_matching_elements_search(const MatchingElementsFields &fields) const
{
    if (fields.has_field(_children_field.getName())) {
        return std::make_unique<WeightedSetTermMatchingElementsSearch>(*this, _children_field.getName(), _terms);
    } else {
        return std::unique_ptr<MatchingElementsSearch>();
    }
}

void
WeightedSetTermBlueprint::fetchPostings(const ExecuteInfo &execInfo)
{
    ExecuteInfo childInfo = ExecuteInfo::create(true, execInfo.hitRate());
    for (size_t i = 0; i < _terms.size(); ++i) {
        _terms[i]->fetchPostings(childInfo);
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
