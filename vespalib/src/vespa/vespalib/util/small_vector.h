// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "alloc.h"
#include "traits.h"
#include <string.h>
#include <cstdint>
#include <cassert>
#include <memory>
#include <iterator>

namespace vespalib {

namespace small_vector {

template <typename T>
constexpr size_t select_N() {
    if constexpr (sizeof(T) <= 16) {
        return (48 / sizeof(T));
    } else {
        static_assert(sizeof(T) <= 16,
                      "auto-selecting N is only supported for small objects (16 bytes or less)");
        return 1;
    }
}

template <typename T, typename... Args>
void create_at(T *ptr, Args &&...args) {
    // https://en.cppreference.com/w/cpp/memory/construct_at
    ::new (const_cast<void*>(static_cast<const volatile void*>(ptr))) T(std::forward<Args>(args)...);
}

template <typename T>
void move_objects(T *dst, T *src, uint32_t n) {
    if constexpr (std::is_trivially_copyable_v<T>) {
        // need to cast dst to void to avoid compiler warning caused by some trivially copyable objects not being copy assignable.
        memcpy(static_cast<void *>(dst), src, n * sizeof(T));
    } else {
        for (size_t i = 0; i < n; ++i) {
            create_at(dst + i, std::move(src[i]));
        }
    }
}

template <typename T>
void copy_objects(T *dst, const T *src, uint32_t n) {
    if constexpr (std::is_trivially_copyable_v<T>) {
        memcpy(dst, src, n * sizeof(T));
    } else {
        for (size_t i = 0; i < n; ++i) {
            create_at(dst + i, src[i]);
        }
    }
}

template <typename T, typename... Args>
void create_objects(T *dst, uint32_t n, Args &&...args) {
    for (size_t i = 0; i < n; ++i) {
        create_at(dst + i, std::forward<Args>(args)...);
    }
}

template <typename T>
void destroy_objects(T *src, uint32_t n) {
    if (!can_skip_destruction_v<T>) {
        std::destroy_n(src, n);
    }
}

template <typename T>
std::pair<T*,size_t> alloc_objects(size_t wanted) {
    size_t mem = roundUp2inN(wanted * sizeof(T));
    size_t entries = (mem / sizeof(T));
    mem = (entries * sizeof(T));
    T *ptr = static_cast<T*>(malloc(mem));
    assert(ptr != nullptr);
    return {ptr, entries};
}

} // namespace small_vector

/**
 * Simplified vector-like container that has space for some elements
 * inside the object itself. Intended use is to contain lists of
 * simple objects/values that are small in both size and number.
 **/
template <typename T, size_t N = small_vector::select_N<T>()>
class SmallVector
{
private:
    T *_data;
    uint32_t _size;
    uint32_t _capacity;
    alignas(T) char _space[sizeof(T) * N];
    constexpr T *local() noexcept { return reinterpret_cast<T*>(_space); }
    constexpr const T *local() const noexcept { return reinterpret_cast<const T*>(_space); }
    void expand(size_t wanted) {
        auto [new_data, new_capacity] = small_vector::alloc_objects<T>(wanted);
        small_vector::move_objects(new_data, _data, _size);
        small_vector::destroy_objects(_data, _size);
        auto old_data = _data;
        _data = new_data;
        _capacity = new_capacity;
        if (old_data != local()) {
            free(old_data);
        }
    }
    template <typename InputIt>
    void init(InputIt first, InputIt last, std::random_access_iterator_tag) {
        reserve(last - first);
        while (first != last) {
            small_vector::create_at((_data + _size++), *first++);
        }
    }
    template <typename InputIt>
    void init(InputIt first, InputIt last, std::input_iterator_tag) {
        while (first != last) {
            emplace_back(*first++);
        }
    }
public:
    constexpr SmallVector() noexcept : _data(local()), _size(0), _capacity(N) {
        static_assert(N > 0);
    }
    SmallVector(size_t n) : SmallVector() {
        reserve(n);
        small_vector::create_objects(_data, n);
        _size = n;
    }
    SmallVector(size_t n, const T &obj) : SmallVector() {
        reserve(n);
        small_vector::create_objects(_data, n, obj);
        _size = n;
    }
    SmallVector(std::initializer_list<T> list) : SmallVector() {
        reserve(list.size());
        for (const T &value: list) {
            small_vector::create_at((_data + _size++), value);
        }
    }
    template <typename InputIt, std::enable_if_t<std::is_base_of_v<std::input_iterator_tag, typename std::iterator_traits<InputIt>::iterator_category>, bool> = true>
    SmallVector(InputIt first, InputIt last) : SmallVector()
    {
        init(first, last, typename std::iterator_traits<InputIt>::iterator_category());
    }
    SmallVector(SmallVector &&rhs) : SmallVector() {
        reserve(rhs._size);
        small_vector::move_objects(_data, rhs._data, rhs._size);
        _size = rhs._size;
    }
    SmallVector(const SmallVector &rhs) : SmallVector() {
        reserve(rhs._size);
        small_vector::copy_objects(_data, rhs._data, rhs._size);
        _size = rhs._size;
    }
    SmallVector &operator=(SmallVector &&rhs) {
        assert(std::addressof(rhs) != this);
        clear();
        reserve(rhs._size);
        small_vector::move_objects(_data, rhs._data, rhs._size);
        _size = rhs._size;
        return *this;
    }
    SmallVector &operator=(const SmallVector &rhs) {
        assert(std::addressof(rhs) != this);
        clear();
        reserve(rhs._size);
        small_vector::copy_objects(_data, rhs._data, rhs._size);
        _size = rhs._size;
        return *this;
    }
    ~SmallVector() {
        small_vector::destroy_objects(_data, _size);
        if (_data != local()) {
            free(_data);
        }
    }
    bool empty() const { return (_size == 0); }
    uint32_t size() const { return _size; }
    uint32_t capacity() const { return _capacity; }
    bool is_local() const { return (_data == local()); }
    T *begin() { return _data; }
    T *end() { return (_data + _size); }
    const T *begin() const { return _data; }
    const T *end() const { return (_data + _size); }
    T &operator[](size_t idx) { return _data[idx]; }
    const T &operator[](size_t idx) const { return _data[idx]; }
    T &back() { return _data[_size - 1]; }
    const T &back() const { return _data[_size - 1]; }
    void clear() {
        small_vector::destroy_objects(_data, _size);
        _size = 0;
    }
    void reserve(size_t wanted) {
        if (__builtin_expect(wanted > _capacity, false)) {
            expand(wanted);
        }
    }
    template <typename... Args>
    void emplace_back(Args &&...args) {
        reserve(_size + 1);
        small_vector::create_at((_data + _size), std::forward<Args>(args)...);
        ++_size;
    }
    template <typename... Args>
    SmallVector &add(Args &&...args) {
        emplace_back(std::forward<Args>(args)...);
        return *this;
    }
    void push_back(const T &obj) { emplace_back(obj); }
    void push_back(T &&obj) { emplace_back(std::move(obj)); }
};

template <typename T, size_t N, size_t M>
bool operator==(const SmallVector<T,N> &a,
                const SmallVector<T,M> &b)
{
    if (a.size() != b.size()) {
        return false;
    }
    for (size_t i = 0; i < a.size(); ++i) {
        if (!(a[i] == b[i])) {
            return false;
        }
    }
    return true;
}

} // namespace
