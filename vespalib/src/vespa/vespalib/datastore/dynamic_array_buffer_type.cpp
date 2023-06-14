// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dynamic_array_buffer_type.hpp"

namespace vespalib::datastore {

template class DynamicArrayBufferType<char>;
template class DynamicArrayBufferType<int8_t>;
template class DynamicArrayBufferType<int16_t>;
template class DynamicArrayBufferType<int32_t>;
template class DynamicArrayBufferType<int64_t>;
template class DynamicArrayBufferType<float>;
template class DynamicArrayBufferType<double>;
template class DynamicArrayBufferType<AtomicEntryRef>;

}

