// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bitvector_search_cache.h"
#include "posting_list_merger.h"
#include <vespa/searchcommon/attribute/search_context_params.h>
#include <vespa/searchcommon/attribute/i_search_context.h>
#include <vespa/vespalib/datastore/atomic_value_wrapper.h>
#include <span>

namespace search::fef { class TermFieldMatchData; }
namespace search { class QueryTermSimple; }
namespace search::attribute {

class IAttributeVector;
class ReferenceAttribute;
class ImportedAttributeVector;
class SearchContextParams;

/**
 * Search context exposing iteraton over an imported attribute vector.
 *
 * Iterator doc id matching is performed via the GID->LID indirection of the
 * associated reference attribute. This means that if the _referenced_ document
 * matches the search term, the doc id of the _referring_ document will be
 * considered a match.
 */
class ImportedSearchContext : public ISearchContext {
    using AtomicTargetLid = vespalib::datastore::AtomicValueWrapper<uint32_t>;
    using TargetLids = std::span<const AtomicTargetLid>;
    enum class MergedPostingsType : uint8_t {
        WEIGHTED_ARRAY,
        BITVECTOR
    };
    const ImportedAttributeVector&                  _imported_attribute;
    std::string                                _queryTerm;
    bool                                            _useSearchCache;
    std::shared_ptr<BitVectorSearchCache::Entry>    _searchCacheLookup;
    IDocumentMetaStoreContext::IReadGuard::SP       _dmsReadGuardFallback;
    const ReferenceAttribute&                       _reference_attribute;
    const IAttributeVector                         &_target_attribute;
    std::unique_ptr<ISearchContext>                 _target_search_context;
    TargetLids                                      _targetLids;
    uint32_t                                        _target_docid_limit;
    PostingListMerger<int32_t>                      _merger;
    SearchContextParams                             _params;
    mutable std::atomic<bool>                       _zero_hits;

    static constexpr uint32_t MIN_TARGET_HITS_FOR_APPROXIMATION = 50;

    uint32_t getTargetLid(uint32_t lid) const {
        // Check range to avoid reading memory beyond end of mapping array
        uint32_t target_lid = lid < _targetLids.size() ? _targetLids[lid].load_acquire() : 0u;
        // Check target range
        return target_lid < _target_docid_limit ? target_lid : 0u;
    }

    MergedPostingsType select_merged_postings_type(bool is_filter);
    void makeMergedPostings(MergedPostingsType merged_postings_type);
    void considerAddSearchCacheEntry();
    uint32_t calc_approx_hits(uint32_t target_approx_hits) const;
    uint32_t calc_exact_hits() const;
public:
    ImportedSearchContext(std::unique_ptr<QueryTermSimple> term,
                          const SearchContextParams& params,
                          const ImportedAttributeVector& imported_attribute,
                          const attribute::IAttributeVector &target_attribute);
    ~ImportedSearchContext() override;

    /*
     * Each array element in weighted array uses 8 bytes, thus a weighted array with docid_limit / 64 elements
     * would use the same amount of memory as the bitvector. The divisor is adjusted to account for extra memory usage
     * by an addition array (_startPos in posting list merger) and for cpu time spent doing the sorting.
     */
    static constexpr uint32_t bitvector_limit_divisor = 150;

    std::unique_ptr<queryeval::SearchIterator>
    createIterator(fef::TermFieldMatchData* matchData, bool strict) override;
    HitEstimate calc_hit_estimate() const override;
    void fetchPostings(const queryeval::ExecuteInfo &execInfo, bool strict) override;
    bool valid() const override;
    Int64Range getAsIntegerTerm() const override;
    DoubleRange getAsDoubleTerm() const override;
    const QueryTermUCS4 * queryTerm() const override;
    const std::string& attributeName() const override;

    using DocId = uint32_t;

    int32_t find(DocId docId, int32_t elemId, int32_t& weight) const {
        return _target_search_context->find(getTargetLid(docId), elemId, weight);
    }

    int32_t find(DocId docId, int32_t elemId) const {
        return _target_search_context->find(getTargetLid(docId), elemId);
    }

    int32_t onFind(uint32_t docId, int32_t elemId, int32_t &weight) const override { return find(docId, elemId, weight); }
    int32_t onFind(uint32_t docId, int32_t elemId) const override { return find(docId, elemId); }

    const ReferenceAttribute& attribute() const noexcept { return _reference_attribute; }

    const ISearchContext &target_search_context() const noexcept {
        return *_target_search_context;
    }
    uint32_t get_committed_docid_limit() const noexcept override;
    void get_element_ids(uint32_t docid, std::vector<uint32_t>& element_ids) const override;
    void and_element_ids_into(uint32_t docid, std::vector<uint32_t>& element_ids) const override;
};

}
