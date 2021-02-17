// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <cstdint>
#include <climits>
#include <vector>
#include <vespa/vespalib/util/arrayref.h>

namespace vespalib::eval {

template<typename T = uint64_t>
inline bool access_bit_in_array(const T *data, size_t index) {
    static constexpr size_t bit_per_t = CHAR_BIT * sizeof(T);
    static constexpr size_t lower_bits = bit_per_t - 1;
    static_assert((bit_per_t & lower_bits) == 0);
    size_t elem = index / bit_per_t;
    size_t bit = index & lower_bits;
    T mask = (1u << bit);
    T masked = data[elem] & mask;
    return (masked != 0);
}

template <typename T> class CellsArrayRef;

template <typename T>
class ConstCellsArrayRef {
private:
    const T *_data;
    const size_t _size;
public:
    ConstCellsArrayRef() : _data(nullptr), _size(0) {}

    ConstCellsArrayRef(const T *data, size_t sz)
      : _data(data), _size(sz)
    {}

    ConstCellsArrayRef(const std::vector<T> &values)
      : _data(&values[0]), _size(values.size())
    {}

    ConstCellsArrayRef(const vespalib::ConstArrayRef<T> &values)
      : _data(values.cbegin()), _size(values.size())
    {}

    size_t size() const { return _size; }

    T at(size_t idx) const {
        return _data[idx];
    }

    T operator[] (size_t idx) const { return at(idx); }

    ConstCellsArrayRef<T> sub_array(size_t sz, size_t offset) const {
        return ConstCellsArrayRef<T>(_data + offset, sz);
    }

    // const-cast for array references; use with care
    CellsArrayRef<T> unconstify() const;

    /** backwards semi-compatibility */
    const T * dbegin() const { return _data; }
};

template <>
class ConstCellsArrayRef<bool> {
private:
    const uint64_t *_data;
    const uint32_t _size;
    const uint32_t _offset;
public:
    ConstCellsArrayRef() : _data(nullptr), _size(0), _offset(0) {}

    ConstCellsArrayRef(const uint64_t *data, size_t sz, size_t offset = 0)
      : _data(data), _size(sz), _offset(offset)
    {}

    size_t size() const { return _size; }

    bool at(size_t idx) const {
        return access_bit_in_array(_data, _offset + idx);
    }

    bool operator[] (size_t idx) const { return at(idx); }

    ConstCellsArrayRef<bool> sub_array(size_t sz, size_t offset) {
        return ConstCellsArrayRef<bool>(_data, sz, _offset + offset);
    }

    const uint64_t * bits_begin() const { return _data; }

    size_t bits_offset() const { return _offset; }

    // const-cast for array references; use with care
    CellsArrayRef<bool> unconstify() const;
};

}
