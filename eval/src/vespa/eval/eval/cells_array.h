// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "const_cells_array_ref.h"
#include "cells_array_ref.h"
#include <cstdint>
#include <climits>
#include <cstring>
#include <vector>
#include <stdlib.h>
#include <vespa/vespalib/util/alloc.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <vespa/vespalib/util/traits.h>

namespace vespalib::eval {

template <typename T>
class CellsArray {
private:
    uint32_t _capacity;
    uint32_t _size;
    T *      _data;

    static constexpr size_t elem_size = sizeof(T);

    static T * alloc_elements(size_t num) {
        void *ptr = malloc(elem_size * num);
        return reinterpret_cast<T*>(ptr);
    }
public:
    CellsArray(size_t initial_capacity)
        : _capacity(roundUp2inN(initial_capacity)),
          _size(0),
          _data(alloc_elements(_capacity))
    {
        static_assert(std::is_trivially_copyable_v<T>);
        static_assert(can_skip_destruction<T>::value);
    }

    CellsArray(const CellsArray &other)
      : _capacity(other._capacity),
        _size(other._size),
        _data(alloc_elements(_capacity))
    {
        memcpy(_data, other._data, elem_size * _size);
    }

    CellsArray(CellsArray && other)
      : _capacity(other._capacity),
        _size(other._size),
        _data(other._data)
    {
        other._capacity = 0;
        other._size = 0;
        other._data = nullptr;
    }

    ~CellsArray() {
        free(_data);
    }

    void ensure_free(size_t need) {
        if (__builtin_expect((_size + need) > _capacity, false)) {
            _capacity = roundUp2inN(_size + need);
            T *new_data = alloc_elements(_capacity);
            memcpy(new_data, _data, elem_size * _size);
            free(_data);
            _data = new_data;
        }
    }
    
    CellsArrayRef<T> array_ref(size_t sz, size_t offset) {
        return CellsArrayRef(_data + offset, sz);
    }
    CellsArrayRef<T> array_ref() { return array_ref(_size, 0); }

    ConstCellsArrayRef<T> const_array_ref(size_t sz, size_t offset) const {
        return ConstCellsArrayRef(_data + offset, sz);
    }
    ConstCellsArrayRef<T> const_array_ref() const { return const_array_ref(_size, 0); }

    size_t size() const { return _size; }

    T at(size_t offset) {
        return _data[offset];
    }

    void assign(size_t offset, T value) {
        _data[offset] = value;
    }

    void push_back_fast(T value) {
        _data[_size++] = value;
    }

    CellsArrayRef<T> add_cells(size_t n) {
        size_t old_size = _size;
        ensure_free(n);
        _size += n;
        return array_ref(n, old_size);
    }

    MemoryUsage estimate_extra_memory_usage() const {
        MemoryUsage usage;
        usage.incAllocatedBytes(elem_size * _capacity);
        usage.incUsedBytes(elem_size * _size);
        return usage;
    }
};


template <>
class CellsArray<bool> {
private:
    uint32_t   _capacity;
    uint32_t   _size;
    uint64_t * _data;

    static constexpr size_t bit_per_t = CHAR_BIT * sizeof(uint64_t);
    static constexpr size_t lower_bits = (bit_per_t - 1);
    static_assert((bit_per_t & lower_bits) == 0);

public:
    static constexpr size_t num_words_for(size_t capacity) {
        return (capacity + lower_bits) / bit_per_t;        
    }

    static size_t need_bytes_for(size_t sz) {
        return num_words_for(sz) * sizeof(uint64_t);
    }

    static uint64_t * alloc_bytes_for(size_t sz) {
        void *ptr = malloc(need_bytes_for(sz));
        return reinterpret_cast<uint64_t *>(ptr);
    }

    CellsArray(size_t initial_capacity)
        : _capacity(roundUp2inN(initial_capacity)),
          _size(0),
          _data(alloc_bytes_for(_capacity))
    {
    }

    CellsArray(const CellsArray &other)
      : _capacity(other._capacity),
        _size(other._size),
        _data(alloc_bytes_for(_capacity))
    {
        memcpy(_data, other._data, need_bytes_for(_size));
    }

    CellsArray(CellsArray && other)
      : _capacity(other._capacity),
        _size(other._size),
        _data(other._data)
    {
        other._capacity = 0;
        other._size = 0;
        other._data = nullptr;
    }


    ~CellsArray() {
        free(_data);
    }

    size_t size() const { return _size; }

    void ensure_free(size_t need) {
        if (__builtin_expect((_size + need) > _capacity, false)) {
            _capacity = roundUp2inN(_size + need);
            uint64_t *new_data = alloc_bytes_for(_capacity);
            memcpy(new_data, _data, need_bytes_for(_size));
            free(_data);
            _data = new_data;
        }
    }
    
    CellsArrayRef<bool> array_ref(size_t sz, size_t offset) {
        return CellsArrayRef<bool>(_data, sz, offset);
    }
    CellsArrayRef<bool> array_ref() { return array_ref(_size, 0); }

    ConstCellsArrayRef<bool> const_array_ref(size_t sz, size_t offset) const {
        return ConstCellsArrayRef<bool>(_data, sz, offset);
    }
    ConstCellsArrayRef<bool> const_array_ref() const { return const_array_ref(_size, 0); }

    bool at(size_t offset) {
        return access_bit_in_array(_data, offset);
    }

    void assign(size_t offset, bool value) {
        assign_bit_in_array(_data, offset, value);
    }

    void push_back_fast(bool value) {
        assign_bit_in_array(_data, _size++, value);
    }

    CellsArrayRef<bool> add_cells(size_t n) {
        size_t old_size = _size;
        ensure_free(n);
        _size += n;
        return array_ref(n, old_size);
    }

    MemoryUsage estimate_extra_memory_usage() const {
        MemoryUsage usage;
        usage.incAllocatedBytes(_capacity / CHAR_BIT);
        usage.incUsedBytes((_size + CHAR_BIT - 1) / CHAR_BIT);
        return usage;
    }

};


}
