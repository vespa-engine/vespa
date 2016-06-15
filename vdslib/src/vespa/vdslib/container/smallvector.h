// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * A vector type implementation that is optimized for keeping a small amount of
 * elements. If a small amount is kept, no malloc will be done within the
 * vector implementation.
 */

#pragma once

#include <boost/array.hpp>
#include <vespa/fastos/fastos.h>
#include <iterator>
#include <memory>
#include <vector>
#include <vespa/vespalib/util/printable.h>

namespace storage {
namespace lib {

/**
 * A generic iterator implementation using size() and operator[] to access
 * elements.
 */
template<typename Container, typename T>
class IndexedContainerIterator
    : public std::iterator<std::random_access_iterator_tag, T>,
      public vespalib::AsciiPrintable
{
    Container* _container;
    uint64_t _index;

public:
    typedef IndexedContainerIterator<Container, T> Iterator;
    typedef typename std::iterator<std::random_access_iterator_tag, T>::difference_type difference_type;
        // Required to be possible to default construct iterators
    IndexedContainerIterator() : _container(0), _index(-1) {}
    IndexedContainerIterator(Container& c, uint64_t index)
        : _container(&c), _index(index) {}

    T& operator*() { return (*_container)[_index]; }
    T* operator->() { return &(*_container)[_index]; }
    bool operator==(const Iterator& o) const {
        return (_index == o._index);
    }
    bool operator!=(const Iterator& o) const {
        return (_index != o._index);
    }
    bool operator<(const Iterator& o) const {
        return (_index < o._index);
    }

    Iterator& operator++() {
        ++_index;
        return *this;
    }
    Iterator operator++(int) {
        return Iterator(*_container, _index++);
    }
    Iterator& operator--() {
        --_index;
        return *this;
    }
    Iterator operator--(int) {
        return Iterator(*_container, _index--);
    }

    Iterator operator+(const difference_type& v) {
        return Iterator(*_container, _index + v);
    }
    difference_type operator+(const Iterator& o) {
        return _index + o._index;
    }
    Iterator operator-(const difference_type& v) {
        return Iterator(*_container, _index - v);
    }
    difference_type operator-(const Iterator& o) {
        return _index - o._index;
    }

    void print(vespalib::asciistream& out, const PrintProperties& p) const
    {
        out << "Iterator.";
        if (_index >= _container->size()) {
            out << "end";
        } else {
            out << _index;
            if (p.verbose()) {
                out << "(" << (*_container)[_index] << ")";
            }
        }
    }
};

template <typename T, size_t S = 8>
class SmallVector : public vespalib::AsciiPrintable {
    size_t _size;
    boost::array<T, S> _smallVector;
    mutable std::unique_ptr< std::vector<T> > _bigVector;

public:
    typedef IndexedContainerIterator<SmallVector<T, S>, T> iterator;
    typedef IndexedContainerIterator<const SmallVector<T, S>, const T>
            const_iterator;
    typedef T value_type;
    typedef T& reference;
    typedef const T& const_reference;
    typedef size_t difference_type;
    typedef size_t size_type;

    iterator begin() { return iterator(*this, 0); }
    iterator end() { return iterator(*this, _size); }
    const_iterator begin() const { return const_iterator(*this, 0); }
    const_iterator end() const { return const_iterator(*this, _size); }

    SmallVector() : _size(0) {}

    SmallVector(std::initializer_list<T> elems)
        : _size(0)
    {
        for (auto it=elems.begin(); it != elems.end(); ++it) {
            push_back(*it);
        }
    }

    /** Copy needs to be efficient. That's the whole basis for this class. */
    SmallVector(const SmallVector<T, S>& other) {
        operator=(other);
    }
    SmallVector<T, S>& operator=(const SmallVector<T, S>& other) {
        _size = other.size();
        _smallVector = other._smallVector;
        if (other._bigVector.get() != 0) {
            _bigVector.reset(new std::vector<T>());
            *_bigVector = *other._bigVector;
        } else {
            _bigVector.reset();
        }
        return *this;
    }

