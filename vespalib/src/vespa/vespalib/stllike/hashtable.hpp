// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "hashtable.h"
#include <vespa/vespalib/util/array.hpp>
#include <algorithm>

namespace vespalib {

namespace {

/// TODO Currently we require that you have atleast one element in _nodes to avoid one extra branch
/// However that means that empty unused hashtables are larger than necessary.
/// This we should probably reconsider.
template<typename Modulator>
uint32_t
computeModulo(size_t size) {
    return (size > 0) ? Modulator::selectHashTableSize(roundUp2inN(size) / 3) : 1;
}

template <typename NodeStore>
NodeStore
createStore(size_t size, uint32_t modulo) {
    size = (size > 0) ? roundUp2inN(std::max(size_t(modulo), roundUp2inN(size))) : 1;
    NodeStore store;
    store.reserve(size);
    store.resize(modulo);
    return store;
}

}
template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
void hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::swap(hashtable & rhs)
{
    std::swap(_modulator, rhs._modulator);
    std::swap(_count, rhs._count);
    _nodes.swap(rhs._nodes);
    std::swap(_hasher, rhs._hasher);
    std::swap(_equal, rhs._equal);
    std::swap(_keyExtractor, rhs._keyExtractor);
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::hashtable(size_t reservedSpace) :
    _modulator(computeModulo<Modulator>(reservedSpace)),
    _count(0),
    _nodes(createStore<NodeStore>(reservedSpace, _modulator.getTableSize()))
{ }

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::hashtable(size_t reservedSpace, const Hash & hasher, const Equal & equal) :
    _modulator(computeModulo<Modulator>(reservedSpace)),
    _count(0),
    _nodes(createStore<NodeStore>(reservedSpace, _modulator.getTableSize())),
    _hasher(hasher),
    _equal(equal)
{
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::hashtable(const hashtable &) = default;

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator> &
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::operator = (const hashtable &) = default;

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::~hashtable() = default;

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
typename hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::iterator
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::find(const Key & key)
{
    next_t h = hash(key);
    if (__builtin_expect(_nodes[h].valid(), true)) {
        do {
            if (__builtin_expect(_equal(_keyExtractor(_nodes[h].getValue()), key), true)) {
                return iterator(this, h);
            }
            h = _nodes[h].getNext();
        } while (h != Node::npos);
    }
    return end();
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
typename hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::const_iterator
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::find(const Key & key) const
{
    next_t h = hash(key);
    if (__builtin_expect(_nodes[h].valid(), true)) {
        do {
            if (__builtin_expect(_equal(_keyExtractor(_nodes[h].getValue()), key), true)) {
                return const_iterator(this, h);
            }
            h = _nodes[h].getNext();
        } while (h != Node::npos);
    }
    return end();
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
template< typename AltKey>
typename hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::iterator
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::find(const AltKey & key)
{
    next_t h = hash(key);
    if (__builtin_expect(_nodes[h].valid(), true)) {
        do {
            if (__builtin_expect(_equal(_keyExtractor(_nodes[h].getValue()), key), true)) {
                return iterator(this, h);
            }
            h = _nodes[h].getNext();
        } while (h != Node::npos);
    }
    return end();
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
template< typename AltKey>
typename hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::const_iterator
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::find(const AltKey & key) const
{
    next_t h = hash(key);
    if (__builtin_expect(_nodes[h].valid(), true)) {
        do {
            if (__builtin_expect(_equal(_keyExtractor(_nodes[h].getValue()), key), true)) {
                return const_iterator(this, h);
            }
            h = _nodes[h].getNext();
        } while (h != Node::npos);
    }
    return end();
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
void
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::erase(const Key & key) {
    const_iterator found(find(key));
    if (found != end()) {
        DefaultMoveHandler moveHandler;
        erase(moveHandler, hash(key), found);
    }
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
void
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::clear() {
    _nodes.clear();
    _count = 0;
    _nodes.resize(getTableSize());
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
template< typename V >
typename hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::insert_result
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::insertInternal(V && node)
{
    const next_t h = hash(_keyExtractor(node));
    if ( ! _nodes[h].valid() ) {
        _nodes[h] = std::forward<V>(node);
        _count++;
        return insert_result(iterator(this, h), true);
    } else {
        for (next_t c(h); c != Node::npos; c = _nodes[c].getNext()) {
            if (_equal(_keyExtractor(_nodes[c].getValue()), _keyExtractor(node))) {
                return insert_result(iterator(this, c), false);
            }
        }
        if (_nodes.size() < _nodes.capacity()) {
            const next_t p(_nodes[h].getNext());
            const next_t newIdx(_nodes.size());
            _nodes[h].setNext(newIdx);
            new (_nodes.push_back_fast()) Node(std::forward<V>(node), p);
            _count++;
            return insert_result(iterator(this, newIdx), true);
        } else {
            resize(_nodes.capacity()*2);
            return insertInternal(std::forward<V>(node));
        }
    }
}


template <typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator>
void
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::force_insert(Value && value)
{
    const next_t h = hash(_keyExtractor(value));
    if ( ! _nodes[h].valid() ) {
        _nodes[h] = std::move(value);
        _count++;
    } else {
        if (_nodes.size() < _nodes.capacity()) {
            const next_t p(_nodes[h].getNext());
            const next_t newIdx(_nodes.size());
            _nodes[h].setNext(newIdx);
            new (_nodes.push_back_fast()) Node(std::move(value), p);
            _count++;
        } else {
            resize(_nodes.capacity()*2);
            force_insert(std::move(value));
        }
    }
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
template<typename MoveHandler>
void
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::reclaim(MoveHandler & moveHandler, next_t node)
{
    size_t last(_nodes.size()-1);
    if (last >= getTableSize()) {
        if (last != node) {
            next_t h = hash(_keyExtractor(_nodes[last].getValue()));
            for (next_t n(_nodes[h].getNext()); n != last; n=_nodes[h].getNext()) {
                h = n;
            }
            move(moveHandler, last, node);
            _nodes[h].setNext(node);
        }
        _nodes.resize(last);
    }
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
template <typename Func>
void
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::for_each(Func func) const
{
    uint32_t i(0);
    for (; i < _modulator.getTableSize(); i++) {
        if (_nodes[i].valid()) {
            func(_nodes[i].getValue());
        }
    }
    for (; i < _nodes.size(); i++) {
        func(_nodes[i].getValue());
    }
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
template <typename MoveHandler>
void
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::erase(MoveHandler & moveHandler, next_t h, const const_iterator & it)
{
    next_t prev = Node::npos;
    do {
        if (h == it.getInternalIndex()) {
            if (prev != Node::npos) {
                _nodes[prev].setNext(_nodes[h].getNext());
                reclaim(moveHandler, h);
            } else {
                if (_nodes[h].hasNext()) {
                    next_t next = _nodes[h].getNext();
                    move(moveHandler, next, h);
                    reclaim(moveHandler, next);
                } else {
                    _nodes[h].invalidate();
                }
            }
            _count--;
            return;
        }
        prev = h;
        h = _nodes[h].getNext();
    } while (h != Node::npos);
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
void
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::resize(size_t newSize)
{
    next_t newModulo = computeModulo<Modulator>(newSize);
    NodeStore newStore = createStore<NodeStore>(newSize, newModulo);
    _modulator = Modulator(newModulo);
    _count = 0;
    _nodes.swap(newStore);
    move(std::move(newStore));
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
void
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::move(NodeStore && oldStore)
{
    for (auto & entry : oldStore) {
        if (entry.valid()) {
            force_insert(std::move(entry.getValue()));
        }
    }
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
size_t
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::getMemoryConsumption() const
{
    return sizeof(hashtable<Key, Value, Hash, Equal, KeyExtract>)
            + _nodes.capacity() * sizeof(Node);
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
size_t
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::getMemoryUsed() const
{
    return sizeof(hashtable<Key, Value, Hash, Equal, KeyExtract>)
            + _nodes.size() * sizeof(Node);
}

}

