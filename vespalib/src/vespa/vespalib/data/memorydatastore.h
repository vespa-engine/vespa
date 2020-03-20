// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/alloc.h>
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/util/sync.h>
#include <vector>

namespace vespalib {

/**
 * This is a backing store intended for small size variable length data elemets.
 * It has the important property that once an object has been allocated it does not move in memory.
 * It will start of by allocating one backing buffer and items stored will be appended here.
 * When limit is exceeded a new buffer is allocated with twice the size of the previous and so it goes.
 **/
class MemoryDataStore {
public:
    class Reference {
    public:
        Reference(void * data_) : _data(data_) { }
        void * data() { return _data; }
        const char * c_str() const { return static_cast<const char *>(_data); }
    private:
        void   * _data;
    };
    MemoryDataStore(alloc::Alloc && initialAlloc=alloc::Alloc::alloc(256), Lock * lock=nullptr);
    MemoryDataStore(const MemoryDataStore &) = delete;
    MemoryDataStore & operator = (const MemoryDataStore &) = delete;
    ~MemoryDataStore();
    /**
     * Will allocate space and copy the data in. The returned pointer will be valid
     * for the lifetime of this object.
     * @return A pointer/reference to the freshly stored object.
     */
    Reference push_back(const void * data, const size_t sz);
    void swap(MemoryDataStore & rhs) { _buffers.swap(rhs._buffers); }
    void clear() {
        _buffers.clear();
    }
private:
    std::vector<alloc::Alloc> _buffers;
    size_t _writePos;
    Lock * _lock;
};

class VariableSizeVector
{
public:
    class Reference {
    public:
        Reference(void * data_, size_t sz) : _data(data_), _sz(sz) { }
        void * data() { return _data; }
        const char * c_str() const { return static_cast<const char *>(_data); }
        size_t size() const { return _sz; }
    private:
        void   * _data;
        size_t   _sz;
    };
    class iterator {
    public:
        iterator(vespalib::Array<Reference> & v, size_t index) : _vector(&v), _index(index) {}
        Reference & operator  * () const { return (*_vector)[_index]; }
        Reference * operator -> () const { return &(*_vector)[_index]; }
        iterator & operator ++ () {
            _index++;
            return *this;
        }
        iterator operator ++ (int) {
            iterator prev = *this;
            ++(*this);
            return prev;
        }
        bool operator==(const iterator& rhs) const { return (_index == rhs._index); }
        bool operator!=(const iterator& rhs) const { return (_index != rhs._index); }
    private:
        vespalib::Array<Reference> * _vector;
        size_t _index;
    };
    class const_iterator {
    public:
        const_iterator(const vespalib::Array<Reference> & v, size_t index) : _vector(&v), _index(index) {}
        const Reference & operator  * () const { return (*_vector)[_index]; }
        const Reference * operator -> () const { return &(*_vector)[_index]; }
        const_iterator & operator ++ () {
            _index++;
            return *this;
        }
        const_iterator operator ++ (int) {
            const_iterator prev = *this;
            ++(*this);
            return prev;
        }
        bool operator==(const const_iterator& rhs) const { return (_index == rhs._index); }
        bool operator!=(const const_iterator& rhs) const { return (_index != rhs._index); }
    private:
        const vespalib::Array<Reference> * _vector;
        size_t _index;
    };
    VariableSizeVector(const VariableSizeVector &) = delete;
    VariableSizeVector & operator = (const VariableSizeVector &) = delete;
    VariableSizeVector(size_t initialCount, size_t initialBufferSize);
    ~VariableSizeVector();
    iterator begin() { return iterator(_vector, 0); }
    iterator end() { return iterator(_vector, size()); }
    const_iterator begin() const { return const_iterator(_vector, 0); }
    const_iterator end() const { return const_iterator(_vector, size()); }
    Reference push_back(const void * data, const size_t sz);
    Reference operator [] (uint32_t index) const { return _vector[index]; }
    size_t size() const { return _vector.size(); }
    bool empty() const { return _vector.empty(); }
    void swap(VariableSizeVector & rhs) {
        _vector.swap(rhs._vector);
        _store.swap(rhs._store);
    }
    void clear() {
        _vector.clear();
        _store.clear();
    }
private:
    vespalib::Array<Reference> _vector;
    MemoryDataStore _store;
};

} // namespace vespalib

