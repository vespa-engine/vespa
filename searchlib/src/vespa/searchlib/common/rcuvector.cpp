// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rcuvector.hpp"

namespace search {
namespace attribute {

template class RcuVectorBase<uint8_t>;
template class RcuVectorBase<uint16_t>;
template class RcuVectorBase<uint32_t>;
template class RcuVectorBase<int8_t>;
template class RcuVectorBase<int16_t>;
template class RcuVectorBase<int32_t>;
template class RcuVectorBase<int64_t>;
template class RcuVectorBase<float>;
template class RcuVectorBase<double>;


}
}
