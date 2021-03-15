// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "postinglistattribute.h"
#include "loadednumericvalue.h"
#include "enumcomparator.h"
#include <vespa/vespalib/util/array.hpp>

namespace search {

using attribute::LoadedNumericValue;

template <typename P>
PostingListAttributeBase<P>::
PostingListAttributeBase(AttributeVector &attr,
                         IEnumStore &enumStore)
    : attribute::IPostingListAttributeBase(),
      _postingList(enumStore.get_dictionary(), attr.getStatus(),
                   attr.getConfig()),
      _attr(attr),
      _dictionary(enumStore.get_dictionary())
{ }

template <typename P>
PostingListAttributeBase<P>::~PostingListAttributeBase() = default;

template <typename P>
void
PostingListAttributeBase<P>::clearAllPostings()
{
    _postingList.clearBuilder();
    _attr.incGeneration(); // Force freeze
    auto clearer = [this](EntryRef posting_idx)
                   {
                       _postingList.clear(posting_idx);
                   };
    _dictionary.clear_all_posting_lists(clearer);
    _attr.incGeneration(); // Force freeze
}


template <typename P>
void
PostingListAttributeBase<P>::handle_load_posting_lists_and_update_enum_store(enumstore::EnumeratedPostingsLoader& loader)
{
    clearAllPostings();
    uint32_t docIdLimit = _attr.getNumDocs();
    EntryRef newIndex;
    PostingChange<P> postings;
    const auto& loaded_enums = loader.get_loaded_enums();
    if (loaded_enums.empty()) {
        return;
    }
    uint32_t preve = 0;
    uint32_t refCount = 0;

    auto& dict = _dictionary.get_posting_dictionary();
    auto itr = dict.begin();
    auto posting_itr = itr;
    assert(itr.valid());
    for (const auto& elem : loaded_enums) {
        if (preve != elem.getEnum()) {
            assert(preve < elem.getEnum());
            loader.set_ref_count(itr.getKey(), refCount);
            refCount = 0;
            while (preve != elem.getEnum()) {
                ++itr;
                assert(itr.valid());
                ++preve;
            }
            assert(itr.valid());
            if (loader.is_folded_change(posting_itr.getKey(), itr.getKey())) {
                postings.removeDups();
                newIndex = EntryRef();
                _postingList.apply(newIndex,
                                   &postings._additions[0],
                                   &postings._additions[0] +
                                   postings._additions.size(),
                                   &postings._removals[0],
                                   &postings._removals[0] +
                                   postings._removals.size());
                posting_itr.writeData(newIndex.ref());
                while (posting_itr != itr) {
                    ++posting_itr;
                }
                postings.clear();
            }
        }
        assert(refCount < std::numeric_limits<uint32_t>::max());
        ++refCount;
        assert(elem.getDocId() < docIdLimit);
        (void) docIdLimit;
        postings.add(elem.getDocId(), elem.getWeight());
    }
    assert(refCount != 0);
    loader.set_ref_count(itr.getKey(), refCount);
    postings.removeDups();
    newIndex = EntryRef();
    _postingList.apply(newIndex,
                       &postings._additions[0],
                       &postings._additions[0] + postings._additions.size(),
                       &postings._removals[0],
                       &postings._removals[0] + postings._removals.size());
    posting_itr.writeData(newIndex.ref());
    loader.free_unused_values();
}

template <typename P>
void
PostingListAttributeBase<P>::updatePostings(PostingMap &changePost,
                                            vespalib::datastore::EntryComparator &cmp)
{
    for (auto& elem : changePost) {
        EnumIndex idx = elem.first.getEnumIdx();
        auto& change = elem.second;
        change.removeDups();
        auto updater= [this, &change](EntryRef posting_idx) -> EntryRef
                      {
                          _postingList.apply(posting_idx,
                                             &change._additions[0],
                                             &change._additions[0] + change._additions.size(),
                                             &change._removals[0],
                                             &change._removals[0] + change._removals.size());
                          return posting_idx;
                      };
        _dictionary.update_posting_list(idx, cmp, updater);
    }
}

template <typename P>
bool
PostingListAttributeBase<P>::forwardedOnAddDoc(DocId doc,
                                               size_t wantSize,
                                               size_t wantCapacity)
{
    if (!_postingList._enableBitVectors) {
        return false;
    }
    if (doc >= wantSize) {
        wantSize = doc + 1;
    }
    if (doc >= wantCapacity) {
        wantCapacity = doc + 1;
    }
    return _postingList.resizeBitVectors(wantSize, wantCapacity);
}

template <typename P>
void
PostingListAttributeBase<P>::
clearPostings(attribute::IAttributeVector::EnumHandle eidx,
              uint32_t fromLid,
              uint32_t toLid,
              vespalib::datastore::EntryComparator &cmp)
{
    PostingChange<P> postings;

    for (uint32_t lid = fromLid; lid < toLid; ++lid) {
        postings.remove(lid);
    }

    EntryRef er(eidx);
    auto updater = [this, &postings](EntryRef posting_idx) -> EntryRef
                   {
                       _postingList.apply(posting_idx,
                                          &postings._additions[0],
                                          &postings._additions[0] + postings._additions.size(),
                                          &postings._removals[0],
                                          &postings._removals[0] + postings._removals.size());
                       return posting_idx;
                   };
    _dictionary.update_posting_list(er, cmp, updater);
}

template <typename P>
void
PostingListAttributeBase<P>::forwardedShrinkLidSpace(uint32_t newSize)
{
    (void) _postingList.resizeBitVectors(newSize, newSize);
}

template <typename P>
vespalib::MemoryUsage
PostingListAttributeBase<P>::getMemoryUsage() const
{
    return _postingList.getMemoryUsage();
}

template <typename P, typename LoadedVector, typename LoadedValueType,
          typename EnumStoreType>
PostingListAttributeSubBase<P, LoadedVector, LoadedValueType, EnumStoreType>::
PostingListAttributeSubBase(AttributeVector &attr,
                            EnumStore &enumStore)
    : Parent(attr, enumStore),
      _es(enumStore)
{
}

template <typename P, typename LoadedVector, typename LoadedValueType,
          typename EnumStoreType>
PostingListAttributeSubBase<P, LoadedVector, LoadedValueType, EnumStoreType>::
~PostingListAttributeSubBase() = default;

template <typename P, typename LoadedVector, typename LoadedValueType,
          typename EnumStoreType>
void
PostingListAttributeSubBase<P, LoadedVector, LoadedValueType, EnumStoreType>::
handle_load_posting_lists(LoadedVector& loaded)
{
    if constexpr (!std::is_same_v<LoadedVector, NoLoadedVector>) {
        clearAllPostings();
        EntryRef newIndex;
        PostingChange<P> postings;
        uint32_t docIdLimit = _attr.getNumDocs();
        _postingList.resizeBitVectors(docIdLimit, docIdLimit);
        if ( ! loaded.empty() ) {
            vespalib::Array<typename LoadedVector::Type> similarValues;
            auto value = loaded.read();
            LoadedValueType prev = value.getValue();
            for (size_t i(0), m(loaded.size()); i < m; i++, loaded.next()) {
                value = loaded.read();
                if (FoldedComparatorType::equal_helper(prev, value.getValue())) {
                    // for single value attributes loaded[numDocs] is used
                    // for default value but we don't want to add an
                    // invalid docId to the posting list.
                    if (value._docId < docIdLimit) {
                        postings.add(value._docId, value.getWeight());
                        similarValues.push_back(value);
                    }
                } else {
                    postings.removeDups();
                    newIndex = EntryRef();
                    _postingList.apply(newIndex,
                                       &postings._additions[0],
                                       &postings._additions[0] +
                                       postings._additions.size(),
                                       &postings._removals[0],
                                       &postings._removals[0] +
                                       postings._removals.size());
                    postings.clear();
                    if (value._docId < docIdLimit) {
                        postings.add(value._docId, value.getWeight());
                    }
                    similarValues[0]._pidx = newIndex;
                    for (size_t j(0), k(similarValues.size()); j < k; j++) {
                        loaded.write(similarValues[j]);
                    }
                    similarValues.clear();
                    similarValues.push_back(value);
                    prev = value.getValue();
                }
            }
            postings.removeDups();
            newIndex = EntryRef();
            _postingList.apply(newIndex,
                               &postings._additions[0],
                               &postings._additions[0] +
                               postings._additions.size(),
                               &postings._removals[0],
                               &postings._removals[0] + postings._removals.size());
            similarValues[0]._pidx = newIndex;
            for (size_t i(0), m(similarValues.size()); i < m; i++) {
                loaded.write(similarValues[i]);
            }
        }
    }
}


template <typename P, typename LoadedVector, typename LoadedValueType,
          typename EnumStoreType>
void
PostingListAttributeSubBase<P, LoadedVector, LoadedValueType, EnumStoreType>::
updatePostings(PostingMap &changePost)
{
    auto cmp = _es.make_folded_comparator();
    updatePostings(changePost, cmp);
}


template <typename P, typename LoadedVector, typename LoadedValueType,
          typename EnumStoreType>
void
PostingListAttributeSubBase<P, LoadedVector, LoadedValueType, EnumStoreType>::
clearPostings(attribute::IAttributeVector::EnumHandle eidx,
              uint32_t fromLid, uint32_t toLid)
{
    auto cmp = _es.make_folded_comparator();
    clearPostings(eidx, fromLid, toLid, cmp);
}


template class PostingListAttributeBase<AttributePosting>;
template class PostingListAttributeBase<AttributeWeightPosting>;

using LoadedInt8Vector = SequentialReadModifyWriteInterface<LoadedNumericValue<int8_t> >;

using LoadedInt16Vector = SequentialReadModifyWriteInterface<LoadedNumericValue<int16_t> >;

using LoadedInt32Vector = SequentialReadModifyWriteInterface<LoadedNumericValue<int32_t> >;

using LoadedInt64Vector = SequentialReadModifyWriteInterface<LoadedNumericValue<int64_t> >;

using LoadedFloatVector = SequentialReadModifyWriteInterface<LoadedNumericValue<float> >;

using LoadedDoubleVector = SequentialReadModifyWriteInterface<LoadedNumericValue<double> >;
                                                       

template class
PostingListAttributeSubBase<AttributePosting,
                            LoadedInt8Vector,
                            int8_t,
                            EnumStoreT<int8_t>>;

template class
PostingListAttributeSubBase<AttributePosting,
                            LoadedInt16Vector,
                            int16_t,
                            EnumStoreT<int16_t>>;

template class
PostingListAttributeSubBase<AttributePosting,
                            LoadedInt32Vector,
                            int32_t,
                            EnumStoreT<int32_t>>;

template class
PostingListAttributeSubBase<AttributePosting,
                            LoadedInt64Vector,
                            int64_t,
                            EnumStoreT<int64_t>>;

template class
PostingListAttributeSubBase<AttributePosting,
                            LoadedFloatVector,
                            float,
                            EnumStoreT<float>>;

template class
PostingListAttributeSubBase<AttributePosting,
                            LoadedDoubleVector,
                            double,
                            EnumStoreT<double>>;

template class
PostingListAttributeSubBase<AttributePosting,
                            NoLoadedVector,
                            const char*,
                            EnumStoreT<const char*>>;

template class
PostingListAttributeSubBase<AttributeWeightPosting,
                            LoadedInt8Vector,
                            int8_t,
                            EnumStoreT<int8_t>>;

template class
PostingListAttributeSubBase<AttributeWeightPosting,
                            LoadedInt16Vector,
                            int16_t,
                            EnumStoreT<int16_t>>;

template class
PostingListAttributeSubBase<AttributeWeightPosting,
                            LoadedInt32Vector,
                            int32_t,
                            EnumStoreT<int32_t>>;

template class
PostingListAttributeSubBase<AttributeWeightPosting,
                            LoadedInt64Vector,
                            int64_t,
                            EnumStoreT<int64_t>>;

template class
PostingListAttributeSubBase<AttributeWeightPosting,
                            LoadedFloatVector,
                            float,
                            EnumStoreT<float>>;

template class
PostingListAttributeSubBase<AttributeWeightPosting,
                            LoadedDoubleVector,
                            double,
                            EnumStoreT<double>>;

template class
PostingListAttributeSubBase<AttributeWeightPosting,
                            NoLoadedVector,
                            const char*,
                            EnumStoreT<const char*>>;


}
