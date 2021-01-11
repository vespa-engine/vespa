// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_store.hpp"
#include "buffer_type.hpp"

namespace vespalib::datastore {

template class BufferType<vespalib::Array<uint8_t>>;
template class BufferType<vespalib::Array<uint32_t>>;
template class BufferType<vespalib::Array<int32_t>>;
template class BufferType<vespalib::Array<std::string>>;
template class BufferType<vespalib::Array<AtomicEntryRef>>;

}
