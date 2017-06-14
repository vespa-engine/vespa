// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "hashtable.h"
#include <vespa/vespalib/util/array.hpp>

namespace vespalib {

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
    _modulator(1),
    _count(0),
    _nodes(1)
{
    if (reservedSpace > 0) {
        resize(reservedSpace);
    }
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::hashtable(size_t reservedSpace, const Hash & hasher, const Equal & equal) :
    _modulator(1),
    _count(0),
    _nodes(1),
    _hasher(hasher),
    _equal(equal)
{
    if (reservedSpace > 0) {
        resize(reservedSpace);
    }
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::hashtable(const hashtable & rhs)
    : _modulator(rhs._modulator),
      _count(rhs._count),
      _nodes(rhs._nodes),
      _hasher(rhs._hasher),
      _equal(rhs._equal)
{ }

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator> &
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::operator = (const hashtable & rhs) {
    hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>(rhs).swap(*this);
    return *this;
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::~hashtable()
{
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
typename hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::iterator
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::find(const Key & key)
{
    next_t h = hash(key);
    if (_nodes[h].valid()) {
        next_t start(h);
        do {
            if (_equal(_keyExtractor(_nodes[h].getValue()), key)) {
                return iterator(this, start, h);
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
    if (_nodes[h].valid()) {
        next_t start(h);
        do {
            if (_equal(_keyExtractor(_nodes[h].getValue()), key)) {
                return const_iterator(this, start, h);
            }
            h = _nodes[h].getNext();
        } while (h != Node::npos);
    }
    return end();
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
template< typename AltKey, typename AltExtract, typename AltHash, typename AltEqual>
typename hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::const_iterator
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::find(const AltKey & key, const AltExtract & altExtract) const
{
    AltHash altHasher;
    next_t h = modulator(altHasher(key));
    if (_nodes[h].valid()) {
        next_t start(h);
        AltEqual altEqual;
        do {
            if (altEqual(altExtract(_keyExtractor(_nodes[h].getValue())), key)) {
                return const_iterator(this, start, h);
            }
            h = _nodes[h].getNext();
        } while (h != Node::npos);
    }
    return end();
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
template< typename AltKey, typename AltExtract, typename AltHash, typename AltEqual>
typename hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::iterator
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::find(const AltKey & key, const AltExtract & altExtract)
{
    AltHash altHasher;
    next_t h = modulator(altHasher(key));
    if (_nodes[h].valid()) {
        next_t start(h);
        AltEqual altEqual;
        do {
            if (altEqual(altExtract(_keyExtractor(_nodes[h].getValue())), key)) {
                return iterator(this, start, h);
            }
            h = _nodes[h].getNext();
        } while (h != Node::npos);
    }
    return end();
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
template<typename V>
typename hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::insert_result
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::insert(V && node) {
    return insertInternal(std::forward<V>(node));
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
void
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::erase(const Key & key) {
    const_iterator found(find(key));
    if (found != end()) {
        DefaultMoveHandler moveHandler;
        erase(moveHandler, found);
    }
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
void
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::clear() {
    _nodes.clear();
    resize(getTableSize());
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
        return insert_result(iterator(this, h, h), true);
    } else if (_nodes.size() <= _nodes.capacity()) {
        for (next_t c(h); c != Node::npos; c = _nodes[c].getNext()) {
            if (_equal(_keyExtractor(_nodes[c].getValue()), _keyExtractor(node))) {
                return insert_result(iterator(this, h, c), false);
            }
        }
        if (_nodes.size() < _nodes.capacity()) {
            const next_t p(_nodes[h].getNext());
            const next_t newIdx(_nodes.size());
            _nodes[h].setNext(newIdx);
            new (_nodes.push_back_fast()) Node(std::forward<V>(node), p);
            _count++;
            return insert_result(iterator(this, h, newIdx), true);
        } else {
            resize(_nodes.capacity()*2);
            return insertInternal(std::forward<V>(node));
        }
    } else {
        resize(_nodes.capacity()*2);
        return insertInternal(std::forward<V>(node));
    }
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
template<typename MoveHandler>
void hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::reclaim(MoveHandler & moveHandler, next_t node)
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
template <typename MoveHandler>
void
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::erase(MoveHandler & moveHandler, const const_iterator & it)
{
    next_t h = it.getHash();
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
    newSize = roundUp2inN(newSize);
    next_t newModulo = Modulator::selectHashTableSize(newSize/3);
    if (newModulo > newSize) {
        newSize = newModulo;
    }
    NodeStore newStore;
    newStore.reserve(roundUp2inN(newSize));
    newStore.resize(newModulo);
    _modulator = Modulator(newModulo);
    _count = 0;
    _nodes.swap(newStore);
    move(std::move(newStore));
}

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator >
void
hashtable<Key, Value, Hash, Equal, KeyExtract, Modulator>::move(NodeStore && oldStore)
{
    for(typename NodeStore::iterator it(oldStore.begin()), mt(oldStore.end()); it != mt; it++) {
        if (it->valid()) {
            insert(std::move(it->getValue()));
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

