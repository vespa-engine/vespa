// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <iterator>
#include <vespa/vespalib/util/array.h>

namespace vespalib {

/**
   Yet another hashtable implementation. This one is justified by
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

class hashtable_base
{
public:
    typedef unsigned int next_t;
    /**
     * This is a standard modulator that does modulo/hashTableSize.
     * Hashtable size is selected from a a set of prime numbers.
     **/
    class prime_modulator
    {
    public:
        prime_modulator(next_t sizeOfHashTable) : _modulo(sizeOfHashTable) { }
        next_t modulo(next_t hash) const { return hash % _modulo; }
        next_t getTableSize() const { return _modulo; }
        static next_t selectHashTableSize(size_t sz) { return hashtable_base::getModuloStl(sz); }
    private:
        next_t _modulo;
    };
    /**
     * This is a simple and fast modulator that uses simple and by hashTableSize-1.
     * Hashtable size is selected by selecting the next 2^N that fits the requested size.
     **/
    class and_modulator
    {
    public:
        and_modulator(next_t sizeOfHashTable) : _mask(sizeOfHashTable-1) { }
        next_t modulo(next_t hash) const { return hash & _mask; }
        next_t getTableSize() const { return _mask + 1; }
        static next_t selectHashTableSize(size_t sz) { return hashtable_base::getModuloSimple(sz); }
    private:
        next_t _mask;
    };
protected:
    struct DefaultMoveHandler
    {
        void move(next_t from, next_t to) {
            (void) from;
            (void) to;
        }
    };
private:
    static size_t getModuloStl(size_t newSize);
    static size_t getModuloSimple(size_t newSize);
    static size_t getModulo(size_t newSize, const unsigned long * list, size_t sz);
};

template<typename V>
class hash_node {
public:
    using next_t=hashtable_base::next_t;
    enum {npos=-1u, invalid=-2u};
    hash_node() : _next(invalid) {}
    hash_node(const V & node, next_t next=npos)
        : _next(next), _node(node) {}
    hash_node(V &&node, next_t next=npos)
        : _next(next), _node(std::move(node)) {}
    hash_node(hash_node &&) = default;
    hash_node &operator=(hash_node &&) = default;
    hash_node(const hash_node &) = default;             // These will not be created
    hash_node &operator=(const hash_node &) = default;  // if V is non-copyable.
    bool operator == (const hash_node & rhs) const {
        return (_next == rhs._next) && (_node == rhs._node);
    }
    V & getValue()             { return _node; }
    const V & getValue() const { return _node; }
    next_t getNext()     const { return _next; }
    void setNext(next_t next)  { _next = next; }
    void invalidate()          { _next = invalid; _node = V(); }
    void terminate()           { _next = npos; }
    bool valid()         const { return _next != invalid; }
    bool hasNext()       const { return valid() && (_next != npos); }
private:
    next_t  _next;
    V       _node;
};

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator = hashtable_base::prime_modulator>
class hashtable : public hashtable_base
{
private:
    using Node=hash_node<Value>;
protected:
    typedef vespalib::Array<Node> NodeStore;
    virtual void move(NodeStore && oldStore);
public:
    class const_iterator;
    class iterator {
    public:
        typedef std::ptrdiff_t difference_type;
        typedef Value value_type;
        typedef Value& reference;
        typedef Value* pointer;
        typedef std::forward_iterator_tag iterator_category;

        iterator(hashtable * hash, next_t start) : _hash(start), _subNode(start), _hashTable(hash) {
            advanceToNextValidHash();
        }
        iterator(hashtable * hash, next_t start, next_t subNode) : _hash(start), _subNode(subNode), _hashTable(hash) { }
        Value & operator * ()  const { return _hashTable->get(_subNode); }
        Value * operator -> () const { return & _hashTable->get(_subNode); }
        iterator & operator ++ () {
            if (_hashTable->_nodes[_subNode].hasNext()) {
                _subNode = _hashTable->_nodes[_subNode].getNext();
            } else {
                _hash++;
                advanceToNextValidHash();
            }
            return *this;
        }
        iterator operator ++ (int) {
            iterator prev = *this;
            ++(*this);
            return prev;
        }
        bool operator==(const iterator& rhs) const { return (_subNode == rhs._subNode); }
        bool operator!=(const iterator& rhs) const { return (_subNode != rhs._subNode); }
        /// Carefull about this one. Only used by lrucache.
        next_t getInternalIndex() const  { return _subNode; }
        void setInternalIndex(next_t n)  { _subNode = n; }
        next_t getHash() const { return _hash; }
    private:
        void advanceToNextValidHash() {
            for (;(_hash < _hashTable->getTableSize()) && ! _hashTable->_nodes[_hash].valid(); _hash++) { }
            _subNode = (_hash < _hashTable->getTableSize()) ? _hash : Node::npos;
        }
        next_t      _hash;
        next_t      _subNode;
        hashtable * _hashTable;

        friend class hashtable::const_iterator;
    };
    class const_iterator {
    public:
        typedef std::ptrdiff_t difference_type;
        typedef const Value value_type;
        typedef const Value& reference;
        typedef const Value* pointer;
        typedef std::forward_iterator_tag iterator_category;

