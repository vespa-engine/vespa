// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "postinglistattribute.h"
#include "loadednumericvalue.h"
#include "enumcomparator.h"
#include "enum_store_loaders.h"
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
        loader.build_empty_dictionary();
        return;
    }
    uint32_t preve = 0;
    uint32_t refCount = 0;

    vespalib::ConstArrayRef<EnumIndex> enum_indexes(loader.get_enum_indexes());
    assert(!enum_indexes.empty());
    auto posting_indexes = loader.initialize_empty_posting_indexes();
    uint32_t posting_enum = preve;
    for (const auto& elem : loaded_enums) {
        if (preve != elem.getEnum()) {
            assert(preve < elem.getEnum());
            assert(elem.getEnum() < enum_indexes.size());
            loader.set_ref_count(enum_indexes[preve], refCount);
            refCount = 0;
            preve = elem.getEnum();
            if (loader.is_folded_change(enum_indexes[posting_enum], enum_indexes[preve])) {
                postings.removeDups();
                newIndex = EntryRef();
                _postingList.apply(newIndex,
                                   postings._additions.data(),
                                   postings._additions.data() +
                                   postings._additions.size(),
                                   postings._removals.data(),
                                   postings._removals.data() +
                                   postings._removals.size());
                posting_indexes[posting_enum] = newIndex;
                postings.clear();
                posting_enum = elem.getEnum();
            }
        }
        assert(refCount < std::numeric_limits<uint32_t>::max());
        ++refCount;
        assert(elem.getDocId() < docIdLimit);
        (void) docIdLimit;
        postings.add(elem.getDocId(), elem.getWeight());
    }
    assert(refCount != 0);
    loader.set_ref_count(enum_indexes[preve], refCount);
    postings.removeDups();
    newIndex = EntryRef();
    _postingList.apply(newIndex,
                       postings._additions.data(),
                       postings._additions.data() + postings._additions.size(),
                       postings._removals.data(),
                       postings._removals.data() + postings._removals.size());
    posting_indexes[posting_enum] = newIndex;
    loader.build_dictionary();
    loader.free_unused_values();
}

template <typename P>
void
PostingListAttributeBase<P>::updatePostings(PostingMap &changePost,
                                            const vespalib::datastore::EntryComparator &cmp)
{
    for (auto& elem : changePost) {
        EnumIndex idx = elem.first.getEnumIdx();
        auto& change = elem.second;
        change.removeDups();
        auto updater= [this, &change](EntryRef posting_idx) -> EntryRef
                      {
                          _postingList.apply(posting_idx,
                                             change._additions.data(),
                                             change._additions.data() + change._additions.size(),
                                             change._removals.data(),
                                             change._removals.data() + change._removals.size());
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
              const vespalib::datastore::EntryComparator &cmp)
{
    PostingChange<P> postings;

    for (uint32_t lid = fromLid; lid < toLid; ++lid) {
        postings.remove(lid);
    }

    EntryRef er(eidx);
    auto updater = [this, &postings](EntryRef posting_idx) -> EntryRef
                   {
                       _postingList.apply(posting_idx,
                                          postings._additions.data(),
                                          postings._additions.data() + postings._additions.size(),
                                          postings._removals.data(),
                                          postings._removals.data() + postings._removals.size());
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

template <typename P>
bool
PostingListAttributeBase<P>::consider_compact_worst_btree_nodes(const CompactionStrategy& compaction_strategy)
{
    return _postingList.consider_compact_worst_btree_nodes(compaction_strategy);
}

template <typename P>
bool
PostingListAttributeBase<P>::consider_compact_worst_buffers(const CompactionStrategy& compaction_strategy)
{
    return _postingList.consider_compact_worst_buffers(compaction_strategy);
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
                if (ComparatorType::equal_helper(prev, value.getValue())) {
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
                                       postings._additions.data(),
                                       postings._additions.data() +
                                       postings._additions.size(),
                                       postings._removals.data(),
                                       postings._removals.data() +
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
                               postings._additions.data(),
                               postings._additions.data() +
                               postings._additions.size(),
                               postings._removals.data(),
                               postings._removals.data() + postings._removals.size());
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
    updatePostings(changePost, _es.get_folded_comparator());
}


template <typename P, typename LoadedVector, typename LoadedValueType,
          typename EnumStoreType>
void
PostingListAttributeSubBase<P, LoadedVector, LoadedValueType, EnumStoreType>::
clearPostings(attribute::IAttributeVector::EnumHandle eidx, uint32_t fromLid, uint32_t toLid)
{
    clearPostings(eidx, fromLid, toLid, _es.get_folded_comparator());
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
