// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "direct_weighted_set_blueprint.h"
#include "document_weight_or_filter_search.h"
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <memory>

namespace search::attribute {

template <typename SearchType>
DirectWeightedSetBlueprint<SearchType>::DirectWeightedSetBlueprint(const queryeval::FieldSpec &field,
                                                                   const IAttributeVector &iattr,
                                                                   const IDocumentWeightAttribute &attr,
                                                                   size_t size_hint)
    : ComplexLeafBlueprint(field),
      _weights(),
      _terms(),
      _iattr(iattr),
      _attr(attr),
      _dictionary_snapshot(_attr.get_dictionary_snapshot())
{
    set_allow_termwise_eval(true);
    _weights.reserve(size_hint);
    _terms.reserve(size_hint);
}

template <typename SearchType>
DirectWeightedSetBlueprint<SearchType>::~DirectWeightedSetBlueprint() = default;

template <typename SearchType>
std::unique_ptr<queryeval::SearchIterator>
DirectWeightedSetBlueprint<SearchType>::createLeafSearch(const fef::TermFieldMatchDataArray &tfmda, bool) const
{
    assert(tfmda.size() == 1);
    assert(getState().numFields() == 1);
    if (_terms.empty()) {
        return std::make_unique<queryeval::EmptySearch>();
    }
    std::vector<DocumentWeightIterator> iterators;
    const size_t numChildren = _terms.size();
    iterators.reserve(numChildren);
    for (const IDocumentWeightAttribute::LookupResult &r : _terms) {
        _attr.create(r.posting_idx, iterators);
    }
    bool field_is_filter = getState().fields()[0].isFilter();
    if (field_is_filter && tfmda[0]->isNotNeeded()) {
        return attribute::DocumentWeightOrFilterSearch::create(std::move(iterators));
    }
    return SearchType::create(*tfmda[0], field_is_filter, _weights, std::move(iterators));
}

template <typename SearchType>
std::unique_ptr<queryeval::SearchIterator>
DirectWeightedSetBlueprint<SearchType>::createFilterSearch(bool, FilterConstraint) const
{
    std::vector<DocumentWeightIterator> iterators;
    iterators.reserve(_terms.size());
    for (const IDocumentWeightAttribute::LookupResult &r : _terms) {
        _attr.create(r.posting_idx, iterators);
    }
    return attribute::DocumentWeightOrFilterSearch::create(std::move(iterators));
}

}
