// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "hashmapdata.h"
#include <cstring>
#include <cstdlib>
#include <assert.h>

/**
 * @brief namespace for generic Vespa library
 **/
namespace vespalib {

size_t hashValue(const char *str);
size_t hashValue(const void * buf, size_t sz);

/**
 * @brief Simple hash map implementation
 *
 * The type T must have normal copy semantics, and a special empty
 * value must be provided that lookup can return when no mapping exists.
 * The key is always a zero-terminated C string and key matching is done
 * via strcmp().  The hash function used on the key is vespalib::hashValue()
 * and the map will auto-resize to become bigger when enough entries have
 * been added.
 **/
template <typename T>
class HashMap
{
private:
    class HashMapKey
    {
    private:
        HashMapKey(const HashMapKey &);
        HashMapKey& operator=(const HashMapKey &);
    public:
        HashMapKey(const char *data) : _data(strdup(data)) {}
        ~HashMapKey() { free(_data); }
        bool operator== (const char *other) {
            return (strcmp(_data, other) == 0);
        }
        const char *key() { return _data; }
    private:
        char *_data;
    };

    struct Entry
    {
        Entry       *_next;
        HashMapKey   _key;
        T            _value;

        Entry(Entry *next, const char *key, const T &value)
            : _next(next), _key(key), _value(value) {}

    private:
        Entry(const Entry &);
        Entry& operator=(const Entry &);
    };

    Entry    **_table;
    uint32_t   _tableSize;
    uint32_t   _rehashSize;
    uint32_t   _entryCnt;
    T          _empty;

    // not defined [2]
    HashMap(const HashMap<T> &);
    HashMap<T>& operator=(const HashMap<T> &);

    inline uint32_t getSize(uint32_t minBuckets) const;
    Entry *lookup(const char *key) const;

public:
    /**
     * @brief Iterator for HashMap class
     *
     * Note that iterators are unsafe; if the hashmap is changed
     * the iterator may refer to freed data.
     **/
    class Iterator
    {
        friend class HashMap<T>;
    private:
        typename HashMap<T>::Entry **_table;
        uint32_t                     _tableSize;
        uint32_t                     _idx;
        typename HashMap<T>::Entry  *_entry;

        explicit Iterator(const HashMap<T> *map);
    public:
        inline Iterator(const Iterator &src);
        inline Iterator &operator=(const Iterator &src);
        inline bool valid() const { return _entry != NULL; }
        inline const char *key() const { return _entry->_key.key(); };
        inline const T &value() const { return _entry->_value; };
        inline void next();
    };

    friend class Iterator;

    explicit HashMap(const T &empty, uint32_t minBuckets = 50);
    ~HashMap();

    void clear();
    T set(const char *key, const T &value);
    inline bool isSet(const char *key) const;
    inline const T &get(const char *key) const;
    T remove(const char *key);
    inline const T& operator[](const char *key) const; // R-value
    inline Iterator iterator() const;