        const_iterator(const hashtable * hash, next_t start) : _hash(start), _subNode(start), _hashTable(hash) {
            advanceToNextValidHash();
        }
        const_iterator(const hashtable * hash, next_t start, next_t subNode) : _hash(start), _subNode(subNode), _hashTable(hash) { }
        const_iterator(const iterator &i)
            : _hash(i._hash), _subNode(i._subNode), _hashTable(i._hashTable) {}
        const Value & operator * ()  const { return _hashTable->get(_subNode); }
        const Value * operator -> () const { return & _hashTable->get(_subNode); }
        const_iterator & operator ++ () {
            if (_hashTable->_nodes[_subNode].hasNext()) {
                _subNode = _hashTable->_nodes[_subNode].getNext();
            } else {
                _hash++;
                advanceToNextValidHash();
            }
            return *this;
        }
        const_iterator operator ++ (int) {
            const_iterator prev = *this;
            ++(*this);
            return prev;
        }
        bool operator==(const const_iterator& rhs) const { return (_subNode == rhs._subNode); }
        bool operator!=(const const_iterator& rhs) const { return (_subNode != rhs._subNode); }
        next_t getInternalIndex() const  { return _subNode; }
        next_t getHash() const { return _hash; }
    private:
        void advanceToNextValidHash() {
            for (;(_hash < _hashTable->getTableSize()) && ! _hashTable->_nodes[_hash].valid(); _hash++) { }
            _subNode = (_hash < _hashTable->getTableSize()) ? _hash : Node::npos;
        }
        next_t            _hash;
        next_t            _subNode;
        const hashtable * _hashTable;
    };
    typedef std::pair<iterator, bool> insert_result;

public:
    hashtable(hashtable &&) = default;
    hashtable & operator = (hashtable &&) = default;
    hashtable(const hashtable &);
    hashtable & operator = (const hashtable &);
    hashtable(size_t reservedSpace);
    hashtable(size_t reservedSpace, const Hash & hasher, const Equal & equal);
    virtual ~hashtable();
    iterator begin()             { return iterator(this, 0); }
    iterator end()               { return iterator(this, Node::npos); }
    const_iterator begin() const { return const_iterator(this, 0); }
    const_iterator end()   const { return const_iterator(this, Node::npos); }
    size_t capacity()      const { return _nodes.capacity(); }
    size_t size()          const { return _count; }
    bool empty()           const { return _count == 0; }
    template< typename AltKey, typename AltExtract, typename AltHash, typename AltEqual >
    iterator find(const AltKey & key, const AltExtract & altExtract);
    template< typename AltKey, typename AltExtract, typename AltHash, typename AltEqual >
    iterator find(const AltKey & key) { return find<AltKey, AltExtract, AltHash, AltEqual>(key, AltExtract()); }
    iterator find(const Key & key);
    template< typename AltKey, typename AltExtract, typename AltHash, typename AltEqual >
    const_iterator find(const AltKey & key, const AltExtract & altExtract) const;
    template< typename AltKey, typename AltExtract, typename AltHash, typename AltEqual >
    const_iterator find(const AltKey & key) const { return find<AltKey, AltExtract, AltHash, AltEqual>(key, AltExtract()); }
    const_iterator find(const Key & key) const;
    template <typename V>
    insert_result insert(V && node);
    void erase(const Key & key);
    void reserve(size_t sz) {
        if (sz > _nodes.capacity()) {
            resize(sz);
        }
    }
    void clear();
    void resize(size_t newSize);
    void swap(hashtable & rhs);

    /**
     * Get an approximate number of the memory allocated (in bytes) by this hash table.
     * Not including any data K would store outside of sizeof(K) of course.
     */
    size_t getMemoryConsumption() const;

    /**
     * Get an approximate number of memory used (in bytes) by this hash table.
     * Note that getMemoryConsumption() >= getMemoryUsed().
     */
    size_t getMemoryUsed() const;

protected:
    /// These two methods are only for the ones that know what they are doing.
    /// valid input here are stuff returned from iterator.getInternalIndex.
    template <typename V>
    insert_result insertInternal(V && node);
    Value & getByInternalIndex(size_t index)             { return _nodes[index].getValue(); }
    const Value & getByInternalIndex(size_t index) const { return _nodes[index].getValue(); }
    template <typename MoveHandler>
    void erase(MoveHandler & moveHandler, const const_iterator & key);
private:
    Modulator   _modulator;
    size_t      _count;
    NodeStore   _nodes;
    Hash        _hasher;
    Equal       _equal;
    KeyExtract  _keyExtractor;
    Value & get(size_t index)             { return _nodes[index].getValue(); }
    const Value & get(size_t index) const { return _nodes[index].getValue(); }
    next_t modulator(next_t key) const { return _modulator.modulo(key); }
    next_t getTableSize() const { return _modulator.getTableSize(); }
    next_t hash(const Key & key) const { return modulator(_hasher(key)); }
    template <typename MoveHandler>
    void move(MoveHandler & moveHandler, next_t from, next_t to) {
        _nodes[to] = std::move(_nodes[from]);
        moveHandler.move(from, to);
    }
    template <typename MoveHandler>
    void reclaim(MoveHandler & moveHandler, next_t node);
};

}
