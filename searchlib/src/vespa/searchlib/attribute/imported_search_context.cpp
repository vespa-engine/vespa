// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bitvector_search_cache.h"
#include "imported_search_context.h"
#include "imported_attribute_vector.h"
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include "attributeiterators.hpp"

using search::datastore::EntryRef;
using search::queryeval::EmptySearch;
using search::queryeval::SearchIterator;
using search::attribute::ISearchContext;
using search::attribute::ReferenceAttribute;
using search::AttributeVector;

using ReverseMappingRefs = ReferenceAttribute::ReverseMappingRefs;
using ReverseMapping = ReferenceAttribute::ReverseMapping;


namespace search::attribute {

ImportedSearchContext::ImportedSearchContext(
        std::unique_ptr<QueryTermSimple> term,
        const SearchContextParams& params,
        const ImportedAttributeVector& imported_attribute)
    : _imported_attribute(imported_attribute),
      _queryTerm(term->getTerm()),
      _useSearchCache(_imported_attribute.getSearchCache().get() != nullptr),
      _searchCacheLookup((_useSearchCache ? _imported_attribute.getSearchCache()->find(_queryTerm) :
                          std::shared_ptr<BitVectorSearchCache::Entry>())),
      _dmsReadGuard((_useSearchCache && !_searchCacheLookup) ? imported_attribute.getDocumentMetaStore()->getReadGuard() :
                    std::unique_ptr<IDocumentMetaStoreContext::IReadGuard>()),
      _reference_attribute(*_imported_attribute.getReferenceAttribute()),
      _target_attribute(*_imported_attribute.getTargetAttribute()),
      _target_search_context(_target_attribute.createSearchContext(std::move(term), params)),
      _referencedLids(_reference_attribute.getReferencedLids()),
      _merger(_reference_attribute.getCommittedDocIdLimit()),
      _fetchPostingsDone(false)
{

}

ImportedSearchContext::~ImportedSearchContext() {
}

unsigned int ImportedSearchContext::approximateHits() const {
    return _reference_attribute.getNumDocs();
}

std::unique_ptr<queryeval::SearchIterator>
ImportedSearchContext::createIterator(fef::TermFieldMatchData* matchData, bool strict) {
    if (_searchCacheLookup) {
        return BitVectorIterator::create(_searchCacheLookup->bitVector.get(), _searchCacheLookup->docIdLimit, *matchData, strict);
    }
    if (_merger.hasArray()) {
        if (_merger.emptyArray()) {
            return SearchIterator::UP(new EmptySearch());
        } else {
            using Posting = btree::BTreeKeyData<uint32_t, int32_t>;
            using DocIt = DocIdIterator<Posting>;
            DocIt postings;
            auto array = _merger.getArray();
            postings.set(&array[0], &array[array.size()]);
            return std::make_unique<AttributePostingListIteratorT<DocIt>>(true, matchData, postings);
        }
    } else if (_merger.hasBitVector()) {
        return BitVectorIterator::create(_merger.getBitVector(), _merger.getDocIdLimit(), *matchData, strict);
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

    WeightedRef(EntryRef revMapIdx_, int32_t weight_)
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
    ReverseMappingBitVector(const ReverseMapping &reverseMapping, EntryRef revMapIdx)
        : _reverseMapping(reverseMapping),
          _revMapIdx(revMapIdx)
    {}
    ~ReverseMappingBitVector() { }

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
        EntryRef revMapIdx = reverseMappingRefs[lid];
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
        EntryRef revMapIdx = reverseMappingRefs[lid];
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

void ImportedSearchContext::makeMergedPostings(bool isFilter)
{
    uint32_t committedTargetDocIdLimit = _target_attribute.getCommittedDocIdLimit();
    std::atomic_thread_fence(std::memory_order_acquire);
    const auto &reverseMapping = _reference_attribute.getReverseMapping();
    if (isFilter) {
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
        assert(_dmsReadGuard);
        auto cacheEntry = std::make_shared<BitVectorSearchCache::Entry>(std::move(_dmsReadGuard), _merger.getBitVectorSP(), _merger.getDocIdLimit());
        _imported_attribute.getSearchCache()->insert(_queryTerm, std::move(cacheEntry));
    }
}

void ImportedSearchContext::fetchPostings(bool strict) {
    assert(!_fetchPostingsDone);
    _fetchPostingsDone = true;
    if (!_searchCacheLookup) {
        _target_search_context->fetchPostings(strict);
        if (strict || _target_attribute.getConfig().fastSearch()) {
            makeMergedPostings(_target_attribute.getConfig().getIsFilter());
            considerAddSearchCacheEntry();
        }
    }
}

bool ImportedSearchContext::valid() const {
    return _target_search_context->valid();
}

Int64Range ImportedSearchContext::getAsIntegerTerm() const {
    return _target_search_context->getAsIntegerTerm();
}

const QueryTermBase& ImportedSearchContext::queryTerm() const {
    return _target_search_context->queryTerm();
}

const vespalib::string& ImportedSearchContext::attributeName() const {
    return _imported_attribute.getName();
}

}
