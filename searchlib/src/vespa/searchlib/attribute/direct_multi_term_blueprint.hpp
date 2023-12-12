// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "direct_multi_term_blueprint.h"
#include "document_weight_or_filter_search.h"
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/queryeval/filter_wrapper.h>
#include <vespa/searchlib/queryeval/orsearch.h>
#include <memory>
#include <type_traits>

using search::queryeval::FilterWrapper;
using search::queryeval::SearchIterator;

namespace search::queryeval { class WeightedSetTermSearch; }

namespace search::attribute {

template <typename PostingStoreType, typename SearchType>
DirectMultiTermBlueprint<PostingStoreType, SearchType>::DirectMultiTermBlueprint(const queryeval::FieldSpec &field,
                                                                                 const IAttributeVector &iattr,
                                                                                 const PostingStoreType &attr,
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

template <typename PostingStoreType, typename SearchType>
DirectMultiTermBlueprint<PostingStoreType, SearchType>::~DirectMultiTermBlueprint() = default;

template <typename PostingStoreType, typename SearchType>
typename DirectMultiTermBlueprint<PostingStoreType, SearchType>::IteratorWeights
DirectMultiTermBlueprint<PostingStoreType, SearchType>::create_iterators(std::vector<DocidWithWeightIterator>& weight_iterators,
                                                                         std::vector<std::unique_ptr<SearchIterator>>& bitvectors,
                                                                         bool use_bitvector_when_available,
                                                                         fef::TermFieldMatchData& tfmd, bool strict) const

{
    std::vector<int32_t> result_weights;
    for (size_t i = 0; i < _terms.size(); ++i) {
        const auto& r = _terms[i];
        if (use_bitvector_when_available && _attr.has_bitvector(r.posting_idx)) {
            if (bitvectors.empty()) {
                // With a combination of weight iterators and bitvectors,
                // ensure that the resulting weight vector matches the weight iterators.
                result_weights.reserve(_weights.size());
                result_weights.insert(result_weights.begin(), _weights.begin(), _weights.begin() + i);
            }
            bitvectors.push_back(_attr.make_bitvector_iterator(r.posting_idx, get_docid_limit(), tfmd, strict));
        } else {
            _attr.create(r.posting_idx, weight_iterators);
            if (!bitvectors.empty()) {
                result_weights.push_back(_weights[i]);
            }
        }
    }
    if (result_weights.empty()) {
        // Only weight iterators are used, so just reference the original weight vector.
        return std::cref(_weights);
    } else {
        return result_weights;
    }
}

template <typename PostingStoreType, typename SearchType>
std::unique_ptr<SearchIterator>
DirectMultiTermBlueprint<PostingStoreType, SearchType>::combine_iterators(std::unique_ptr<SearchIterator> multi_term_iterator,
                                                                          std::vector<std::unique_ptr<SearchIterator>>&& bitvectors,
                                                                          bool strict) const
{
    if (!bitvectors.empty()) {
        if (multi_term_iterator) {
            bitvectors.push_back(std::move(multi_term_iterator));
        }
        return queryeval::OrSearch::create(std::move(bitvectors), strict);
    }
    return multi_term_iterator;
}

template <typename PostingStoreType, typename SearchType>
std::unique_ptr<queryeval::SearchIterator>
DirectMultiTermBlueprint<PostingStoreType, SearchType>::create_search_helper(const fef::TermFieldMatchDataArray& tfmda, bool strict, bool is_filter_search) const
{
    if (_terms.empty()) {
        return std::make_unique<queryeval::EmptySearch>();
    }
    std::vector<DocidWithWeightIterator> weight_iterators;
    std::vector<queryeval::SearchIterator::UP> bitvectors;
    const size_t num_children = _terms.size();
    weight_iterators.reserve(num_children);
    bool use_bit_vector_when_available = is_filter_search || !_attr.has_always_weight_iterator();
    auto weights = create_iterators(weight_iterators, bitvectors, use_bit_vector_when_available, *tfmda[0], strict);
    if (is_filter_search) {
        auto filter = !weight_iterators.empty() ? attribute::DocumentWeightOrFilterSearch::create(std::move(weight_iterators)) : std::unique_ptr<SearchIterator>();
        return combine_iterators(std::move(filter), std::move(bitvectors), strict);
    }
    bool field_is_filter = getState().fields()[0].isFilter();
    if constexpr (std::is_same_v<SearchType, queryeval::WeightedSetTermSearch>) {
        auto multi_term = !weight_iterators.empty() ?
                SearchType::create(*tfmda[0], field_is_filter, std::move(weights), std::move(weight_iterators))
                : std::unique_ptr<SearchIterator>();
        return combine_iterators(std::move(multi_term), std::move(bitvectors), strict);
    } else {
        // In this case we should only have weight iterators.
        assert(weight_iterators.size() == _terms.size());
        assert(weights.index() == 0);
        return SearchType::create(*tfmda[0], field_is_filter, std::get<0>(weights).get(), std::move(weight_iterators));
    }
}

template <typename PostingStoreType, typename SearchType>
std::unique_ptr<queryeval::SearchIterator>
DirectMultiTermBlueprint<PostingStoreType, SearchType>::createLeafSearch(const fef::TermFieldMatchDataArray &tfmda, bool strict) const
{
    assert(tfmda.size() == 1);
    assert(getState().numFields() == 1);
    bool field_is_filter = getState().fields()[0].isFilter();
    bool is_filter_search = field_is_filter && tfmda[0]->isNotNeeded();
    return create_search_helper(tfmda, strict, is_filter_search);
}

template <typename PostingStoreType, typename SearchType>
std::unique_ptr<queryeval::SearchIterator>
DirectMultiTermBlueprint<PostingStoreType, SearchType>::createFilterSearch(bool strict, FilterConstraint) const
{
    assert(getState().numFields() == 1);
    auto wrapper = std::make_unique<FilterWrapper>(getState().numFields());
    wrapper->wrap(create_search_helper(wrapper->tfmda(), strict, true));
    return wrapper;
}

}
