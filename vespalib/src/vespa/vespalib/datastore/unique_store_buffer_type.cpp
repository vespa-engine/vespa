// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "unique_store_buffer_type.hpp"
#include "unique_store_entry.h"

namespace vespalib::datastore {

template class BufferType<UniqueStoreEntry<int8_t>>;
template class BufferType<UniqueStoreEntry<int16_t>>;
template class BufferType<UniqueStoreEntry<int32_t>>;
template class BufferType<UniqueStoreEntry<int64_t>>;
template class BufferType<UniqueStoreEntry<uint32_t>>;
template class BufferType<UniqueStoreEntry<float>>;
template class BufferType<UniqueStoreEntry<double>>;

template class UniqueStoreBufferType<UniqueStoreEntry<int8_t>>;
template class UniqueStoreBufferType<UniqueStoreEntry<int16_t>>;
template class UniqueStoreBufferType<UniqueStoreEntry<int32_t>>;
template class UniqueStoreBufferType<UniqueStoreEntry<int64_t>>;
template class UniqueStoreBufferType<UniqueStoreEntry<uint32_t>>;
template class UniqueStoreBufferType<UniqueStoreEntry<float>>;
template class UniqueStoreBufferType<UniqueStoreEntry<double>>;

};

