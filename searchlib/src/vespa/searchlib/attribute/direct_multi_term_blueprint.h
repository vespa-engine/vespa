// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_object_visitor.h"
#include "i_direct_posting_store.h"
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchlib/common/matching_elements_fields.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/flow_tuning.h>
#include <vespa/searchlib/queryeval/field_spec.h>
#include <vespa/searchlib/queryeval/matching_elements_search.h>
#include <variant>

namespace search::queryeval { class SearchIterator; }

namespace search::attribute {

/**
 * Blueprint used for multi-term query operators as InTerm, WeightedSetTerm or DotProduct
 * over an attribute which supports the IDocidPostingStore or IDocidWithWeightPostingStore interface.
 *
 * This uses access to low-level posting lists, which speeds up query execution.
 */
template <typename PostingStoreType, typename SearchType>
class DirectMultiTermBlueprint : public queryeval::ComplexLeafBlueprint
{
private:
    std::vector<int32_t>                           _weights;
    std::vector<IDirectPostingStore::LookupResult> _terms;
    const IAttributeVector                        &_iattr;
    const PostingStoreType                        &_attr;
    vespalib::datastore::EntryRef                  _dictionary_snapshot;

    using IteratorType = typename PostingStoreType::IteratorType;
    using IteratorWeights = std::variant<std::reference_wrapper<const std::vector<int32_t>>, std::vector<int32_t>>;

    bool use_hash_filter(bool strict) const;

    IteratorWeights create_iterators(std::vector<IteratorType>& btree_iterators,
                                     std::vector<std::unique_ptr<queryeval::SearchIterator>>& bitvectors,
                                     bool use_bitvector_when_available,
                                     fef::TermFieldMatchData& tfmd, bool strict) const;

    std::unique_ptr<queryeval::SearchIterator> combine_iterators(std::unique_ptr<queryeval::SearchIterator> multi_term_iterator,
                                                                 std::vector<std::unique_ptr<queryeval::SearchIterator>>&& bitvectors,
                                                                 bool strict) const;

    template <bool filter_search>
    std::unique_ptr<queryeval::SearchIterator> create_search_helper(const fef::TermFieldMatchDataArray& tfmda,
                                                                    bool strict) const;

public:
    DirectMultiTermBlueprint(const queryeval::FieldSpec &field, const IAttributeVector &iattr, const PostingStoreType &attr, size_t size_hint);
    ~DirectMultiTermBlueprint() override;

    void addTerm(const IDirectPostingStore::LookupKey & key, int32_t weight, HitEstimate & estimate) {
        IDirectPostingStore::LookupResult result = _attr.lookup(key, _dictionary_snapshot);
        HitEstimate childEst(result.posting_size, (result.posting_size == 0));
        if (!childEst.empty) {
            if (estimate.empty) {
                estimate = childEst;
            } else {
                estimate.estHits += childEst.estHits;
            }
            _weights.push_back(weight);
            _terms.push_back(result);
        }
    }
    void complete(HitEstimate estimate) {
        setEstimate(estimate);
    }

    queryeval::FlowStats calculate_flow_stats(uint32_t docid_limit) const override {
        using OrFlow = search::queryeval::OrFlow;
        struct MyAdapter {
            uint32_t docid_limit;
            MyAdapter(uint32_t docid_limit_in) noexcept : docid_limit(docid_limit_in) {}
            double estimate(const IDirectPostingStore::LookupResult &term) const noexcept {
                return abs_to_rel_est(term.posting_size, docid_limit);
            }
            double cost(const IDirectPostingStore::LookupResult &) const noexcept {
                return search::queryeval::flow::btree_cost();
            }
            double strict_cost(const IDirectPostingStore::LookupResult &term) const noexcept {
                double rel_est = abs_to_rel_est(term.posting_size, docid_limit);
                return search::queryeval::flow::btree_strict_cost(rel_est);
            }
        };
        double est = OrFlow::estimate_of(MyAdapter(docid_limit), _terms);
        return {est, OrFlow::cost_of(MyAdapter(docid_limit), _terms, false),
                OrFlow::cost_of(MyAdapter(docid_limit), _terms, true) + queryeval::flow::heap_cost(est, _terms.size())};
    }

    std::unique_ptr<queryeval::SearchIterator> createLeafSearch(const fef::TermFieldMatchDataArray &tfmda, bool) const override;

    std::unique_ptr<queryeval::SearchIterator> createFilterSearch(bool strict, FilterConstraint constraint) const override;
    std::unique_ptr<queryeval::MatchingElementsSearch> create_matching_elements_search(const MatchingElementsFields &fields) const override {
        if (fields.has_field(_iattr.getName())) {
            return queryeval::MatchingElementsSearch::create(_iattr, _dictionary_snapshot, vespalib::ConstArrayRef<IDirectPostingStore::LookupResult>(_terms));
        } else {
            return {};
        }
    }
    void visitMembers(vespalib::ObjectVisitor& visitor) const override {
        LeafBlueprint::visitMembers(visitor);
        visit_attribute(visitor, _iattr);
    }
};

}

