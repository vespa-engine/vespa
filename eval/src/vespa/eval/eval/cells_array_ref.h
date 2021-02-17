// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "const_cells_array_ref.h"
#include <climits>
#include <vector>
#include <stdlib.h>
#include <vespa/vespalib/util/stash.h>

namespace vespalib::eval {

template<typename T = uint64_t>
inline void assign_bit_in_array(T *data, size_t index, bool value) {
    static constexpr size_t bit_per_t = CHAR_BIT * sizeof(T);
    static constexpr size_t lower_bits = bit_per_t - 1;
    static_assert((bit_per_t & lower_bits) == 0);
    size_t elem = index / bit_per_t;
    size_t bit = index & lower_bits;
    T mask = (1u << bit);
    if (value) {
        data[elem] |= mask;
    } else {
        data[elem] &= ~mask;
    }
}

template <typename T>
class CellsArrayRef {
    T *_data;
    size_t _size;
public:
    CellsArrayRef() : _data(nullptr), _size(0) {}

    CellsArrayRef(T *data, size_t sz)
      : _data(data), _size(sz)
    {}

    CellsArrayRef(std::vector<T> &values)
      : _data(&values[0]), _size(values.size())
    {}

    CellsArrayRef(vespalib::ArrayRef<T> &values)
      : _data(values.begin()), _size(values.size())
    {}

    size_t size() const { return _size; }

    T at(size_t idx) const {
        return _data[idx];
    }

    void assign(size_t idx, T value) {
        _data[idx] = value;
    }

    operator ConstCellsArrayRef<T> () const {
        return ConstCellsArrayRef(_data, _size);
    }

    static CellsArrayRef<T> create_uninitialized(vespalib::Stash &stash, size_t sz) {
        ArrayRef<T> array = stash.create_uninitialized_array<T>(sz);
        return array;
    }

    /** backwards semi-compatibility */
    T * wbegin() {
        return _data;
    }
};

template <>
class CellsArrayRef<bool> {
private:
    using WordType = uint64_t;
    WordType *_data;
    const uint32_t _size;
    const uint32_t _offset;

    static constexpr size_t bit_per_t = CHAR_BIT * sizeof(WordType);
    static constexpr size_t lower_bits = (bit_per_t - 1);
    static_assert((bit_per_t & lower_bits) == 0);

    static constexpr size_t num_words_for(size_t capacity) {
        return (capacity + lower_bits) / bit_per_t;        
    }

public:
    CellsArrayRef() : _data(nullptr), _size(0), _offset(0) {}

    CellsArrayRef(WordType *data, size_t bits_sz, size_t bits_off)
      : _data(data), _size(bits_sz), _offset(bits_off)
    {}

    size_t size() const { return _size; }

    bool at(size_t idx) const {
        return access_bit_in_array(_data, _offset + idx);
    }

    void assign(size_t idx, bool value) {
        assign_bit_in_array(_data, _offset + idx, value);
    }

    operator ConstCellsArrayRef<bool>() const {
        return ConstCellsArrayRef<bool>(_data, _size, _offset);
    }

    static CellsArrayRef<bool> create_uninitialized(vespalib::Stash &stash, size_t sz) {
        // probably not valgrind friendly
        auto array = stash.create_uninitialized_array<uint64_t>(num_words_for(sz));
        return CellsArrayRef<bool>(array.begin(), sz, 0);
    }

    WordType * bits_begin() const { return _data; }
    size_t bits_offset() const { return _offset; }
};


}
