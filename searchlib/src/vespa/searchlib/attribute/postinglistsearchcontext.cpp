// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
      _frozenDictionary(_dictionary.get_has_btree_dictionary() ? _dictionary.get_posting_dictionary().getFrozenView() : FrozenDictionary()),
      _lowerDictItr(_dictionary.get_has_btree_dictionary() ? DictionaryConstIterator(BTreeNode::Ref(), _frozenDictionary.getAllocator()) : DictionaryConstIterator()),
      _upperDictItr(_dictionary.get_has_btree_dictionary() ? DictionaryConstIterator(BTreeNode::Ref(), _frozenDictionary.getAllocator()) : DictionaryConstIterator()),
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
      _bv(nullptr),
      _baseSearchCtx(baseSearchCtx)
{
}


PostingListSearchContext::~PostingListSearchContext() = default;


void
PostingListSearchContext::lookupTerm(const vespalib::datastore::EntryComparator &comp)
{
    auto lookup_result = _dictionary.find_posting_list(comp, _frozenDictionary.getRoot());
    if (lookup_result.first.valid()) {
        _pidx = lookup_result.second;
        _uniqueValues = 1u;
    }
}


void
PostingListSearchContext::lookupRange(const vespalib::datastore::EntryComparator &low,
                                      const vespalib::datastore::EntryComparator &high)
{
    if (!_dictionary.get_has_btree_dictionary()) {
        _uniqueValues = 2; // Avoid zero and single value optimizations, use filtering
        return;
    }
    _lowerDictItr.lower_bound(_frozenDictionary.getRoot(), AtomicEntryRef(), low);
    _upperDictItr = _lowerDictItr;
    if (_upperDictItr.valid() && !high.less(EnumIndex(), _upperDictItr.getKey().load_acquire())) {
        _upperDictItr.seekPast(AtomicEntryRef(), high);
    }
    _uniqueValues = _upperDictItr - _lowerDictItr;
}


void
PostingListSearchContext::lookupSingle()
{
    if (_lowerDictItr.valid()) {
        _pidx = _lowerDictItr.getData().load_acquire();
    }
}

template class PostingListSearchContextT<vespalib::btree::BTreeNoLeafData>;
template class PostingListSearchContextT<int32_t>;
template class PostingListFoldedSearchContextT<vespalib::btree::BTreeNoLeafData>;
template class PostingListFoldedSearchContextT<int32_t>;

}
