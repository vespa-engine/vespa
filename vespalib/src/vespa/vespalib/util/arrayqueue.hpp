// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <cstdlib>
#include <cassert>
#include <algorithm>
#include <concepts>

namespace vespalib {

/**
 * A queue implemented as a circular array. Elements may be inserted
 * and extracted at both ends of the queue. Access to the i'th element
 * inside the queue can be done in constant time. The internal array
 * will be grown in size when needed. All inserting and moving of
 * objects within the queue will use the copy constructor and
 * destructor of T.
 **/
template <typename T>
class ArrayQueue
{
private:
    T       *_data;     // space allocated for actual objects
    uint32_t _capacity; // current maximum queue size
    uint32_t _used;     // the number of items in the queue
    uint32_t _skew;     // the circular skew of this queue

    /**
     * Copy all items on this queue into the given queue while
     * retaining item order. The items will be inserted at the back of
     * the target queue.
     *
     * @param q the target queue
     **/
    void copyInto(ArrayQueue<T> &q) const {
        for (uint32_t i = 0; i < _used; ++i) {
            q.emplace(peek(i));
        }
    }

    /**
     * Move all items on this queue into the given queue while
     * retaining item order. The items will be inserted at the back of
     * the target queue.
     *
     * @param q the target queue
     **/
    void moveInto(ArrayQueue<T> &q) {
        while (_used > 0) {
            q.emplace(std::move(access(0)));
            pop();
        }
    }

    /**
     * Suggest a new capacity that will be able to contain 'n' more
     * elements than are currently on the queue.
     *
     * @return new capacity
     * @param n the additional elements we want room for
     **/
    uint32_t suggestCapacity(uint32_t n) const {
        uint32_t newCapacity = _capacity;
        uint32_t minCapacity = _used + n;
        if (newCapacity < 16) {
            newCapacity = 16;
        }
        while (newCapacity < minCapacity) {
            newCapacity *= 2;
        }
        return newCapacity;
    }

    /**
     * Calculate the offset into the backing object array for the
     * given index. The front of the queue will have index 0 and the
     * tail of the queue will have index size() - 1.
     *
     * @return array offset for the given index
     * @param idx the index of the item for which we want the array offset
     **/
    uint32_t offset(uint32_t idx) const {
        return ((_skew + idx) % _capacity);
    }

    /**
     * Calculate the raw address of the object located at the given
     * index. The front of the queue will have index 0 and the tail of
     * the queue will have index size() - 1.
     *
     * @return raw object address for the given index
     * @param idx the index of the object for which we want the raw address
     **/
    void *address(uint32_t idx) const {
        return ((void *)(&_data[offset(idx)]));
    }

public:
    /**
     * Create an empty queue with an initial capacity of 0.
     **/
    ArrayQueue() noexcept : _data(0), _capacity(0), _used(0), _skew(0) {}

    /**
     * Create an empty queue with the given initial capacity.
     *
     * @param cap initial capacity
     **/
    explicit ArrayQueue(uint32_t cap) noexcept
        : _data((T*)malloc(sizeof(T) * cap)), _capacity(cap), _used(0), _skew(0)
    {}

    /**
     * Create a queue that is a copy of another queue. Now with funky
     * shit to make the queue itself non-copyable if the elements in
     * the queue are non-copyable.
     *
     * @param q the queue that should be copied
     **/
    ArrayQueue(const ArrayQueue &q) requires std::copy_constructible<T>
        : _data((T*)malloc(sizeof(T) * q._capacity)), _capacity(q._capacity), _used(0), _skew(0)
    {
        try {
            q.copyInto(*this);
        } catch (...) {
            clear();
            free(_data);
            throw;
        }
    }

    /**
     * Move constructor
     *
     * @param q the queue that should be moved
     **/
    ArrayQueue(ArrayQueue &&q) noexcept : _data(0), _capacity(0), _used(0), _skew(0)
    {
        swap(q);
    }

    /**
     * Assignment operator with copy semantics for queues.
     *
     * @return this object
     * @param rhs the right hand side of the assignment
     **/
    ArrayQueue &operator=(const ArrayQueue &rhs) {
        ArrayQueue tmp(rhs);
        swap(tmp);
        return *this;
    }

    /**
     * Assignment operator with move semantics for queues.
     *
     * @return this object
     * @param rhs the right hand side of the assignment
     **/
    ArrayQueue &operator=(ArrayQueue &&rhs) {
        swap(rhs);
        return *this;
    }

