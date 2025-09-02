// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "imported_search_context.h"
#include "bitvector_search_cache.h"
#include "imported_attribute_vector.h"
#include "reference_attribute.h"
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/queryeval/executeinfo.h>
#include "attributeiterators.hpp"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.imported_search_context");

using vespalib::datastore::EntryRef;
using search::queryeval::EmptySearch;
using search::queryeval::SearchIterator;
using search::attribute::ISearchContext;
using search::attribute::ReferenceAttribute;
using search::AttributeVector;

// Classes used to map from target lid to source lids
using ReverseMappingRefs = ReferenceAttribute::ReverseMappingRefs;
using ReverseMapping = ReferenceAttribute::ReverseMapping;


namespace search::attribute {

ImportedSearchContext::ImportedSearchContext(
        std::unique_ptr<QueryTermSimple> term,
        const SearchContextParams& params,
        const ImportedAttributeVector& imported_attribute,
        const IAttributeVector &target_attribute)
    : _imported_attribute(imported_attribute),
      _queryTerm(term->getTerm()),
      _useSearchCache(_imported_attribute.getSearchCache()),
      _searchCacheLookup((_useSearchCache ? _imported_attribute.getSearchCache()->find(_queryTerm) :
                          std::shared_ptr<BitVectorSearchCache::Entry>())),
      // Fallback in case we need to insert entry into cache, and no guard has been supplied in search context params.
      // The latter will only happen for tests and docsum fetching.
      _dmsReadGuardFallback((_useSearchCache && !_searchCacheLookup && (params.metaStoreReadGuard() == nullptr))
                    ? _imported_attribute.getDocumentMetaStore()->getReadGuard()
                    : IDocumentMetaStoreContext::IReadGuard::SP()),
      _reference_attribute(*_imported_attribute.getReferenceAttribute()),
      _target_attribute(target_attribute),
      _target_search_context(_target_attribute.createSearchContext(std::move(term), params)),
      _targetLids(_reference_attribute.getTargetLids()),
      _target_docid_limit(_target_search_context->get_committed_docid_limit()),
      _merger(_reference_attribute.getCommittedDocIdLimit()),
      _params(params),
      _zero_hits(false)
{
}

ImportedSearchContext::~ImportedSearchContext() = default;

uint32_t
ImportedSearchContext::calc_approx_hits(uint32_t target_approx_hits) const
{
    uint32_t docid_limit = _targetLids.size();
    uint32_t target_docid_limit = _target_attribute.getCommittedDocIdLimit();
    double approx_hits_multiplier = static_cast<double>(docid_limit) / target_docid_limit;
    if (approx_hits_multiplier < 1.0) {
        approx_hits_multiplier = 1.0;
    }
    uint64_t approx_hits = target_approx_hits * approx_hits_multiplier;
    if (approx_hits > docid_limit) {
        approx_hits = docid_limit;
    }
    return approx_hits;
}

uint32_t
ImportedSearchContext::calc_exact_hits() const
{
    uint32_t docid_limit = _targetLids.size();
    uint32_t target_docid_limit = _target_attribute.getCommittedDocIdLimit();
    auto reverse_mapping_refs = _reference_attribute.getReverseMappingRefs();
    auto& reverse_mapping = _reference_attribute.getReverseMapping();
    if (target_docid_limit > reverse_mapping_refs.size()) {
        target_docid_limit = reverse_mapping_refs.size();
    }
    fef::TermFieldMatchData matchData;
    auto it = _target_search_context->createIterator(&matchData, true);
    uint64_t sum_hits = 0;
    it->initRange(1, target_docid_limit);
    for (uint32_t lid = it->seekFirst(1); !it->isAtEnd(); lid = it->seekNext(lid + 1)) {
        EntryRef ref = reverse_mapping_refs[lid].load_acquire();
        if (__builtin_expect(ref.valid(), true)) {
            sum_hits += reverse_mapping.frozenSize(ref);
        }
    }
    if (sum_hits > docid_limit) {
        sum_hits = docid_limit;
    }
    return sum_hits;
}

HitEstimate
ImportedSearchContext::calc_hit_estimate() const
{
    uint32_t target_est_hits = _target_search_context->calc_hit_estimate().est_hits();
    if (target_est_hits == 0) {
        _zero_hits.store(true, std::memory_order_relaxed);
        return HitEstimate(0);
    }
    if (!_target_attribute.getIsFastSearch()) {
        return HitEstimate::unknown(_reference_attribute.getNumDocs());
    }
    if (target_est_hits >= MIN_TARGET_HITS_FOR_APPROXIMATION) {
        return HitEstimate(calc_approx_hits(target_est_hits));
    } else {
        return HitEstimate(calc_exact_hits());
    }
}

std::unique_ptr<queryeval::SearchIterator>
ImportedSearchContext::createIterator(fef::TermFieldMatchData* matchData, bool strict) {
    if (_zero_hits.load(std::memory_order_relaxed)) {
        return std::make_unique<EmptySearch>();
    }
    if (_searchCacheLookup) {
        return BitVectorIterator::create(_searchCacheLookup->bitVector.get(), _searchCacheLookup->docIdLimit,
                                         *matchData, this, strict, false, false);
    }
    if (_merger.hasArray()) {
        if (_merger.emptyArray()) {
            return SearchIterator::UP(new EmptySearch());
        } else {
            using Posting = vespalib::btree::BTreeKeyData<uint32_t, int32_t>;
            using DocIt = ArrayIterator<Posting>;
            DocIt postings;
            auto array = _merger.getArray();
            postings.set(&array[0], &array[array.size()]);
            if (_target_attribute.getIsFilter()) {
                return std::make_unique<FilterAttributePostingListIteratorT<DocIt>>(*this, matchData, postings);
            } else {
                return std::make_unique<AttributePostingListIteratorT<DocIt>>(*this, matchData, postings);
            }
        }
    } else if (_merger.hasBitVector()) {
        return BitVectorIterator::create(_merger.getBitVector(), _merger.getDocIdLimit(), *matchData, this,
                                         strict, false, false);
    }
    if (_params.useBitVector()) {
        if (!strict) {
            return std::make_unique<FilterAttributeIteratorT<ImportedSearchContext>>(*this, matchData);
        } else {
            return std::make_unique<FilterAttributeIteratorStrict<ImportedSearchContext>>(*this, matchData);
        }
    }
    if (!strict) {
        return std::make_unique<AttributeIteratorT<ImportedSearchContext>>(*this, matchData);
    } else {
        return std::make_unique<AttributeIteratorStrict<ImportedSearchContext>>(*this, matchData);
    }
}

namespace {

struct WeightedRef {
    EntryRef revMapIdx;
    int32_t  weight;

