// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rcuvector.hpp"

namespace vespalib {

template class RcuVectorBase<uint8_t>;
template class RcuVectorBase<uint16_t>;
template class RcuVectorBase<uint32_t>;
template class RcuVectorBase<uint64_t>;
template class RcuVectorBase<int8_t>;
template class RcuVectorBase<int16_t>;
template class RcuVectorBase<int32_t>;
template class RcuVectorBase<int64_t>;
template class RcuVectorBase<float>;
template class RcuVectorBase<double>;

template class RcuVector<uint8_t>;
template class RcuVector<uint16_t>;
template class RcuVector<uint32_t>;
template class RcuVector<uint64_t>;
template class RcuVector<int8_t>;
template class RcuVector<int16_t>;
template class RcuVector<int32_t>;
template class RcuVector<int64_t>;
template class RcuVector<float>;
template class RcuVector<double>;

template class RcuVectorHeld<uint8_t>;
template class RcuVectorHeld<uint16_t>;
template class RcuVectorHeld<uint32_t>;
template class RcuVectorHeld<uint64_t>;
template class RcuVectorHeld<int8_t>;
template class RcuVectorHeld<int16_t>;
template class RcuVectorHeld<int32_t>;
template class RcuVectorHeld<int64_t>;
template class RcuVectorHeld<float>;
template class RcuVectorHeld<double>;

}
