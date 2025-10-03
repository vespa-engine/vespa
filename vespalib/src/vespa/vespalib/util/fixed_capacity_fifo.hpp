// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fixed_capacity_fifo.h"

namespace vespalib {

template <typename T, std::unsigned_integral SizeT>
FixedCapacityFifo<T, SizeT>::FixedCapacityFifo(size_type capacity)
    : _read_idx(0),
      _write_idx(0),
      _size(0),
      _capacity(std::max(roundUp2inN(capacity), size_type{4})), // To allow for bitwise AND modulo
      _buf(alloc::Alloc::alloc(sizeof(T) * _capacity))
{
}

// TODO test all of this stuff :D

template <typename T, std::unsigned_integral SizeT>
FixedCapacityFifo<T, SizeT>::FixedCapacityFifo(FixedCapacityFifo&& src, size_type new_capacity)
    : _read_idx(0),
      _write_idx(src._size & (src._capacity - 1)),
      _size(src._size),
      _capacity(std::max(roundUp2inN(src.size()), roundUp2inN(new_capacity))),
      _buf(alloc::Alloc::alloc(sizeof(T) * _capacity))
{
    if (src.size() != 0) [[likely]] {
        // Move (or transparently memmove if trivial) into our own buffer so that the
        // valid part of the source's ring is always at the start of our own ring.
        if (src._write_idx > src._read_idx) {
            // All useful memory is contained in one sequential chunk
            // 0 [------------R======W-----] Capacity
            std::uninitialized_move(src.buffer() + src._read_idx, src.buffer() + src._write_idx, buffer());
        } else {
            // Must do in two steps since the r/w cursors wrap around
            // 0 [=====W-----------R=======] Capacity
            std::uninitialized_move(src.buffer() + _read_idx, src.buffer() + src._capacity, buffer());
            const size_type n_moved = src._capacity - _read_idx;
            std::uninitialized_move(src.buffer(), src.buffer() + src._write_idx, buffer() + n_moved);
        }
    }
}

template <typename T, std::unsigned_integral SizeT>
FixedCapacityFifo<T, SizeT>::FixedCapacityFifo(const FixedCapacityFifo& src)
    : _read_idx(0),
      _write_idx(src._size & (src._capacity - 1)),
      _size(src._size),
      _capacity(src._capacity),
      _buf(alloc::Alloc::alloc(sizeof(T) * _capacity))
{
    // TODO dedupe with moving
    if (src.size() != 0) [[likely]] {
        // Copy (or transparently memcpy if trivial) into our own buffer so that the
        // valid part of the source's ring is always at the start of our own ring.
        if (src._write_idx > src._read_idx) {
            // All useful memory is contained in one sequential chunk
            // 0 [------------R======W-----] Capacity
            std::uninitialized_copy(src.buffer() + src._read_idx, src.buffer() + src._write_idx, buffer());
        } else {
            // Must do in two steps since the r/w cursors wrap around
            // 0 [=====W-----------R=======] Capacity
            std::uninitialized_copy(src.buffer() + _read_idx, src.buffer() + src._capacity, buffer());
            const size_type n_copied = src._capacity - _read_idx;
            std::uninitialized_copy(src.buffer(), src.buffer() + src._write_idx, buffer() + n_copied);
        }
    }
}

template <typename T, std::unsigned_integral SizeT>
FixedCapacityFifo<T, SizeT>&
FixedCapacityFifo<T, SizeT>::operator=(const FixedCapacityFifo& rhs) {
    FixedCapacityFifo tmp(rhs);
    swap(tmp, *this);
    return *this;
}

template <typename T, std::unsigned_integral SizeT>
FixedCapacityFifo<T, SizeT>::FixedCapacityFifo(FixedCapacityFifo&& src) noexcept
    : _read_idx(0),
      _write_idx(src._size & (src._capacity - 1)),
      _size(src._size),
      _capacity(src._capacity),
      _buf(std::move(src._buf))
{
    src._read_idx = 0;
    src._write_idx = 0;
    // FIXME this leaves the source object in a broken state! Is that OK?
    src._size = 0;
    src._capacity = 0;
}

template <typename T, std::unsigned_integral SizeT>
FixedCapacityFifo<T, SizeT>&
FixedCapacityFifo<T, SizeT>::operator=(FixedCapacityFifo&& rhs) noexcept {
    FixedCapacityFifo tmp(std::move(rhs));
    swap(tmp, *this);
    return *this;
}

template <typename T, std::unsigned_integral SizeT>
FixedCapacityFifo<T, SizeT>::~FixedCapacityFifo() = default;

}