    template<size_t S2>
    SmallVector(const SmallVector<T, S2>& other) {
        operator=(other);
    }
    template<size_t S2>
    SmallVector<T, S>& operator=(const SmallVector<T, S2>& other) {
        clear();
        for (uint32_t i=0, n=other.size(); i<n; ++i) {
            push_back(other[i]);
        }
        return *this;
    }

    size_t getEfficientSizeLimit() const { return S; }

    void push_back(const T& t) {
        if (_size < S) {
            _smallVector[_size] = t;
            ++_size;
        } else {
            if (_size == S && _bigVector.get() == 0) {
                populateVector();
            }
            _bigVector->push_back(t);
            ++_size;
        }
    }
    void pop_back() {
        if (_size <= S) {
            --_size;
        } else {
            if (--_size == S) {
                _bigVector.reset();
            } else {
                _bigVector->pop_back();
            }
        }
    }
    const T& back() const { return operator[](_size - 1); }
    T& back() { return operator[](_size - 1); }
    const T& front() const { return operator[](0); }
    T& front() { return operator[](0); }
    void clear() {
        _size = 0;
        _bigVector.reset(0);
    }
    const T& operator[](size_t i) const {
        if (i < S) {
            return _smallVector[i];
        } else {
            return (*_bigVector)[i];
        }
    }
    T& operator[](size_t i) {
        if (i < S) {
            return _smallVector[i];
        } else {
            return (*_bigVector)[i];
        }
    }
    bool empty() const { return (_size == 0); }
    size_t size() const { return _size; }

    std::vector<T> getVector() const {
        std::vector<T> result;
        if (_bigVector.get() == 0) {
            for (size_t i=0; i<_size; ++i) {
                result.push_back(_smallVector[i]);
            }
        } else {
            result = *_bigVector;
        }
        return *_bigVector;
    }

    template<typename O>
    bool operator==(const O& o) const {
        if (size() != o.size()) return false;
        for (size_t i=0; i<_size; ++i) {
            if ((*this)[i] != o[i]) return false;
        }
        return true;
    }
    template<typename O>
    bool operator!=(const O& o) const {
        return !(operator==(o));
    }

    void swap(SmallVector<T, S>& other) {
            // Move current into temporaries
        size_t                          copySize(_size);
        boost::array<T, S>              copySmall(_smallVector);
        std::unique_ptr< std::vector<T> > copyBig(std::move(_bigVector));
            // Overwrite current with other
        _size = other._size;
        _smallVector = other._smallVector;
        _bigVector = std::move(other._bigVector);
            // Overwrite other with temporaries
        other._size = copySize;
        other._smallVector = copySmall;
        other._bigVector = std::move(copyBig);
    }

    void print(vespalib::asciistream& out, const PrintProperties& p) const {
        if (_size == 0) {
            out << "[]";
            return;
        }
        vespalib::asciistream ost;
        ost << operator[](0);
        bool newLineBetweenEntries = (ost.str().size() > 15);
        out << "[";
        for (size_t i=0; i<_size; ++i) {
            if (i != 0) out << ",";
            if (newLineBetweenEntries) {
                out << "\n" << p.indent(1);
            } else {
                if (i != 0) { out << " "; }
            }
            out << operator[](i);
        }
        if (newLineBetweenEntries) {
            out << "\n" << p.indent();
        }
        out << "]";
    }
    void erase(iterator eraseIt) {
        SmallVector<T, S> copy;
        for (auto it = begin(); it != end(); ++it) {
            if (it != eraseIt) {
                copy.push_back(*it);
            }
        }
        copy.swap(*this);
    }

private:
    void populateVector() const {
        assert(_bigVector.get() == 0 && _size == S);
        _bigVector.reset(new std::vector<T>());
        for (size_t i=0; i<S; ++i) {
            _bigVector->push_back(_smallVector[i]);
        }
    }
};

template <typename T, size_t S>
void
swap(SmallVector<T, S>& v1, SmallVector<T, S>& v2) {
    v1.swap(v2);
}

} // lib
} // storage
