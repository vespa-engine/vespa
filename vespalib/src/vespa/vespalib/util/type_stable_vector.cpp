// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "type_stable_vector.hpp"

namespace vespalib {

template class TypeStableVectorBase<uint8_t>;
template class TypeStableVectorBase<uint16_t>;
template class TypeStableVectorBase<uint32_t>;
template class TypeStableVectorBase<uint64_t>;
template class TypeStableVectorBase<int8_t>;
template class TypeStableVectorBase<int16_t>;
template class TypeStableVectorBase<int32_t>;
template class TypeStableVectorBase<int64_t>;
template class TypeStableVectorBase<float>;
template class TypeStableVectorBase<double>;

template class TypeStableVector<uint8_t>;
template class TypeStableVector<uint16_t>;
template class TypeStableVector<uint32_t>;
template class TypeStableVector<uint64_t>;
template class TypeStableVector<int8_t>;
template class TypeStableVector<int16_t>;
template class TypeStableVector<int32_t>;
template class TypeStableVector<int64_t>;
template class TypeStableVector<float>;
template class TypeStableVector<double>;

template class TypeStableVectorHeld<uint8_t>;
template class TypeStableVectorHeld<uint16_t>;
template class TypeStableVectorHeld<uint32_t>;
template class TypeStableVectorHeld<uint64_t>;
template class TypeStableVectorHeld<int8_t>;
template class TypeStableVectorHeld<int16_t>;
template class TypeStableVectorHeld<int32_t>;
template class TypeStableVectorHeld<int64_t>;
template class TypeStableVectorHeld<float>;
template class TypeStableVectorHeld<double>;

} // namespace vespalib
