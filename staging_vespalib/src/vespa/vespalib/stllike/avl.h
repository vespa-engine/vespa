// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <iterator>
#include <bits/stl_algo.h>
#include <bits/stl_function.h>

#include "hash_fun.h"

namespace vespalib {

/**
   Yet another avl implementation. This one is justified by
   different memory management.

   The interface is tried to keep similar to stl version. However the
   are some major differences.  In order to avoid an allocation /
   deallocation for every object inserted / erased, it stores all
   objects in a std::vector. This should significantly speed up
   things. However it does remove properties that the stl versions
   have. The most obvious is that insert might invalidate iterators.
   That is due to possible resizing of memory store. That can be
   avoided by using a deque. However a deque is more complex and will
   be slower. Since speed is here preferred over functionality that is
   not yet done.
   The same trick could be done for tree based map/set.
   An entry can be valid or invalid(not set, or erased).
   The entry contains the element + a next index.

   After selecting the proper prime number for modulo operation, the
   vector reserves requested space. Resize (triggered by full vector)
   doubles size. Then the first 'prime' entries are initialized to
   invalid. Then the objects are filled in.  If the selected bucket is
   invalid the element is inserted directly. If not it is
   'push_back'ed, on the vector and linked in after the head element
   for that bucket. Erased elements are marked invalid, but not
   reused. They are reclaimed on resize.

   Advantage:
   - Significantly faster insert on average. No alloc/dealloc per element.
   Disadvantage:
   - insert spikes due to possible resize.
   - not fully stl-compliant.
   Conclusion:
   - Probably very good for typical use. Fx duplicate removal.
   Advice:
   - If you know how many elements you are going to put in, construct
   the hash with 2 times the amount. Since a hash will never be
   fully filled.
   ( hash_set<T>(2*num_expected_elements) ).
**/

class avl_base
{
public:
    typedef unsigned int next_t;
};

template< typename Key, typename Value, typename Compare, typename KeyExtract >
class avl : public avl_base
{
protected:
    class Node {
    public:
        enum {npos=-1u, invalid=-2u};
        Node() : _parent(npos), _left(npos), _right(npos) { }
        Node(const Value & node, next_t parent=npos, next_t left=npos, next_t right=npos) : _parent(parent), _left(left), _right(right), _node(node) { }
        Value & getValue()             { return _node; }
        const Value & getValue() const { return _node; }
        next_t getParent()       const { return _parent; }
        void setParent(next_t v)       { _parent = v; }
        next_t getLeft()         const { return _left; }
        void setLeft(next_t v)         { _left = v; }
        next_t getRight()        const { return _right; }
        void setRight(next_t v)        { _right = v; }
    private:
        next_t  _parent;
        next_t  _left;
        next_t  _right;
        Value   _node;
    };
    typedef std::vector<Node> NodeStore;
    virtual void move(const NodeStore & oldStore);
public:
    class iterator {
    public:
        iterator(avl * avl, next_t start) : _node(start), _avl(avl) { }
        Value & operator * ()  const { return _avl->get(_node); }
        Value * operator -> () const { return & _avl->get(_node); }
        iterator & operator ++ () {
            _node = _avl->getNextRight(_node);
            return *this;
        }
        iterator operator ++ (int) {
            iterator prev = *this;
            ++(*this);
            return prev;
        }
        bool operator==(const iterator& rhs) const { return (_node == rhs._node); }
        bool operator!=(const iterator& rhs) const { return (_node != rhs._node); }
    private:
        next_t   _node;
        avl    * _avl;

        friend class avl::const_iterator;
    };
    class const_iterator {
    public:
        const_iterator(const avl * avl, next_t start) : _node(start), _avl(avl) { }
        const_iterator(const iterator &i) : _node(i._node), _avl(i._avl) {}
        Value & operator * ()  const { return _avl->get(_node); }
        Value * operator -> () const { return & _avl->get(_node); }
        iterator & operator ++ () {
            _node = _avl->getNextRight(_node);
            return *this;
        }
        iterator operator ++ (int) {
            iterator prev = *this;
            ++(*this);
            return prev;
        }
        bool operator==(const iterator& rhs) const { return (_node == rhs._node); }
        bool operator!=(const iterator& rhs) const { return (_node != rhs._node); }
    private:
        next_t   _node;
        avl    * _avl;

