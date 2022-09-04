// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <limits>
#include <cstdint>

namespace search {

template <typename T>
class Range {
public:
    Range() :
        _lower(std::numeric_limits<T>::max()),
        _upper(std::numeric_limits<T>::min()) { }
    Range(T v) : _lower(v), _upper(v) { }
    Range(T low, T high) : _lower(low), _upper(high) { }
    T lower() const { return _lower; }
    T upper() const { return _upper; }
    bool valid() const { return _lower <= _upper; }
    bool isPoint() const { return _lower == _upper; }
private:
    T _lower;
    T _upper;
};

using Int64Range = Range<int64_t>;
using DoubleRange = Range<double>;

} // namespace search