    /**
     * Make sure that this queue has enough space for 'n' additional
     * elements.
     *
     * @param n the number of additional elements to reserve space for
     **/
    void reserve(uint32_t n) {
        if ((_used + n) > _capacity) {
            ArrayQueue q(suggestCapacity(n));
            moveInto(q);
            swap(q);
        }
    }

    /**
     * Obtain the current capacity of this queue.
     *
     * @return current queue capacity
     **/
    uint32_t capacity() const {
        return _capacity;
    }

    /**
     * Obtain the number of elements in this queue.
     *
     * @return number of elements in this queue
     **/
    uint32_t size() const {
        return _used;
    }

    /**
     * Check whether this queue is empty.
     *
     * @return true if (and only if) the queue is empty
     **/
    bool empty() const {
        return (_used == 0);
    }

    /**
     * Insert an item at the back of this queue.
     *
     * @param item the item to insert
     **/
    void push(const T &item) {
        emplace(item);
    }

    /**
     * Insert an item at the back of this queue.
     *
     * @param item the item to insert
     **/
    void push(T &&item) {
        emplace(std::move(item));
    }

    /**
     * Insert an item at the front of this queue.
     *
     * @param item the item to insert
     **/
    void pushFront(const T &item) {
        emplaceFront(item);
    }

    /**
     * Insert an item at the front of this queue.
     *
     * @param item the item to insert
     **/
    void pushFront(T &&item) {
        emplaceFront(std::move(item));
    }

    /**
     * Insert an item at the back of this queue.
     *
     * @param args constructor args
     **/
    template <typename... Args>
    void emplace(Args &&...args) {
        reserve(1);
        new (address(_used)) T(std::forward<Args>(args)...);
        ++_used;
    }

    /**
     * Insert an item at the front of this queue.
     *
     * @param args constructor args
     **/
    template <typename... Args>
    void emplaceFront(Args &&...args) {
        reserve(1);
        new (address(_capacity - 1)) T(std::forward<Args>(args)...);
        _skew = offset(_capacity - 1);
        ++_used;
    }

    /**
     * Remove the item at the front of this queue. This method may not
     * be invoked on an empty queue.
     **/
    void pop() {
        assert(!empty());
        _data[offset(0)].~T();
        _skew = offset(1);
        --_used;
    }

    /**
     * Remove the item at the back of this queue. This method may not
     * be invoked on an empty queue.
     **/
    void popBack() {
        assert(!empty());
        _data[offset(_used - 1)].~T();
        --_used;
    }

    /**
     * Remove all elements from this queue.
     **/
    void clear() {
        for (uint32_t i = 0; i < _used; ++i) {
            _data[offset(i)].~T();
        }
        _used = 0;
    }

    /**
     * Look at an item within this queue. The given index enumerates
     * the items in the queue relative to the front of the queue (the
     * front has index 0 and the back has index size-1). This method
     * may not be invoked with an index that is not in the range [0,
     * size - 1].
     *
     * @return the item value
     * @param idx index of the item we want to look at
     **/
    const T &peek(uint32_t idx) const {
        assert(idx < _used);
        return _data[offset(idx)];
    }

    /**
     * Access an item within this queue. The given index enumerates
     * the items in the queue relative to the front of the queue (the
     * front has index 0 and the back has index size-1). This method
     * may not be invoked with an index that is not in the range [0,
     * size - 1].
     *
     * @return the item
     * @param idx index of the item we want to access
     **/
    T &access(uint32_t idx) {
        assert(idx < _used);
        return _data[offset(idx)];
    }

    /**
     * Look at the item at the front of this queue. This method may
     * not be invoked on an empty queue.
     *
     * @return the item value
     **/
    T &front() { return access(0); }

    /**
     * Look at the item at the front of this queue. This method may
     * not be invoked on an empty queue.
     *
     * @return the item value
     **/
    const T &front() const { return peek(0); }

    /**
     * Look at the item at the back of this queue. This method may
     * not be invoked on an empty queue.
     *
     * @return the item value
     **/
    const T &back() const {
        return peek(_used - 1);
    }

    /**
     * Swap the internal state of this queue with the given queue.
     *
     * @param q the queue we want to swap state with
     **/
    void swap(ArrayQueue<T> &q) noexcept {
        std::swap(_data, q._data);
        std::swap(_capacity, q._capacity);
        std::swap(_used, q._used);
        std::swap(_skew, q._skew);
    }

    /**
     * Destructs all items on the queue and cleans up memory usage.
     **/
    ~ArrayQueue() {
        clear();
        free(_data);
    }
};

} // namespace vespalib