        friend class avl::const_iterator;
    };

public:
    avl(size_t reservedSpace);
    iterator begin()             { return iterator(this, getLeftMost(_root)); }
    iterator end()               { return iterator(this, Node::npos); }
    const_iterator begin() const { return const_iterator(this, getLeftMost(_root)); }
    const_iterator end()   const { return const_iterator(this, Node::npos); }
    size_t capacity()      const { return _nodes.capacity(); }
    size_t size()          const { return _nodes.size(); }
    bool empty()           const { return _root == npos; }
    template< typename AltKey, typename AltExtract, typename AltCompare >
    iterator find(const AltKey & key);
    iterator find(const Key & key);
    iterator find(const Key & key) { return iterator(this, internalFind(key)); }
    template< typename AltKey, typename AltExtract, typename AltCompare >
    const_iterator find(const AltKey & key) const;
    const_iterator find(const Key & key) const { return const_iterator(this, internalFind(key)); }
    void insert(const Value & node);
    void erase(const Key & key);
    void clear() { _nodes.clear(); }
    void reserve(size_t newReserve) { _nodes.reserve(newReserve); }
    void swap(avl & rhs);
    /**
     * Get an approximate number of memory consumed by hash set. Not including
     * any data K would store outside of sizeof(K) of course.
     */
    size_t getMemoryConsumption() const;

protected:
    /// These two methods are only for the ones that know what they are doing.
    /// valid input here are stuff returned from iterator.getInternalIndex.
    next_t insertInternal(const Value & node);
    Value & getByInternalIndex(size_t index)             { return _nodes[index].getValue(); }
    const Value & getByInternalIndex(size_t index) const { return _nodes[index].getValue(); }
    template <typename MoveHandler>
    void erase(MoveHandler & moveHandler, const Key & key);
private:
    next_t      _begin;
    next_t      _root;
    NodeStore   _nodes;
    Compare     _compare;
    KeyExtract  _keyExtractor;
    next_t internalFind(const Key & key) const;
    Value & get(size_t index)             { return _nodes[index].getValue(); }
    const Value & get(size_t index) const { return _nodes[index].getValue(); }
    next_t getRightMost(next_t n) {
        while(_nodes[n].hasRight()) {
            n = _nodes[n].getRight());
        }
        return n;
    }
    next_t getLeftMost(next_t n) {
        while(_nodes[n].hasLeft()) {
            n = _nodes[n].getleft());
        }
        return n;
    }
    next_t getNextRight(next_t n) const {
        if (_nodes[n].hasParent()) {
            next_t parent = _nodes[_node].getParent();
            if (_nodes[parent].getLeft() == _node) {
                return parent;
            } else {
                return getNextRight(parent);
            }
        } else if (_nodes[n].hasRight()) {
            return getLeftMost(_nodes[n].getRight());
        } else {
            return npos;
        }
    }
    next_t getNextLeft(next_t n) const {
        if (_nodes[n].hasParent()) {
            next_t parent = _nodes[_node].getParent();
            if (_nodes[parent].getRight() == _node) {
                return parent;
            } else {
                return getNextLeft(parent);
            }
        } else if (_nodes[n].hasLeft()) {
            return getRightMost(_nodes[n].getLeft());
        } else {
            return npos;
        }
    }
};

template< typename Key, typename Value, typename Compare, typename KeyExtract >
void avl<Key, Value, Compare, KeyExtract>::swap(avl & rhs)
{
    std::swap(_root, rhs._root);
    _nodes.swap(rhs._nodes);
    std::swap(_compare, rhs._compare);
    std::swap(_keyExtractor, rhs._keyExtractor);
}

template< typename Key, typename Value, typename Compare, typename KeyExtract >
avl<Key, Value, Compare, KeyExtract>::avl(size_t reservedSpace) :
    _root(npos),
    _nodes()
{
    if (reservedSpace > 0) {
        reserve(reservedSpace);
    }
}

