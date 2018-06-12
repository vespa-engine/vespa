// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enumstore.h"
#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.enum_store");
#include "enumstore.hpp"
#include <iomanip>

namespace search {

template <>
void
EnumStoreT<StringEntryType>::
insertEntryValue(char * dst, Type value)
{
    strcpy(dst, value);
}

template <>
void
EnumStoreT<StringEntryType>::
printEntry(vespalib::asciistream & os, const Entry & e) const
{
    os << "Entry: {";
    os << "enum: " << e.getEnum();
    os << ", refcount: " << e.getRefCount();
    os << ", value: " << vespalib::string(e.getValue());
    os << "}";
}


template <>
void
EnumStoreT<NumericEntryType<float> >::
printEntry(vespalib::asciistream & os, const Entry & e) const
{
    os << "Entry: {";
    os << "enum: " << e.getEnum();
    os << ", refcount: " << e.getRefCount();
    os << ", value: " << e.getValue();
    union
    {
        unsigned int _asInt;
        float _asFloat;
    } u;
    u._asFloat = e.getValue();
    os << ", bvalue: 0x" << std::hex << u._asInt;
    os << "}";
}


template <>
void
EnumStoreT<NumericEntryType<double> >::
printEntry(vespalib::asciistream & os, const Entry & e) const
{
    os << "Entry: {";
    os << "enum: " << e.getEnum();
    os << ", refcount: " << e.getRefCount();
    os << ", value: " << e.getValue();
    union
    {
        unsigned long _asLong;
        double _asDouble;
    } u;
    u._asDouble = e.getValue();
    os << ", bvalue: 0x" << std::hex << u._asLong;
    os << "}";
}


template <>
void
EnumStoreT<StringEntryType>::printValue(vespalib::asciistream & os, Index idx) const
{
    os << vespalib::string(getValue(idx));
}

template <>
void
EnumStoreT<StringEntryType>::printValue(vespalib::asciistream & os, Type value) const
{
    os << vespalib::string(value);
}

 
template <>
void
EnumStoreT<StringEntryType>::writeValues(BufferWriter &writer,
                                         const Index *idxs,
                                         size_t count) const
{
    for (uint32_t i = 0; i < count; ++i) {
        Index idx = idxs[i];
        const char *src(_store.getBufferEntry<char>(idx.bufferId(),
                                                    idx.offset()) +
                        EntryBase::size());
        size_t sz = strlen(src) + 1;
        writer.write(src, sz);
    }
}


template <>
ssize_t
EnumStoreT<StringEntryType>::deserialize(const void *src,
                                            size_t available,
                                            size_t &initSpace)
{
    size_t slen = strlen(static_cast<const char *>(src));
    size_t sz(StringEntryType::fixedSize() + slen);
    if (available < sz)
        return -1;
    uint32_t entrySize(alignEntrySize(EntryBase::size() + sz));
    initSpace += entrySize;
    return sz;
}


template <>
ssize_t
EnumStoreT<StringEntryType>::deserialize(const void *src,
                                            size_t available,
                                            Index &idx)
{
    size_t slen = strlen(static_cast<const char *>(src));
    size_t sz(StringEntryType::fixedSize() + slen);
    if (available < sz)
        return -1;
    uint32_t activeBufferId = _store.getActiveBufferId(TYPE_ID);
    datastore::BufferState & buffer = _store.getBufferState(activeBufferId);
    uint32_t entrySize(alignEntrySize(EntryBase::size() + sz));
    if (buffer.remaining() < entrySize) {
        fprintf(stderr, "Out of enumstore bufferspace\n");
        LOG_ABORT("should not be reached"); // not enough space
    }
    uint64_t offset = buffer.size();
    char *dst(_store.getBufferEntry<char>(activeBufferId, offset));
    memcpy(dst, &_nextEnum, sizeof(uint32_t));
    uint32_t pos = sizeof(uint32_t);
    uint32_t refCount(0);
    memcpy(dst + pos, &refCount, sizeof(uint32_t));
    pos += sizeof(uint32_t);
    memcpy(dst + pos, src, sz);
    buffer.pushed_back(entrySize);
    ++_nextEnum;

    if (idx.valid()) {
        assert(ComparatorType::compare(getValue(idx),
                                       Entry(dst).getValue()) < 0);
    }
    idx = Index(offset, activeBufferId);
    return sz;
}


template
class btree::BTreeNodeDataWrap<btree::BTreeNoLeafData,
                               EnumTreeTraits::LEAF_SLOTS>;

template
class btree::BTreeNodeDataWrap<datastore::EntryRef,
                               EnumTreeTraits::LEAF_SLOTS>;

#if 0
template
class btree::BTreeKeyData<EnumStoreBase::Index,
                          btree::BTreeNoLeafData>;

template
class btree::BTreeKeyData<EnumStoreBase::Index,
                          datastore::EntryRef>;
#endif

template
class btree::BTreeNodeT<EnumStoreBase::Index,
                        EnumTreeTraits::INTERNAL_SLOTS>;

#if 0
template
class btree::BTreeNodeT<EnumStoreBase::Index,
                        EnumTreeTraits::LEAF_SLOTS>;
#endif

template
class btree::BTreeNodeTT<EnumStoreBase::Index,
                         datastore::EntryRef,
                         btree::NoAggregated,
                         EnumTreeTraits::INTERNAL_SLOTS>;

template
class btree::BTreeNodeTT<EnumStoreBase::Index,
                         btree::BTreeNoLeafData,
                         btree::NoAggregated,
                         EnumTreeTraits::LEAF_SLOTS>;

#if 0
template
class btree::BTreeNodeTT<EnumStoreBase::Index,
                         datastore::EntryRef,
                         btree::NoAggregated,
                         EnumTreeTraits::LEAF_SLOTS>;
#endif

template
class btree::BTreeInternalNode<EnumStoreBase::Index,
                               btree::NoAggregated,
                               EnumTreeTraits::INTERNAL_SLOTS>;

template
class btree::BTreeLeafNode<EnumStoreBase::Index,
                           btree::BTreeNoLeafData,
                           btree::NoAggregated,
                           EnumTreeTraits::LEAF_SLOTS>;

template
class btree::BTreeLeafNode<EnumStoreBase::Index,
                           datastore::EntryRef,
                           btree::NoAggregated,
                           EnumTreeTraits::LEAF_SLOTS>;

template
class btree::BTreeLeafNodeTemp<EnumStoreBase::Index,
                               btree::BTreeNoLeafData,
                               btree::NoAggregated,
                               EnumTreeTraits::LEAF_SLOTS>;

template
class btree::BTreeLeafNodeTemp<EnumStoreBase::Index,
                               datastore::EntryRef,
                               btree::NoAggregated,
                               EnumTreeTraits::LEAF_SLOTS>;

template
class btree::BTreeNodeStore<EnumStoreBase::Index,
                            btree::BTreeNoLeafData,
                            btree::NoAggregated,
                            EnumTreeTraits::INTERNAL_SLOTS,
                            EnumTreeTraits::LEAF_SLOTS>;

template
class btree::BTreeNodeStore<EnumStoreBase::Index,
                            datastore::EntryRef,
                            btree::NoAggregated,
                            EnumTreeTraits::INTERNAL_SLOTS,
                            EnumTreeTraits::LEAF_SLOTS>;

template
class btree::BTreeIteratorBase<EnumStoreBase::Index,
                               btree::BTreeNoLeafData,
                               btree::NoAggregated,
                               EnumTreeTraits::INTERNAL_SLOTS,
                               EnumTreeTraits::LEAF_SLOTS,
                               EnumTreeTraits::PATH_SIZE>;
template
class btree::BTreeIteratorBase<EnumStoreBase::Index,
                               datastore::EntryRef,
                               btree::NoAggregated,
                               EnumTreeTraits::INTERNAL_SLOTS,
                               EnumTreeTraits::LEAF_SLOTS,
                               EnumTreeTraits::PATH_SIZE>;
template
class btree::BTreeIterator<EnumStoreBase::Index,
                           btree::BTreeNoLeafData,
                           btree::NoAggregated,
                           const EnumStoreComparatorWrapper,
                           EnumTreeTraits>;
template
class btree::BTreeIterator<EnumStoreBase::Index,
                           datastore::EntryRef,
                           btree::NoAggregated,
                           const EnumStoreComparatorWrapper,
                           EnumTreeTraits>;
template
class btree::BTree<EnumStoreBase::Index,
                   btree::BTreeNoLeafData,
                   btree::NoAggregated,
                   const EnumStoreComparatorWrapper,
                   EnumTreeTraits>;
template
class btree::BTree<EnumStoreBase::Index,
                   datastore::EntryRef,
                   btree::NoAggregated,
                   const EnumStoreComparatorWrapper,
                   EnumTreeTraits>;
template
class btree::BTreeRoot<EnumStoreBase::Index,
                       btree::BTreeNoLeafData,
                       btree::NoAggregated,
                       const EnumStoreComparatorWrapper,
                       EnumTreeTraits>;

template
class btree::BTreeRoot<EnumStoreBase::Index,
                       datastore::EntryRef,
                       btree::NoAggregated,
                       const EnumStoreComparatorWrapper,
                       EnumTreeTraits>;
template
class btree::BTreeRootT<EnumStoreBase::Index,
                        btree::BTreeNoLeafData,
                        btree::NoAggregated,
                        const EnumStoreComparatorWrapper,
                        EnumTreeTraits>;

template
class btree::BTreeRootT<EnumStoreBase::Index,
                        datastore::EntryRef,
                        btree::NoAggregated,
                        const EnumStoreComparatorWrapper,
                        EnumTreeTraits>;
template
class btree::BTreeRootBase<EnumStoreBase::Index,
                           btree::BTreeNoLeafData,
                           btree::NoAggregated,
                           EnumTreeTraits::INTERNAL_SLOTS,
                           EnumTreeTraits::LEAF_SLOTS>;

template
class btree::BTreeRootBase<EnumStoreBase::Index,
                           datastore::EntryRef,
                           btree::NoAggregated,
                           EnumTreeTraits::INTERNAL_SLOTS,
                           EnumTreeTraits::LEAF_SLOTS>;

template
class btree::BTreeNodeAllocator<EnumStoreBase::Index,
                                btree::BTreeNoLeafData,
                                btree::NoAggregated,
                                EnumTreeTraits::INTERNAL_SLOTS,
                                EnumTreeTraits::LEAF_SLOTS>;

template
class btree::BTreeNodeAllocator<EnumStoreBase::Index,
                                datastore::EntryRef,
                                btree::NoAggregated,
                                EnumTreeTraits::INTERNAL_SLOTS,
                                EnumTreeTraits::LEAF_SLOTS>;

template
class btree::BTreeBuilder<EnumStoreBase::Index,
                          btree::BTreeNoLeafData,
                          btree::NoAggregated,
                          EnumTreeTraits::INTERNAL_SLOTS,
                          EnumTreeTraits::LEAF_SLOTS>;

template
class btree::BTreeBuilder<EnumStoreBase::Index,
                          datastore::EntryRef,
                          btree::NoAggregated,
                          EnumTreeTraits::INTERNAL_SLOTS,
                          EnumTreeTraits::LEAF_SLOTS>;

template class EnumStoreT< StringEntryType >;
template class EnumStoreT<NumericEntryType<int8_t> >;
template class EnumStoreT<NumericEntryType<int16_t> >;
template class EnumStoreT<NumericEntryType<int32_t> >;
template class EnumStoreT<NumericEntryType<int64_t> >;
template class EnumStoreT<NumericEntryType<float> >;
template class EnumStoreT<NumericEntryType<double> >;

} // namespace search
