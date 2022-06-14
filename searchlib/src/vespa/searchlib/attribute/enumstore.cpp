// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enumstore.hpp"
#include <vespa/vespalib/datastore/sharded_hash_map.h>
#include <iomanip>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.enum_store");

namespace search {

template <>
void
EnumStoreT<const char*>::write_value(BufferWriter& writer, Index idx) const
{
    const char* src = _store.get(idx);
    size_t sz = strlen(src) + 1;
    writer.write(src, sz);
}

template <>
ssize_t
EnumStoreT<const char*>::load_unique_value(const void* src, size_t available, Index& idx)
{
    const char* value = static_cast<const char*>(src);
    size_t slen = strlen(value);
    size_t sz = slen + 1;
    if (available < sz) {
        return -1;
    }
    idx = _store.get_allocator().allocate(value);
    return sz;
}

std::unique_ptr<vespalib::datastore::IUniqueStoreDictionary>
make_enum_store_dictionary(IEnumStore &store, bool has_postings, const DictionaryConfig & dict_cfg,
                           std::unique_ptr<EntryComparator> compare,
                           std::unique_ptr<EntryComparator> folded_compare)
{
    using NoBTreeDictionary = vespalib::datastore::NoBTreeDictionary;
    using ShardedHashMap = vespalib::datastore::ShardedHashMap;
    if (has_postings) {
        if (folded_compare) {
            return std::make_unique<EnumStoreFoldedDictionary>(store, std::move(compare), std::move(folded_compare));
        } else {
            switch (dict_cfg.getType()) {
            case DictionaryConfig::Type::HASH:
                return std::make_unique<EnumStoreDictionary<NoBTreeDictionary, ShardedHashMap>>(store, std::move(compare));
            case DictionaryConfig::Type::BTREE_AND_HASH:
                return std::make_unique<EnumStoreDictionary<EnumPostingTree, ShardedHashMap>>(store, std::move(compare));
            default:
                return std::make_unique<EnumStoreDictionary<EnumPostingTree>>(store, std::move(compare));
            }
        }
    } else {
        return std::make_unique<EnumStoreDictionary<EnumTree>>(store, std::move(compare));
    }
}

}

namespace vespalib::datastore {

template class DataStoreT<search::IEnumStore::InternalIndex>;

}

namespace vespalib::btree {

template
class BTreeBuilder<search::IEnumStore::Index, BTreeNoLeafData, NoAggregated,
                   search::EnumTreeTraits::INTERNAL_SLOTS, search::EnumTreeTraits::LEAF_SLOTS>;

template
class BTreeBuilder<search::IEnumStore::Index, vespalib::datastore::EntryRef, NoAggregated,
                   search::EnumTreeTraits::INTERNAL_SLOTS, search::EnumTreeTraits::LEAF_SLOTS>;

}

namespace search {

template class EnumStoreT<const char*>;
template class EnumStoreT<int8_t>;
template class EnumStoreT<int16_t>;
template class EnumStoreT<int32_t>;
template class EnumStoreT<int64_t>;
template class EnumStoreT<float>;
template class EnumStoreT<double>;

} // namespace search
