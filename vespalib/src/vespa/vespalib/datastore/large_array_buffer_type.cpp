// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "large_array_buffer_type.hpp"
#include "buffer_type.hpp"

namespace vespalib::datastore {

template class BufferType<Array<uint8_t>>;
template class BufferType<Array<uint32_t>>;
template class BufferType<Array<int32_t>>;
template class BufferType<Array<std::string>>;
template class BufferType<Array<AtomicEntryRef>>;

template class LargeArrayBufferType<uint8_t>;
template class LargeArrayBufferType<uint32_t>;
template class LargeArrayBufferType<int32_t>;
template class LargeArrayBufferType<std::string>;
template class LargeArrayBufferType<AtomicEntryRef>;

}
