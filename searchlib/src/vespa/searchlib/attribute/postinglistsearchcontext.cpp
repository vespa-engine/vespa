// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "postinglistsearchcontext.h"
#include "postinglistsearchcontext.hpp"
#include "attributeiterators.hpp"
#include "diversity.hpp"
#include <vespa/vespalib/btree/btreeiterator.hpp>

namespace search::attribute {

using vespalib::btree::BTreeNode;

PostingListSearchContext::
PostingListSearchContext(const IEnumStoreDictionary& dictionary,
                         uint32_t docIdLimit,
                         uint64_t numValues,
                         bool hasWeight,
                         uint32_t minBvDocFreq,
                         bool useBitVector,
                         const ISearchContext &baseSearchCtx)
    : _dictionary(dictionary),
      _frozenDictionary(_dictionary.get_posting_dictionary().getFrozenView()),
      _lowerDictItr(BTreeNode::Ref(), _frozenDictionary.getAllocator()),
      _upperDictItr(BTreeNode::Ref(), _frozenDictionary.getAllocator()),
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
      _minBvDocFreq(minBvDocFreq),
      _gbv(nullptr),
      _baseSearchCtx(baseSearchCtx)
{
}


PostingListSearchContext::~PostingListSearchContext() = default;


void
PostingListSearchContext::lookupTerm(const vespalib::datastore::EntryComparator &comp)
{
    _lowerDictItr.lower_bound(_frozenDictionary.getRoot(), EnumIndex(), comp);
    _upperDictItr = _lowerDictItr;
    if (_upperDictItr.valid() && !comp.less(EnumIndex(), _upperDictItr.getKey())) {
        ++_upperDictItr;
        _uniqueValues = 1u;
    }
}


void
PostingListSearchContext::lookupRange(const vespalib::datastore::EntryComparator &low,
                                      const vespalib::datastore::EntryComparator &high)
{
    _lowerDictItr.lower_bound(_frozenDictionary.getRoot(), EnumIndex(), low);
    _upperDictItr = _lowerDictItr;
    if (_upperDictItr.valid() && !high.less(EnumIndex(), _upperDictItr.getKey())) {
        _upperDictItr.seekPast(EnumIndex(), high);
    }
    _uniqueValues = _upperDictItr - _lowerDictItr;
}


void
PostingListSearchContext::lookupSingle()
{
    if (_lowerDictItr.valid()) {
        _pidx = vespalib::datastore::EntryRef(_lowerDictItr.getData());
    }
}

template class PostingListSearchContextT<vespalib::btree::BTreeNoLeafData>;
template class PostingListSearchContextT<int32_t>;
template class PostingListFoldedSearchContextT<vespalib::btree::BTreeNoLeafData>;
template class PostingListFoldedSearchContextT<int32_t>;

}