    WeightedRef(EntryRef revMapIdx_, int32_t weight_) noexcept
        : revMapIdx(revMapIdx_),
          weight(weight_)
    {
    }
};

struct TargetWeightedResult {
    std::vector<WeightedRef> weightedRefs;
    size_t sizeSum;

    TargetWeightedResult()
        : weightedRefs(),
          sizeSum(0)
    {}
    static TargetWeightedResult
    getResult(ReverseMappingRefs reverseMappingRefs, const ReverseMapping &reverseMapping,
              ISearchContext &target_search_context, uint32_t committedDocIdLimit) __attribute__((noinline));
};

class ReverseMappingBitVector
{
    const ReverseMapping &_reverseMapping;
    EntryRef _revMapIdx;
public:
    ReverseMappingBitVector(const ReverseMapping &reverseMapping, EntryRef revMapIdx) noexcept
        : _reverseMapping(reverseMapping),
          _revMapIdx(revMapIdx)
    {}
    ~ReverseMappingBitVector() = default;

    template <typename Func>
    void foreach_key(Func func) const {
        _reverseMapping.foreach_frozen_key(_revMapIdx, [func](uint32_t lid) { func(lid); });
    }
};

struct TargetResult {
    static void
    getResult(ReverseMappingRefs reverseMappingRefs, const ReverseMapping &reverseMapping,
              ISearchContext &target_search_context, uint32_t committedDocIdLimit,
              PostingListMerger<int32_t> & merger) __attribute__((noinline));
};

TargetWeightedResult
TargetWeightedResult::getResult(ReverseMappingRefs reverseMappingRefs, const ReverseMapping &reverseMapping,
                                ISearchContext &target_search_context, uint32_t committedDocIdLimit)
{
    TargetWeightedResult targetResult;
    fef::TermFieldMatchData matchData;
    auto it = target_search_context.createIterator(&matchData, true);
    uint32_t docIdLimit = reverseMappingRefs.size();
    if (docIdLimit > committedDocIdLimit) {
        docIdLimit = committedDocIdLimit;
    }
    it->initRange(1, docIdLimit);
    for (uint32_t lid = it->seekFirst(1); !it->isAtEnd(); lid = it->seekNext(lid+1)) {
        EntryRef revMapIdx = reverseMappingRefs[lid].load_acquire();
        if (__builtin_expect(revMapIdx.valid(), true)) {
            uint32_t size = reverseMapping.frozenSize(revMapIdx);
            targetResult.sizeSum += size;
            it->doUnpack(lid);
            int32_t weight = matchData.getWeight();
            targetResult.weightedRefs.emplace_back(revMapIdx, weight);
        }
    }
    return targetResult;
}

void
TargetResult::getResult(ReverseMappingRefs reverseMappingRefs, const ReverseMapping &reverseMapping,
                        ISearchContext &target_search_context, uint32_t committedDocIdLimit,
                        PostingListMerger<int32_t> & merger)
{
    fef::TermFieldMatchData matchData;
    auto it = target_search_context.createIterator(&matchData, true);
    uint32_t docIdLimit = reverseMappingRefs.size();
    if (docIdLimit > committedDocIdLimit) {
        docIdLimit = committedDocIdLimit;
    }
    it->initRange(1, docIdLimit);
    for (uint32_t lid = it->seekFirst(1); !it->isAtEnd(); lid = it->seekNext(lid+1)) {
        EntryRef revMapIdx = reverseMappingRefs[lid].load_acquire();
        if (__builtin_expect(revMapIdx.valid(), true)) {
            merger.addToBitVector(ReverseMappingBitVector(reverseMapping, revMapIdx));
        }
    }
}

class ReverseMappingPostingList
{
    const ReverseMapping &_reverseMapping;
    EntryRef _revMapIdx;
    int32_t _weight;
public:
    ReverseMappingPostingList(const ReverseMapping &reverseMapping, EntryRef revMapIdx, int32_t weight)
        : _reverseMapping(reverseMapping),
          _revMapIdx(revMapIdx),
          _weight(weight)
    {}
    ~ReverseMappingPostingList() { }
    template <typename Func>
    void foreach(Func func) const {
        int32_t weight = _weight;
        _reverseMapping.foreach_frozen_key(_revMapIdx, [func, weight](uint32_t lid) { func(lid, weight); });
    }

};

}

ImportedSearchContext::MergedPostingsType
ImportedSearchContext::select_merged_postings_type(bool is_filter)
{
    if (!is_filter) {
        return MergedPostingsType::WEIGHTED_ARRAY;
    }
    /*
     * Select weighted array if the estimated number of hits is low to minimize memory usage. If lid space is 80M, and
     * we estimate 100 hits then a bitvector will use 10MB while a weighted array will use 800 bytes. Always using
     * bitvectors can be a problem when we have queries with many terms (e.g. queries using weightedset operator with
     * 1000 or more terms).
     */
    auto hit_estimate = calc_hit_estimate();
    auto est_hits = hit_estimate.est_hits();
    uint32_t docid_limit = _targetLids.size();
    uint32_t bitvector_limit = 1 + docid_limit / bitvector_limit_divisor;
    if (est_hits < bitvector_limit) {
        return MergedPostingsType::WEIGHTED_ARRAY;
    }
    return MergedPostingsType::BITVECTOR;
}

void
ImportedSearchContext::makeMergedPostings(MergedPostingsType merged_postings_type)
{
    uint32_t committedTargetDocIdLimit = _target_attribute.getCommittedDocIdLimit();
    std::atomic_thread_fence(std::memory_order_acquire);
    const auto &reverseMapping = _reference_attribute.getReverseMapping();
    if (merged_postings_type == MergedPostingsType::BITVECTOR) {
        _merger.allocBitVector();
        TargetResult::getResult(_reference_attribute.getReverseMappingRefs(),
                                _reference_attribute.getReverseMapping(),
                                *_target_search_context, committedTargetDocIdLimit, _merger);
    } else {
        TargetWeightedResult targetResult(TargetWeightedResult::getResult(_reference_attribute.getReverseMappingRefs(),
                                                                          _reference_attribute.getReverseMapping(),
                                                                          *_target_search_context,
                                                                          committedTargetDocIdLimit));
        _merger.reserveArray(targetResult.weightedRefs.size(), targetResult.sizeSum);
        for (const auto &weightedRef : targetResult.weightedRefs) {
            _merger.addToArray(ReverseMappingPostingList(reverseMapping, weightedRef.revMapIdx, weightedRef.weight));
        }
    }
    _merger.merge();
}

void
ImportedSearchContext::considerAddSearchCacheEntry()
{
    if (_useSearchCache && _merger.hasBitVector()) {
        IDocumentMetaStoreContext::IReadGuard::SP dmsReadGuard = (_params.metaStoreReadGuard() != nullptr)
                ? *_params.metaStoreReadGuard()
                : _dmsReadGuardFallback;
        assert(dmsReadGuard);
        auto cacheEntry = std::make_shared<BitVectorSearchCache::Entry>(std::move(dmsReadGuard), _merger.getBitVectorSP(), _merger.getDocIdLimit());
        _imported_attribute.getSearchCache()->insert(_queryTerm, std::move(cacheEntry));
    }
}

void
ImportedSearchContext::fetchPostings(const queryeval::ExecuteInfo &execInfo, bool strict) {
    if (!_searchCacheLookup) {
        _target_search_context->fetchPostings(execInfo, strict);
        if (!_merger.merge_done() && (strict || (_target_attribute.getIsFastSearch() && execInfo.hit_rate() > 0.01))) {
                makeMergedPostings(select_merged_postings_type(_target_attribute.getIsFilter()));
                considerAddSearchCacheEntry();
        }
    }
}

bool
ImportedSearchContext::valid() const {
    return _target_search_context->valid();
}

Int64Range
ImportedSearchContext::getAsIntegerTerm() const {
    return _target_search_context->getAsIntegerTerm();
}

DoubleRange
ImportedSearchContext::getAsDoubleTerm() const {
    return _target_search_context->getAsDoubleTerm();
}

const QueryTermUCS4 *
ImportedSearchContext::queryTerm() const {
    return _target_search_context->queryTerm();
}

const std::string&
ImportedSearchContext::attributeName() const {
    return _imported_attribute.getName();
}

uint32_t
ImportedSearchContext::get_committed_docid_limit() const noexcept
{
    return _targetLids.size();
}

void
ImportedSearchContext::get_element_ids(uint32_t docid, std::vector<uint32_t>& element_ids) const
{
    return _target_search_context->get_element_ids(getTargetLid(docid), element_ids);
}
void
ImportedSearchContext::and_element_ids_into(uint32_t docid, std::vector<uint32_t>& element_ids) const
{
    return _target_search_context->and_element_ids_into(getTargetLid(docid), element_ids);
}

}
