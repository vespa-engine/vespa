// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "lrucache_map.h"
#include <vespa/vespalib/stllike/hashtable.hpp>
#include <cassert>

namespace vespalib {

template< typename P >
typename lrucache_map<P>::insert_result
lrucache_map<P>::insert(const K & key, const V & value) {
    insert_result res = insert(value_type(key, LV(value)));
    if (res.second) {
        onInsert(key);
    }
    return res;
}

template< typename P >
typename lrucache_map<P>::insert_result
lrucache_map<P>::insert(const K & key, V && value) {
    insert_result res = insert(value_type(key, LV(std::move(value))));
    if (res.second) {
        onInsert(key);
    }
    return res;
}

template< typename P >
bool
lrucache_map<P>::removeOldest(const value_type & v) {
    (void) v;
    return (size() > capacity());
}

template< typename P >
void
lrucache_map<P>::onRemove(const K & key) {
    (void) key;
}

template< typename P >
void
lrucache_map<P>::onInsert(const K & key) {
    (void) key;
}

template< typename P >
uint32_t
lrucache_map<P>::RecordMoves::movedTo(uint32_t from) {
    for (size_t i(0); i < _lru._moved.size(); i++) {
        const MoveRecord & mr(_lru._moved[i]);
        if (mr.first == from) {
            from = mr.second;
        }
    }
    return from;
}

template< typename P >
lrucache_map<P>::RecordMoves::~RecordMoves() {
    _lru._moveRecordingEnabled = false;
    _lru._moved.clear();
}

template< typename P >
lrucache_map<P>::lrucache_map(size_t maxElems) :
    HashTable(0),
    _maxElements(maxElems),
    _head(LinkedValueBase::npos),
    _tail(LinkedValueBase::npos),
    _moveRecordingEnabled(false),
    _moved()
{ }

template< typename P >
lrucache_map<P>::~lrucache_map() = default;

template< typename P >
void
lrucache_map<P>::swap(lrucache_map & rhs) {
    auto maxElements = rhs._maxElements.load(std::memory_order_relaxed);
    rhs._maxElements.store(_maxElements.load(std::memory_order_relaxed), std::memory_order_relaxed);
    _maxElements.store(maxElements, std::memory_order_relaxed);
    std::swap(_head, rhs._head);
    std::swap(_tail, rhs._tail);
    HashTable::swap(rhs);
}

template< typename P >
void
lrucache_map<P>::move(next_t from, next_t to) {
    (void) from;
    if (_moveRecordingEnabled) {
        _moved.push_back(std::make_pair(from, to));
    }
    value_type & moved = HashTable::getByInternalIndex(to);
    if (moved.second._prev != LinkedValueBase::npos) {
        HashTable::getByInternalIndex(moved.second._prev).second._next = to;
    } else {
        _head = to;
    }
    if (moved.second._next != LinkedValueBase::npos) {
        HashTable::getByInternalIndex(moved.second._next).second._prev = to;
    } else {
        _tail = to;
    }
}

template< typename P >
void
lrucache_map<P>::erase(const K & key) {
    internal_iterator it = HashTable::find(key);
    if (it != HashTable::end()) {
        next_t h = HashTable::hash(key);
        onRemove(key);
        LV & v = it->second;
        if (v._prev != LinkedValueBase::npos) {
            HashTable::getByInternalIndex(v._prev).second._next = v._next;
        } else {
            _head = v._next;
        }
        if (v._next != LinkedValueBase::npos) {
            HashTable::getByInternalIndex(v._next).second._prev = v._prev;
        } else {
            _tail = v._prev;
        }
        HashTable::erase(*this, h, it);
    }
}

template< typename P >
typename lrucache_map<P>::iterator
lrucache_map<P>::erase(const iterator & it)
{
    iterator next(it);
    if (it != end()) {
        RecordMoves moves(*this);
        next++;
        const K & key(HashTable::getByInternalIndex(it._current).first);
        erase(key);
        next = iterator(this, moves.movedTo(next._current));
    }
    return next;
}

template< typename P >
bool
lrucache_map<P>::verifyInternals()
{
    bool retval(true);
    assert(_head != LinkedValueBase::npos);
    assert(_tail != LinkedValueBase::npos);
    assert(HashTable::getByInternalIndex(_head).second._prev == LinkedValueBase::npos);
    assert(HashTable::getByInternalIndex(_tail).second._next == LinkedValueBase::npos);
    {
        size_t i(0);
        size_t prev(LinkedValueBase::npos);
        size_t c(_head);
        for(size_t m(size()); (c != LinkedValueBase::npos) && (i < m); c = HashTable::getByInternalIndex(c).second._next, i++) {
            assert((HashTable::getByInternalIndex(c).second._prev == prev));
            prev = c;
        }
        assert(i == size());
        assert(c == LinkedValueBase::npos);
    }
    {
        size_t i(0);
        size_t next(LinkedValueBase::npos);
        size_t c(_tail);
        for(size_t m(size()); (c != LinkedValueBase::npos) && (i < m); c = HashTable::getByInternalIndex(c).second._prev, i++) {
            assert((HashTable::getByInternalIndex(c).second._next == next));
            next = c;
        }
        assert(i == size());
        assert(c == LinkedValueBase::npos);
    }
    return retval;
}

template< typename P >
void
lrucache_map<P>::move(NodeStore && oldStore)
{
    next_t curr(_tail);
    _tail = LinkedValueBase::npos;
    _head = LinkedValueBase::npos;
    
    while (curr != LinkedValueBase::npos) {
        value_type & v = oldStore[curr].getValue();
        curr = v.second._prev;
        v.second._prev = LinkedValueBase::npos;
        v.second._next = LinkedValueBase::npos;
        insert(std::move(v));
    }
}

template< typename P >
void
lrucache_map<P>::removeOld() {
    if (_tail != LinkedValueBase::npos) {
        for (value_type * last(& HashTable::getByInternalIndex(_tail));
             (_tail != _head) && removeOldest(*last);
             last = & HashTable::getByInternalIndex(_tail))
        {
            _tail = last->second._prev;
            HashTable::getByInternalIndex(_tail).second._next = LinkedValueBase::npos;
            HashTable::erase(*this, HashTable::hash(last->first), HashTable::find(last->first));
        }
    }
}

template< typename P >
void
lrucache_map<P>::ref(const internal_iterator & it) {
    uint32_t me(it.getInternalIndex());
    if (me != _head) {
        LV & v = it->second;
        LV & oldPrev = HashTable::getByInternalIndex(v._prev).second;
        oldPrev._next = v._next;
        if (me != _tail) {
            LV & oldNext = HashTable::getByInternalIndex(v._next).second;
            oldNext._prev = v._prev;
        } else {
            // I am tail and I am not the only one.
            _tail = v._prev;
        }
        LV & oldHead = HashTable::getByInternalIndex(_head).second;
        oldHead._prev = me;
        v._next = _head;
        v._prev = LinkedValueBase::npos;
        _head = me;
    }
}

template< typename P >
typename lrucache_map<P>::insert_result
lrucache_map<P>::insert(value_type && value) {
    insert_result res = HashTable::insertInternal(std::forward<value_type>(value));
    uint32_t next(_head);
    if ( ! res.second) {
        ref(res.first);
    } else {
        _head = res.first.getInternalIndex();
        HashTable::getByInternalIndex(_head).second._next = next;
        if (next != LinkedValueBase::npos) {
            HashTable::getByInternalIndex(next).second._prev = _head;
        }
        if (_tail == LinkedValueBase::npos) {
            _tail = _head;
        }
        removeOld();
        if (_head != res.first.getInternalIndex()) {
            res.first.setInternalIndex(_head);
        }
    }
    return res;
}

template< typename P >
typename P::Value &
lrucache_map<P>::operator [] (const K & key)
{
    return insert(key, V()).first->second._value;
}

template< typename P >
typename P::Value *
lrucache_map<P>::findAndRef(const K & key)
{
    internal_iterator found = HashTable::find(key);
    if (found != HashTable::end()) {
        if (size()*2 > capacity()) {
            ref(found);
        }
        return &found->second._value;
    }
    return nullptr;
}

}

