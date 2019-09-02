// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enumstore.hpp"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/rcuvector.hpp>
#include <iomanip>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.enum_store");

namespace search {


template <>
void
EnumStoreT<StringEntryType>::writeValues(BufferWriter& writer,
                                         const Index* idxs,
                                         size_t count) const
{
    for (size_t i = 0; i < count; ++i) {
        Index idx = idxs[i];
        const char* src = _store.get(idx);
        size_t sz = strlen(src) + 1;
        writer.write(src, sz);
    }
}

template <>
ssize_t
EnumStoreT<StringEntryType>::deserialize(const void* src,
                                         size_t available,
                                         Index& idx)
{
    const char* value = static_cast<const char*>(src);
    size_t slen = strlen(value);
    size_t sz = slen + 1;
    if (available < sz) {
        return -1;
    }
    Index prev_idx = idx;
    idx = _store.get_allocator().allocate(value);

    if (prev_idx.valid()) {
        assert(ComparatorType::compare(getValue(prev_idx), value) < 0);
    }
    return sz;
}

std::unique_ptr<datastore::IUniqueStoreDictionary>
make_enum_store_dictionary(IEnumStore &store, bool has_postings)
{
    if (has_postings) {
        return std::make_unique<EnumStoreDictionary<EnumPostingTree>>(store);
    } else {
        return std::make_unique<EnumStoreDictionary<EnumTree>>(store);
    }
}

vespalib::asciistream & operator << (vespalib::asciistream & os, const IEnumStore::Index & idx) {
    return os << "offset(" << idx.offset() << "), bufferId(" << idx.bufferId() << "), idx(" << idx.ref() << ")";
}

template class datastore::DataStoreT<IEnumStore::Index>;

template
class btree::BTreeBuilder<IEnumStore::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                          EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

template
class btree::BTreeBuilder<IEnumStore::Index, datastore::EntryRef, btree::NoAggregated,
                          EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

template class EnumStoreT< StringEntryType >;
template class EnumStoreT<NumericEntryType<int8_t> >;
template class EnumStoreT<NumericEntryType<int16_t> >;
template class EnumStoreT<NumericEntryType<int32_t> >;
template class EnumStoreT<NumericEntryType<int64_t> >;
template class EnumStoreT<NumericEntryType<float> >;
template class EnumStoreT<NumericEntryType<double> >;

} // namespace search

namespace vespalib {
    template class RcuVectorBase<search::IEnumStore::Index>;
}

VESPALIB_HASH_MAP_INSTANTIATE_H_E_M(search::IEnumStore::Index, search::IEnumStore::Index,
        vespalib::hash<search::IEnumStore::Index>, std::equal_to<search::IEnumStore::Index>,
        vespalib::hashtable_base::and_modulator);
