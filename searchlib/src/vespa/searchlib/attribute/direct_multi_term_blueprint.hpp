// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "direct_multi_term_blueprint.h"
#include "multi_term_or_filter_search.h"
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/queryeval/filter_wrapper.h>
#include <vespa/searchlib/queryeval/orsearch.h>
#include <cmath>
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
bool
DirectMultiTermBlueprint<PostingStoreType, SearchType>::use_hash_filter(bool strict) const
{
    if (strict || _iattr.hasMultiValue()) {
        return false;
    }
    // The following very simplified formula was created after analysing performance of the IN operator
    // on a 10M document corpus using a machine with an Intel Xeon 2.5 GHz CPU with 48 cores and 256 Gb of memory:
    // https://github.com/vespa-engine/system-test/tree/master/tests/performance/in_operator
    //
    // The following 25 test cases were used to calculate the cost of using btree iterators (strict):
    // op_hits_ratios = [5, 10, 50, 100, 200] * tokens_in_op = [1, 5, 10, 100, 1000]
    // For each case we calculate the latency difference against the case with tokens_in_op=1 and the same op_hits_ratio.
    // This indicates the extra time used to produce the same number of hits when having multiple tokens in the operator.
    // The latency diff is divided with the number of hits produced and convert to nanoseconds:
    //   10M * (op_hits_ratio / 1000) * 1000 * 1000
    // Based on the numbers we can approximate the cost per document (in nanoseconds) as:
    //   8.0 (ns) * log2(tokens_in_op).
    // NOTE: This is very simplified. Ideally we should also take into consideration the hit estimate of this blueprint,
    //       as the cost per document will be lower when producing few hits.
    //
    // In addition, the following 28 test cases were used to calculate the cost of using the hash filter (non-strict).
    // filter_hits_ratios = [1, 5, 10, 50, 100, 150, 200] x op_hits_ratios = [200] x tokens_in_op = [5, 10, 100, 1000]
    // The code was altered to always using the hash filter for non-strict iterators.
    // For each case we calculate the latency difference against a case from above with tokens_in_op=1 that produce a similar number of hits.
    // This indicates the extra time used to produce the same number of hits when using the hash filter.
    // The latency diff is divided with the number of hits the test filter produces and convert to nanoseconds:
    //   10M * (filter_hits_ratio / 1000) * 1000 * 1000
    // Based on the numbers we calculate the average cost per document (in nanoseconds) as 26.0 ns.

    float hash_filter_cost_per_doc_ns = 26.0;
    float btree_iterator_cost_per_doc_ns = 8.0 * std::log2(_terms.size());
    return hash_filter_cost_per_doc_ns < btree_iterator_cost_per_doc_ns;
}

template <typename PostingStoreType, typename SearchType>
typename DirectMultiTermBlueprint<PostingStoreType, SearchType>::IteratorWeights
DirectMultiTermBlueprint<PostingStoreType, SearchType>::create_iterators(std::vector<IteratorType>& btree_iterators,
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
            _attr.create(r.posting_idx, btree_iterators);
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
template <bool filter_search>
std::unique_ptr<queryeval::SearchIterator>
DirectMultiTermBlueprint<PostingStoreType, SearchType>::create_search_helper(const fef::TermFieldMatchDataArray& tfmda,
                                                                             bool strict) const
{
    if (_terms.empty()) {
        return std::make_unique<queryeval::EmptySearch>();
    }
    auto& tfmd = *tfmda[0];
    bool field_is_filter = getState().fields()[0].isFilter();
    if constexpr (SearchType::supports_hash_filter) {
        if (use_hash_filter(strict)) {
            return SearchType::create_hash_filter(tfmd, (filter_search || field_is_filter),
                                                  _weights, _terms,
                                                  _iattr, _attr, _dictionary_snapshot);
        }
    }
    std::vector<IteratorType> btree_iterators;
    std::vector<queryeval::SearchIterator::UP> bitvectors;
    const size_t num_children = _terms.size();
    btree_iterators.reserve(num_children);
    bool use_bit_vector_when_available = filter_search || !_attr.has_always_btree_iterator();
    auto weights = create_iterators(btree_iterators, bitvectors, use_bit_vector_when_available, tfmd, strict);
    if constexpr (!SearchType::require_btree_iterators) {
        auto multi_term = !btree_iterators.empty() ?
                SearchType::create(tfmd, (filter_search || field_is_filter), std::move(weights), std::move(btree_iterators))
                : std::unique_ptr<SearchIterator>();
        return combine_iterators(std::move(multi_term), std::move(bitvectors), strict);
    } else {
        // In this case we should only have btree iterators.
        assert(btree_iterators.size() == _terms.size());
        assert(weights.index() == 0);
        return SearchType::create(tfmd, field_is_filter, std::get<0>(weights).get(), std::move(btree_iterators));
    }
}

template <typename PostingStoreType, typename SearchType>
std::unique_ptr<queryeval::SearchIterator>
DirectMultiTermBlueprint<PostingStoreType, SearchType>::createLeafSearch(const fef::TermFieldMatchDataArray &tfmda) const
{
    assert(tfmda.size() == 1);
    assert(getState().numFields() == 1);
    return create_search_helper<SearchType::filter_search>(tfmda, strict());
}

template <typename PostingStoreType, typename SearchType>
std::unique_ptr<queryeval::SearchIterator>
DirectMultiTermBlueprint<PostingStoreType, SearchType>::createFilterSearch(FilterConstraint) const
{
    assert(getState().numFields() == 1);
    auto wrapper = std::make_unique<FilterWrapper>(getState().numFields());
    wrapper->wrap(create_search_helper<true>(wrapper->tfmda(), strict()));
    return wrapper;
}

template <typename PostingStoreType, typename SearchType>
queryeval::FlowStats
DirectMultiTermBlueprint<PostingStoreType, SearchType>::calculate_flow_stats(uint32_t docid_limit) const
{
    using OrFlow = search::queryeval::OrFlow;
    struct MyAdapter {
        uint32_t docid_limit;
        MyAdapter(uint32_t docid_limit_in) noexcept : docid_limit(docid_limit_in) {}
        double estimate(const IDirectPostingStore::LookupResult &term) const noexcept {
            return abs_to_rel_est(term.posting_size, docid_limit);
        }
        double cost(const IDirectPostingStore::LookupResult &term) const noexcept {
            double rel_est = abs_to_rel_est(term.posting_size, docid_limit);
            return search::queryeval::flow::btree_cost(rel_est);
        }
        double strict_cost(const IDirectPostingStore::LookupResult &term) const noexcept {
            double rel_est = abs_to_rel_est(term.posting_size, docid_limit);
            return search::queryeval::flow::btree_strict_cost(rel_est);
        }
    };
    double est = OrFlow::estimate_of(MyAdapter(docid_limit), _terms);
    // Iterator benchmarking has shown that non-strict cost is different for attributes
    // that support using a reverse hash filter (see use_hash_filter()).
    // Program used: searchlib/src/tests/queryeval/iterator_benchmark
    // Tests: analyze_and_with_filter_vs_in(), analyze_and_with_filter_vs_in_array()
    double non_strict_cost = (SearchType::supports_hash_filter && !_iattr.hasMultiValue())
            ? queryeval::flow::reverse_hash_lookup()
            : OrFlow::cost_of(MyAdapter(docid_limit), _terms, false);
    return {est, non_strict_cost, OrFlow::cost_of(MyAdapter(docid_limit), _terms, true) + queryeval::flow::heap_cost(est, _terms.size())};
}

}
