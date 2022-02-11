// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "small_array_buffer_type.hpp"
#include "buffer_type.hpp"

namespace vespalib::datastore {

template class SmallArrayBufferType<uint8_t>;
template class SmallArrayBufferType<uint32_t>;
template class SmallArrayBufferType<int32_t>;
template class SmallArrayBufferType<std::string>;
template class SmallArrayBufferType<AtomicEntryRef>;

}
