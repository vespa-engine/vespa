// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "numeric_sort_blob_writer.h"
#include <vespa/vespalib/util/sort.h>
#include <cmath>
#include <type_traits>

namespace search::attribute {

template <typename T, bool asc>
NumericSortBlobWriter<T, asc>::NumericSortBlobWriter() noexcept
: _best()
{
}

template <typename T, bool asc>
NumericSortBlobWriter<T, asc>::~NumericSortBlobWriter() noexcept = default;

template <typename T, bool asc>
void
NumericSortBlobWriter<T, asc>::candidate(T val)
{
    if constexpr (std::disjunction_v<std::is_same<T,float>,std::is_same<T, double>>) {
        if (std::isnan(val)) {
            return;
        }
    }
    if (_best.has_value()) {
        if constexpr (asc) {
            if (_best.value() <= val) {
                return;
            }
        } else {
            if (_best.value() >= val) {
                return;
            }
        }
    }
    _best = val;
}

template <typename T, bool asc>
long
NumericSortBlobWriter<T, asc>::write(void *serTo, size_t available)
{
    auto dst = static_cast<unsigned char*>(serTo);
    if (_best.has_value()) {
        if (available < 1 + sizeof(T)) {
            return -1;
        }
        *dst = has_value;
        auto ret = vespalib::serializeForSort<vespalib::convertForSort<T, asc>>(_best.value(), dst + 1, available - 1);
        return (ret >= 0) ? (ret + 1) : -1;
    } else {
        if (available < 1) {
            return -1;
        }
        *dst = missing_value;
        return 1;
    }
}

template class NumericSortBlobWriter<int8_t, true>;
template class NumericSortBlobWriter<int16_t, true>;
template class NumericSortBlobWriter<int32_t, true>;
template class NumericSortBlobWriter<int64_t, true>;
template class NumericSortBlobWriter<float, true>;
template class NumericSortBlobWriter<double, true>;

template class NumericSortBlobWriter<int8_t, false>;
template class NumericSortBlobWriter<int16_t, false>;
template class NumericSortBlobWriter<int32_t, false>;
template class NumericSortBlobWriter<int64_t, false>;
template class NumericSortBlobWriter<float, false>;
template class NumericSortBlobWriter<double, false>;

}
