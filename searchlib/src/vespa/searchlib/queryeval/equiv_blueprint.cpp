// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "equiv_blueprint.h"
#include "equivsearch.h"
#include "field_spec.hpp"
#include <vespa/vespalib/objects/visit.hpp>
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace search::queryeval {

namespace {

class UnpackNeed
{
    bool _needs_normal_features;
    bool _needs_interleaved_features;
public:
    UnpackNeed()
        : _needs_normal_features(false),
          _needs_interleaved_features(false)
    {
    }

    void observe(const fef::TermFieldMatchData &output)
    {
        if (output.needs_normal_features()) {
            _needs_normal_features = true;
        }
        if (output.needs_interleaved_features()) {
            _needs_interleaved_features = true;
        }
    }

    void notify(fef::TermFieldMatchData &input) const
    {
        input.setNeedNormalFeatures(_needs_normal_features);
        input.setNeedInterleavedFeatures(_needs_interleaved_features);
    }
};

};

EquivBlueprint::EquivBlueprint(const FieldSpecBaseList &fields,
                               fef::MatchDataLayout subtree_mdl)
    : ComplexLeafBlueprint(fields),
      _fields(fields),
      _estimate(),
      _layout(subtree_mdl),
      _terms(),
      _exactness()
{
}

EquivBlueprint::~EquivBlueprint() = default;

SearchIterator::UP
EquivBlueprint::createLeafSearch(const fef::TermFieldMatchDataArray &outputs, bool strict) const
{
    fef::MatchData::UP md = _layout.createMatchData();
    MultiSearch::Children children;
    children.reserve(_terms.size());
    fef::TermMatchDataMerger::Inputs childMatch;
    vespalib::hash_map<uint16_t, UnpackNeed> unpack_needs(outputs.size());
    for (size_t i = 0; i < outputs.size(); ++i) {
        unpack_needs[outputs[i]->getFieldId()].observe(*outputs[i]);
    }
    for (size_t i = 0; i < _terms.size(); ++i) {
        const State &childState = _terms[i]->getState();
        for (size_t j = 0; j < childState.numFields(); ++j) {
            auto *child_term_field_match_data = childState.field(j).resolve(*md);
            unpack_needs[child_term_field_match_data->getFieldId()].notify(*child_term_field_match_data);
            childMatch.emplace_back(child_term_field_match_data, _exactness[i]);
        }
        children.push_back(_terms[i]->createSearch(*md, strict));
    }
    return EquivSearch::create(std::move(children), std::move(md), childMatch, outputs, strict);
}

SearchIterator::UP
EquivBlueprint::createFilterSearch(bool strict, FilterConstraint constraint) const
{
    return create_or_filter(_terms, strict, constraint);
}

void
EquivBlueprint::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    LeafBlueprint::visitMembers(visitor);
    visit(visitor, "terms", _terms);
}

void
EquivBlueprint::fetchPostings(const ExecuteInfo &execInfo)
{
    for (size_t i = 0; i < _terms.size(); ++i) {
        _terms[i]->fetchPostings(execInfo);
    }
}

EquivBlueprint&
EquivBlueprint::addTerm(Blueprint::UP term, double exactness)
{
    const State &childState = term->getState();

    HitEstimate childEst = childState.estimate();
    if (_terms.empty() || _estimate < childEst  ) {
        _estimate = childEst;
    }
    setEstimate(_estimate);
    _terms.push_back(std::move(term));
    _exactness.push_back(exactness);
    return *this;
}

}