template< typename Key, typename Value, typename Compare, typename KeyExtract >
typename avl<Key, Value, Compare, KeyExtract>::iterator
avl<Key, Value, Compare, KeyExtract>::internalFind(const Key & key)
{
    next_t found = npos; // Last node which is not less than key.

    for (next_t n(_begin); n != npos; ) {
        if (!_compare(_keyExtractor(_nodes[n]), key)) {
            found = n;
            n = getNextLeft(n);
        } else {
            n = getNextRight(n);
        }
    }
    return ((found != npos) && ! _compare(key, _keyExtractor(_nodes[found])))
           ? found
           : npos;
}

template< typename Key, typename Value, typename Compare, typename KeyExtract >
typename avl<Key, Value, Compare, KeyExtract>::next_t
avl<Key, Value, Compare, KeyExtract>::insert(const Value & node)
{
    next_t n = _begin;
    next_t e = npos;
    Key key(_keyExtractor(node);
    while (n != npos) {
        e = n;
        n = _compare(_keyExtractor(_nodes[n]), key)
            ? getNextLeft(n)
            : getNextRight(n);
    }
    return insert(n, e, node);
}

template< typename Key, typename Value, typename Compare, typename KeyExtract >
typename avl<Key, Value, Compare, KeyExtract>::next_t
avl<Key, Value, Compare, KeyExtract>::insert(next_t n, next_t e, const Value & value)
{
    bool insert_left = (n != npos) ||
                       (e == npos) ||
                       _compare(_keyExtractor(value), _keyExtractor(_nodes[e]));

    next_t newN = _nodes.size();
    Node node(value);
    _nodes.push_back(node);

    insert_and_rebalance(insert_left, newN, e, this->_M_impl._M_header);
    return iterator(newN);
}

template< typename Key, typename Value, typename Compare, typename KeyExtract >
void
avl<Key, Value, Compare, KeyExtract>::erase(const Key & key)
{
    next_t found = internalFind(key);
    if (found != npos) {
        // Link out
        erase_and_rebalance(found);
        // Swap with last
        std::swap(_nodes[found], _nodes.back());
        nodes.resize(nodes.size() - 1);
        // relink parent to last
        if (_nodes[found].hasParent()) {
            next_t parent = _nodes[found].getParent();
            if (_nodes[parent].getLeft() == old) {
                _nodes[parent].setLeft(found);
            } else {
                _nodes[parent].setRight(found);
            }
        }
    }
}

template< typename Key, typename Value, typename Compare, typename KeyExtract >
size_t
avl<Key, Value, Compare, KeyExtract>::getMemoryConsumption() const
{
    return sizeof(*this) + _nodes.capacity() * sizeof(Node);
}

#if 0
template< typename Key, typename Value, typename Compare, typename KeyExtract >
template< typename AltKey, typename AltExtract, typename AltCompare>
typename avl<Key, Value, Compare, KeyExtract>::const_iterator
avl<Key, Value, Compare, KeyExtract>::find(const AltKey & key) const
{
    if (_modulo > 0) {
        AltHash altHasher;
        next_t h = altHasher(key) % _modulo;
        if (_nodes[h].valid()) {
            next_t start(h);
            AltExtract altExtract;
            AltCompare altCompare;
            do {
                if (altCompare(altExtract(_keyExtractor(_nodes[h].getValue())), key)) {
                    return const_iterator(this, start, h);
                }
                h = _nodes[h].getNext();
            } while (h != Node::npos);
        }
    }
    return end();
}

template< typename Key, typename Value, typename Compare, typename KeyExtract >
template< typename AltKey, typename AltExtract, typename AltCompare>
typename avl<Key, Value, Hash, Compare, KeyExtract>::iterator
avl<Key, Value, Compare, KeyExtract>::find(const AltKey & key)
{
    if (_modulo > 0) {
        AltHash altHasher;
        next_t h = altHasher(key) % _modulo;
        if (_nodes[h].valid()) {
            next_t start(h);
            AltExtract altExtract;
            AltCompare altCompare;
            do {
                if (altCompare(altExtract(_keyExtractor(_nodes[h].getValue())), key)) {
                    return iterator(this, start, h);
                }
                h = _nodes[h].getNext();
            } while (h != Node::npos);
        }
    }
    return end();
}
#endif

}