    uint32_t size() const { return _entryCnt; }
    bool isEmpty() const { return _entryCnt == 0; }
    uint32_t buckets() const { return _tableSize; }
    uint32_t maxDepth() const;
    uint32_t emptyBuckets() const;
};

//-----------------------------------------------------------------------------


template <typename T>
HashMap<T>::Iterator::Iterator(const HashMap<T> *map)
    : _table(map->_table),
      _tableSize(map->_tableSize),
      _idx(0),
      _entry(NULL)
{
    while (_entry == NULL && _idx < _tableSize)
        _entry = _table[_idx++];
}


template <typename T>
HashMap<T>::Iterator::Iterator(const Iterator &src)
    : _table(src._table),
      _tableSize(src._tableSize),
      _idx(src._idx),
      _entry(src._entry)
{
}


template <typename T>
typename HashMap<T>::Iterator &
HashMap<T>::Iterator::operator=(const Iterator &src)
{
    _table     = src._table;
    _tableSize = src._tableSize;
    _idx       = src._idx;
    _entry     = src._entry;
    return *this;
}


template <typename T>
void
HashMap<T>::Iterator::next()
{
    _entry = _entry->_next;
    while (_entry == NULL && _idx < _tableSize)
        _entry = _table[_idx++];
}

//-----------------------------------------------------------------------------

template <typename T>
uint32_t
HashMap<T>::getSize(uint32_t minBuckets) const
{
    for (uint32_t my_i = 0; my_i < HashMapData::sizeStepsSize; my_i++)
        if (HashMapData::sizeSteps[my_i] >= minBuckets)
            return HashMapData::sizeSteps[my_i];
    return minBuckets;
}


template <typename T>
typename HashMap<T>::Entry *
HashMap<T>::lookup(const char *key) const
{
    uint32_t bucket = hashValue(key) % _tableSize;
    Entry *pt = _table[bucket];
    for (; pt != NULL; pt = pt->_next)
        if (pt->_key == key)
            break;
    return pt;
}


template <typename T>
HashMap<T>::HashMap(const T &empty, uint32_t minBuckets)
    : _table(NULL),
      _tableSize(0),
      _rehashSize(0),
      _entryCnt(0),
      _empty(empty)
{
    _tableSize = getSize(minBuckets);
    _rehashSize = (_tableSize * 3) / 5;
    _table = new Entry*[_tableSize];
    memset(_table, 0, _tableSize * sizeof(Entry *));
}


template <typename T>
HashMap<T>::~HashMap()
{
    clear();
    delete [] _table;
}


template <typename T>
void
HashMap<T>::clear()
{
    for (uint32_t my_i = 0; my_i < _tableSize; my_i++) {
        while (_table[my_i] != NULL) {
            Entry *entry = _table[my_i];
            _table[my_i] = entry->_next;
            delete entry;
        }
    }
    _entryCnt = 0;
}


template <typename T>
T
HashMap<T>::set(const char *key, const T &value)
{
    uint32_t bucket = hashValue(key) % _tableSize;
    Entry *pt = _table[bucket];
    for (; pt != NULL; pt = pt->_next)
        if (pt->_key == key)
            break;
    if (pt == NULL) {
        _table[bucket] = new Entry(_table[bucket], key, value);
        _entryCnt++;
        return _empty;
    }
    T ret = pt->_value;
    pt->_value = value;

    if (_entryCnt > _rehashSize &&
        _tableSize < HashMapData::sizeSteps[HashMapData::sizeStepsSize - 1])
    {
        uint32_t newsize = getSize(_tableSize+1);
        Entry ** newtable = new Entry*[newsize];
        memset(newtable, 0, newsize * sizeof(Entry *));
        for (uint32_t i = 0; i < _tableSize; i++) {
            Entry *p = _table[i];
            while (p != NULL) {
                Entry *np = p->_next;
                uint32_t newhash = hashValue(p->_key.key()) % newsize;
                // prepend entry to new hash slot
                p->_next = newtable[newhash];
                newtable[newhash] = p;
                p = np;
            }
            // chain moved to newTable
            _table[i] = NULL;
        }
        delete[] _table;
        _table = newtable;
        _tableSize = newsize;
        _rehashSize = (_tableSize * 3) / 5;
    }

    return ret;
}


template <typename T>
bool
HashMap<T>::isSet(const char *key) const
{
    return (lookup(key) != NULL);
}


template <typename T>
const T &
HashMap<T>::get(const char *key) const
{
    Entry *entry = lookup(key);
    if (entry != NULL) {
        return entry->_value;
    } else {
        return _empty;
    }
}


template <typename T>
T
HashMap<T>::remove(const char *key)
{
    uint32_t bucket = hashValue(key) % _tableSize;
    Entry **ptpt = &(_table[bucket]);
    for (; (*ptpt) != NULL; ptpt = &((*ptpt)->_next))
        if ((*ptpt)->_key == key)
            break;
    if ((*ptpt) == NULL) {
        return _empty;
    } else {
        Entry *entry = (*ptpt);
        T ret = entry->_value;
        (*ptpt) = (*ptpt)->_next; // link out entry
        delete entry;
        _entryCnt--;
        return ret;
    }
}


template <typename T>
const T &
HashMap<T>::operator[](const char *key) const
{
    Entry *entry = lookup(key);
    if (entry != NULL) {
        return entry->_value;
    } else {
        return _empty;
    }
}


template <typename T>
typename HashMap<T>::Iterator
HashMap<T>::iterator() const
{
    return Iterator(this);
}


template <typename T>
uint32_t
HashMap<T>::maxDepth() const
{
    uint32_t ret = 0;
    uint32_t cnt = 0;
    for (uint32_t i = 0; i < _tableSize; i++) {
        uint32_t d = 0;
        for (Entry *p = _table[i]; p != NULL; p = p->_next) {
            ++d;
            ++cnt;
        }
        if (d > ret) ret = d;
    }
    assert(cnt == _entryCnt);
    if (cnt != _entryCnt) abort();
    return ret;
}


template <typename T>
uint32_t
HashMap<T>::emptyBuckets() const
{
    uint32_t ret = 0;
    for (uint32_t i = 0; i < _tableSize; i++) {
        if (_table[i] == NULL)
            ++ret;
    }
    return ret;
}


} // namespace vespalib

