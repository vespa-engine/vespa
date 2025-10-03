// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "alloc.h"
#include <cassert>
#include <concepts>
#include <memory>

namespace vespalib {

template <typename T, std::unsigned_integral SizeT = size_t>
class FixedCapacityFifo {
public:
    using value_type = T;
    using size_type = SizeT;
private:
    // We use Alloc directly instead of std::vector with allocator_large since
    // we _don't_ want memory to be implicitly initialized prior to actual use.
    size_type _read_idx;
    size_type _write_idx;
    size_type _size;
    size_type _capacity;
    alloc::Alloc _buf;
public:
    explicit FixedCapacityFifo(size_type capacity);
    // Note: this is _not_ a move constructor!
    FixedCapacityFifo(FixedCapacityFifo&& src, size_type new_capacity);

    FixedCapacityFifo(const FixedCapacityFifo&);
    FixedCapacityFifo& operator=(const FixedCapacityFifo&);
    FixedCapacityFifo(FixedCapacityFifo&&) noexcept;
    FixedCapacityFifo& operator=(FixedCapacityFifo&&) noexcept;

    ~FixedCapacityFifo();

    [[nodiscard]] bool empty() const noexcept { return _size == 0; }
    [[nodiscard]] bool full() const noexcept { return _size == _capacity; }
    [[nodiscard]] size_type size() const noexcept { return _size; }
    [[nodiscard]] size_type capacity() const noexcept { return _capacity; }

    class const_iterator {
    public:
        using value_type = T;
    private:
        const FixedCapacityFifo* _owner;
        size_type _cursor;
    public:
        constexpr const_iterator(const FixedCapacityFifo& owner, size_type cursor) noexcept
            : _owner(&owner),
              _cursor(cursor)
        {
        }

        bool operator==(const const_iterator& rhs) const noexcept {
            return _cursor == rhs._cursor;
        }
        const T& operator*() const noexcept {
            return *(_owner->buffer() + ((_owner->_read_idx + _cursor) & (_owner->_capacity - 1)));
        }
        const_iterator& operator++() noexcept {
            ++_cursor;
            return *this;
        }
        void operator++(int) noexcept {
            ++(*this);
        }
    };

    [[nodiscard]] const_iterator begin() const noexcept {
        return {*this, 0};
    }
    [[nodiscard]] const_iterator end() const noexcept {
        return {*this, _size};
    }

    // Precondition: !full()
    template <typename T1>
    void emplace_back(T1&& val) noexcept(noexcept(T(std::forward<T1>(val)))) {
        assert(!full());
        ::new (buffer() + _write_idx) T(std::forward<T1>(val));
        _write_idx = (_write_idx + 1) & (_capacity - 1);
        ++_size;
    }

    // Precondition: !empty()
    const T& front() const noexcept {
        return *(buffer() + _read_idx);
    }
    T& front() noexcept {
        return *(buffer() + _read_idx);
    }

    // Precondition: !empty()
    void pop_front() noexcept {
        assert(!empty());
        if constexpr (!std::is_trivially_destructible_v<T>) {
            std::destroy_at(buffer() + _read_idx);
        }
        _read_idx = (_read_idx + 1) & (_capacity - 1);
        --_size;
    }

    friend void swap(FixedCapacityFifo& lhs, FixedCapacityFifo& rhs) noexcept {
        std::swap(lhs._read_idx, rhs._read_idx);
        std::swap(lhs._write_idx, rhs._write_idx);
        std::swap(lhs._size, rhs._size);
        std::swap(lhs._capacity, rhs._capacity);
        lhs._buf.swap(rhs._buf);
    }

private:
    [[nodiscard]] T* buffer() noexcept { return static_cast<T*>(_buf.get()); }
    [[nodiscard]] const T* buffer() const noexcept { return static_cast<const T*>(_buf.get()); }
};

}
