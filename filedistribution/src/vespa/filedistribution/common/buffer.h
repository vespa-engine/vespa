// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <algorithm>

namespace filedistribution {

struct USED_FOR_MOVING {};

template <class T>
class Move {
    mutable T _holder;
public:
    Move(T& toMove)
        :_holder(USED_FOR_MOVING())
    {
        _holder.swap(toMove);
    }

    Move(const Move& other)
        :_holder(USED_FOR_MOVING())
    {
        _holder.swap(other._holder);
    }

    void swap(T& t) const {
        _holder.swap(t);
    }

private:
    Move& operator=(const Move&);
};

template <class T>
inline Move<T> move(T& t) {
    return Move<T>(t);
}

class Buffer {
    size_t _capacity;
    char* _buf;
    size_t _size;
public:
    typedef char value_type;
    typedef const value_type& const_reference;
    typedef char* iterator;
    typedef const char* const_iterator;

    Buffer(const Buffer &) = delete;
    Buffer & operator = (const Buffer &) = delete;
    explicit Buffer(size_t capacityArg)
        :_capacity(capacityArg),
         _buf( new char[_capacity] ),
         _size(0)
    {}

    Buffer(const Move<Buffer>& buffer);

    explicit Buffer(USED_FOR_MOVING)
        :_capacity(0),
         _buf(0),
         _size(0)
    {}

    template <typename ITER>
    Buffer(ITER beginIter, ITER endIter)
        : _capacity(endIter-beginIter),
          _buf( new char[_capacity] ),
          _size(_capacity)
    {
        std::copy(beginIter, endIter, begin());
    }

    ~Buffer() {
        delete[] _buf;
    }

    size_t capacity() const {
        return _capacity;
    }

    size_t size() const {
        return _size;
    }

    //might expose uninitialized memory
    void resize(size_t newSize) {
        if ( newSize <= _capacity )
            _size = newSize;
        else {
            reserve(newSize);
            _size = newSize;
        }
    }

    void reserve(size_t newCapacity) {
        if ( newCapacity > _capacity ) {
            Buffer buffer(newCapacity);
            buffer._size = _size;
            std::copy(begin(), end(), buffer.begin());
            buffer.swap(*this);
        }
    }

    void swap(Buffer& other) {
        std::swap(_capacity, other._capacity);
        std::swap(_buf, other._buf);
        std::swap(_size, other._size);
    }

    void push_back(char c) {
        if (_size == _capacity)
            reserve(_capacity * 2);
        _buf[_size++] = c;
    }

    iterator begin() {
        return _buf;
    }

    iterator end() {
        return _buf + _size;
    }

    const_iterator begin() const {
        return _buf;
    }

    const_iterator end() const {
        return _buf + _size;
    }

    char operator[](size_t i) const {
        return _buf[i];
    }

    char& operator[](size_t i) {
        return _buf[i];
    }
};

inline Buffer::Buffer(const Move<Buffer>& buffer)
    :_capacity(0),
     _buf(0),
     _size(0)
{
    buffer.swap(*this);
}

} //namespace filedistribution


