// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "unique_store.hpp"
#include "buffer_type.hpp"

namespace vespalib::datastore {

template class BufferType<UniqueStoreEntry<int8_t>>;
template class BufferType<UniqueStoreEntry<int16_t>>;
template class BufferType<UniqueStoreEntry<int32_t>>;
template class BufferType<UniqueStoreEntry<int64_t>>;
template class BufferType<UniqueStoreEntry<uint32_t>>;
template class BufferType<UniqueStoreEntry<float>>;
template class BufferType<UniqueStoreEntry<double>>;

using namespace btree;

VESPALIB_DATASTORE_INSTANTIATE_BUFFERTYPE_INTERNALNODE(EntryRef, NoAggregated, uniquestore::DefaultDictionaryTraits::INTERNAL_SLOTS);
VESPALIB_DATASTORE_INSTANTIATE_BUFFERTYPE_LEAFNODE(EntryRef, BTreeNoLeafData, NoAggregated, uniquestore::DefaultDictionaryTraits::LEAF_SLOTS);

}
