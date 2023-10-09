// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array.hpp"
#include "array_equal.hpp"

namespace vespalib {

template class Array<signed char>;
template class Array<char>;
template class Array<int16_t>;
template class Array<int32_t>;
template class Array<int64_t>;
template class Array<unsigned char>;
template class Array<uint32_t>;
template class Array<uint64_t>;
template class Array<float>;
template class Array<double>;

}
