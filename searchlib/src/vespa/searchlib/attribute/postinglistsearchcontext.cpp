// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "postinglistsearchcontext.h"
#include "postinglistsearchcontext.hpp"
#include "attributeiterators.hpp"
#include "diversity.hpp"
#include <vespa/searchlib/btree/btreeiterator.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.posting_list_search_context");


namespace search {

namespace attribute {

using btree::BTreeNode;

PostingListSearchContext::
PostingListSearchContext(const Dictionary &dictionary,
                         uint32_t docIdLimit,
                         uint64_t numValues,
                         bool hasWeight,
                         const EnumStoreBase &esb,
                         uint32_t minBvDocFreq,
                         bool useBitVector)
    : _frozenDictionary(dictionary.getFrozenView()),
      _lowerDictItr(BTreeNode::Ref(), dictionary.getAllocator()),
      _upperDictItr(BTreeNode::Ref(), dictionary.getAllocator()),
      _uniqueValues(0u),
      _docIdLimit(docIdLimit),
      _dictSize(_frozenDictionary.size()),
      _numValues(numValues),
      _hasWeight(hasWeight),
      _useBitVector(useBitVector),
      _pidx(),
      _frozenRoot(),
      _FSTC(0.0),
      _PLSTC(0.0),
      _esb(esb),
      _minBvDocFreq(minBvDocFreq),
      _gbv(nullptr)
{
}


PostingListSearchContext::~PostingListSearchContext()
{
}


void
PostingListSearchContext::lookupTerm(const EnumStoreComparator &comp)
{
    _lowerDictItr.lower_bound(_frozenDictionary.getRoot(), EnumIndex(), comp);
    _upperDictItr = _lowerDictItr;
    if (_upperDictItr.valid() && !comp(EnumIndex(), _upperDictItr.getKey())) {
        ++_upperDictItr;
        _uniqueValues = 1u;
    }
}


void
PostingListSearchContext::lookupRange(const EnumStoreComparator &low,
                                      const EnumStoreComparator &high)
{
    _lowerDictItr.lower_bound(_frozenDictionary.getRoot(), EnumIndex(), low);
    _upperDictItr = _lowerDictItr;
    if (_upperDictItr.valid() && !high(EnumIndex(), _upperDictItr.getKey())) {
        _upperDictItr.seekPast(EnumIndex(), high);
    }
    _uniqueValues = _upperDictItr - _lowerDictItr;
}


void
PostingListSearchContext::lookupSingle()
{
    if (_lowerDictItr.valid()) {
        _pidx = _lowerDictItr.getData();
    }
}

template class PostingListSearchContextT<btree::BTreeNoLeafData>;
template class PostingListSearchContextT<int32_t>;
template class PostingListFoldedSearchContextT<btree::BTreeNoLeafData>;
template class PostingListFoldedSearchContextT<int32_t>;


} // namespace attribute

} // namespace search
