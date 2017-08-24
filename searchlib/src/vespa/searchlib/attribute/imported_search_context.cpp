// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "imported_search_context.h"
#include "attributeiterators.hpp"
#include "imported_attribute_vector.h"
#include "reference_attribute.h"
#include <vespa/searchcommon/attribute/search_context_params.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/query/queryterm.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include "dociditerator.h"

using search::datastore::EntryRef;
using search::queryeval::EmptySearch;
using search::queryeval::SearchIterator;
using search::attribute::ReferenceAttribute;
using search::AttributeVector;

using ReverseMappingRefs = ReferenceAttribute::ReverseMappingRefs;
using ReverseMapping = ReferenceAttribute::ReverseMapping;
using SearchContext = AttributeVector::SearchContext;


namespace search {
namespace attribute {

ImportedSearchContext::ImportedSearchContext(
        std::unique_ptr<QueryTermSimple> term,
        const SearchContextParams& params,
        const ImportedAttributeVector& imported_attribute)
    : _imported_attribute(imported_attribute),
      _reference_attribute(*_imported_attribute.getReferenceAttribute()),
      _target_attribute(*_imported_attribute.getTargetAttribute()),
      _target_search_context(_target_attribute.getSearch(std::move(term), params)),
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

struct TargetResult {
    std::vector<WeightedRef> weightedRefs;
    size_t sizeSum;

    TargetResult()
        : weightedRefs(),
          sizeSum(0)
    {
    }
};

TargetResult
getTargetResult(ReverseMappingRefs reverseMappingRefs,
                const ReverseMapping &reverseMapping,
                SearchContext &target_search_context)
{
    TargetResult targetResult;
    fef::TermFieldMatchData matchData;
    auto targetItr = target_search_context.createIterator(&matchData, true);
    uint32_t docIdLimit = reverseMappingRefs.size();
    uint32_t lid = 1;
    targetItr->initRange(1, docIdLimit);
    while (lid < docIdLimit) {
        if (targetItr->seek(lid)) {
            EntryRef revMapIdx = reverseMappingRefs[lid];
            if (revMapIdx.valid()) {
                uint32_t size = reverseMapping.frozenSize(revMapIdx);
                targetResult.sizeSum += size;
                targetItr->unpack(lid);
                int32_t weight = matchData.getWeight();
                targetResult.weightedRefs.emplace_back(revMapIdx, weight);
            }
            ++lid;
        } else {
            ++lid;
            uint32_t nextLid = targetItr->getDocId();
            if (nextLid > lid) {
                lid = nextLid;
            }
        }
    }
    return targetResult;
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
    {
    }
    ~ReverseMappingPostingList() { }
    template <typename Func>
    void foreach(Func func) const {
        int32_t weight = _weight;
        _reverseMapping.foreach_frozen_key(_revMapIdx, [func, weight](uint32_t lid) { func(lid, weight); });
    }
};

}

void ImportedSearchContext::makeMergedPostings()
{
    TargetResult targetResult(getTargetResult(_reference_attribute.getReverseMappingRefs(),
                                              _reference_attribute.getReverseMapping(),
                                              *_target_search_context));
    _merger.reserveArray(targetResult.weightedRefs.size(), targetResult.sizeSum);
    const auto &reverseMapping = _reference_attribute.getReverseMapping();
    for (const auto &weightedRef : targetResult.weightedRefs) {
        _merger.addToArray(ReverseMappingPostingList(reverseMapping, weightedRef.revMapIdx, weightedRef.weight));
    }
    _merger.merge();
}

void ImportedSearchContext::fetchPostings(bool strict) {
    assert(!_fetchPostingsDone);
    _fetchPostingsDone = true;
    _target_search_context->fetchPostings(strict);
    if (strict) {
        makeMergedPostings();
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

bool ImportedSearchContext::cmp(DocId docId, int32_t& weight) const {
    return _target_search_context->cmp(_referencedLids[docId], weight);
}

bool ImportedSearchContext::cmp(DocId docId) const {
    return _target_search_context->cmp(_referencedLids[docId]);
}

} // attribute
} // search
