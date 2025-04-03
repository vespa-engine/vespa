// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "numeric_sort_blob_writer.h"
#include <vespa/searchcommon/common/undefinedvalues.h>
#include <vespa/vespalib/util/sort.h>
#include <cassert>
#include <cmath>
#include <type_traits>

using search::common::sortspec::MissingPolicy;

namespace search::attribute {

template <typename T, bool asc>
NumericSortBlobWriter<T, asc>::NumericSortBlobWriter(MissingPolicy policy, T missing_value, bool multi_value) noexcept
    : _best(),
      _missing_blob(),
      _value_prefix()
{
    switch (policy) {
        case MissingPolicy::DEFAULT:
            if (multi_value) {
                _missing_blob.emplace_back(1);
                _value_prefix.emplace(0);
            } else {
                set_missing_blob(getUndefined<T>()); // Serialize missing value as undefined value
            }
        break;
        case MissingPolicy::FIRST:
            _missing_blob.emplace_back(0);
        _value_prefix.emplace(1);
        break;
        case MissingPolicy::LAST:
            _missing_blob.emplace_back(1);
        _value_prefix.emplace(0);
        break;
        case MissingPolicy::AS:
            set_missing_blob(missing_value);
        break;
        default:
            break;
    }

}

template <typename T, bool asc>
NumericSortBlobWriter<T, asc>::~NumericSortBlobWriter() noexcept = default;

template <typename T, bool asc>
void
NumericSortBlobWriter<T, asc>::set_missing_blob(T value)
{
    _missing_blob.clear();
    _missing_blob.resize(sizeof(T));
    auto ret = vespalib::serializeForSort<vespalib::convertForSort<T, asc> >(value, _missing_blob.data(), _missing_blob.size());
    assert(ret == sizeof(T));
}

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
void
NumericSortBlobWriter<T, asc>::reset()
{
    _best.reset();
}

template <typename T, bool asc>
long
NumericSortBlobWriter<T, asc>::write(void *serTo, size_t available)
{
    auto dst = static_cast<unsigned char*>(serTo);
    if (_best.has_value()) {
        auto vp_len = value_prefix_len();
        if (available < vp_len + sizeof(T)) {
            return -1;
        }
        if (_value_prefix.has_value()) {
            dst[0] = _value_prefix.value();
        }
        auto ret = vespalib::serializeForSort<vespalib::convertForSort<T, asc> >(_best.value(), dst + vp_len,
            available - vp_len);
        return (ret >= 0) ? (ret + vp_len) : -1;
    } else {
        if (available < _missing_blob.size()) {
            return -1;
        }
        memcpy(dst, _missing_blob.data(), _missing_blob.size());
        return _missing_blob.size();
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
