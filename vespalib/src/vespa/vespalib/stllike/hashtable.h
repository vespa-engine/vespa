// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <iterator>
#include <vespa/vespalib/util/array.h>
#include <algorithm>

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
    using next_t = uint32_t;
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
    static size_t getModuloStl(size_t size);
    static size_t getModuloSimple(size_t size) {
        return std::max(size_t(8), roundUp2inN(size));
    }
protected:
    struct DefaultMoveHandler
    {
        void move(next_t from, next_t to) {
            (void) from;
            (void) to;
        }
    };
private:
    static size_t getModulo(size_t newSize, const unsigned long * list, size_t sz);
};

template<typename V>
class hash_node {
public:
    using next_t=hashtable_base::next_t;
    enum {npos=-1u, invalid=-2u};
    hash_node() : _node(), _next(invalid) {}
    hash_node(const V & node, next_t next=npos)
        : _node(node), _next(next) {}
    hash_node(V &&node, next_t next=npos)
        : _node(std::move(node)), _next(next) {}
    hash_node(hash_node &&) noexcept = default;
    hash_node &operator=(hash_node &&) noexcept = default;
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
    V       _node;
    next_t  _next;
};

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator = hashtable_base::prime_modulator>
class hashtable : public hashtable_base
{
private:
    using Node=hash_node<Value>;
protected:
    using NodeStore = vespalib::Array<Node>;
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

        iterator(hashtable * hash) : _current(0), _hashTable(hash) {
            if (! _hashTable->_nodes[_current].valid()) {
                advanceToNextValidHash();
            }
        }
        iterator(hashtable * hash, next_t pos) : _current(pos), _hashTable(hash) { }
        static iterator end(hashtable *hash) { return iterator(hash, hash->initializedSize()); }

        Value & operator * ()  const { return _hashTable->get(_current); }
        Value * operator -> () const { return & _hashTable->get(_current); }
        iterator & operator ++ () {
            advanceToNextValidHash();
            return *this;
        }
        iterator operator ++ (int) {
            iterator prev = *this;
            ++(*this);
            return prev;
        }
        bool operator==(const iterator& rhs) const { return (_current == rhs._current); }
        bool operator!=(const iterator& rhs) const { return (_current != rhs._current); }
        /// Carefull about this one. Only used by lrucache.
        next_t getInternalIndex() const  { return _current; }
        void setInternalIndex(next_t n)  { _current = n; }
    private:
        void advanceToNextValidHash() {
            ++_current;
            while ((_current < _hashTable->initializedSize()) && ! _hashTable->_nodes[_current].valid()) {
                ++_current;
            }
        }
        next_t      _current;
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

        const_iterator(const hashtable * hash) : _current(0), _hashTable(hash) {
            if (! _hashTable->_nodes[_current].valid()) {
                advanceToNextValidHash();
            }
        }
        const_iterator(const hashtable * hash, next_t pos) : _current(pos), _hashTable(hash) { }
        const_iterator(const iterator &i) :  _current(i._current), _hashTable(i._hashTable) {}
        static const_iterator end(const hashtable *hash) { return const_iterator(hash, hash->initializedSize()); }

        const Value & operator * ()  const { return _hashTable->get(_current); }
        const Value * operator -> () const { return & _hashTable->get(_current); }
        const_iterator & operator ++ () {
            advanceToNextValidHash();
            return *this;
        }
        const_iterator operator ++ (int) {
            const_iterator prev = *this;
            ++(*this);
            return prev;
        }
        bool operator==(const const_iterator& rhs) const { return (_current == rhs._current); }
        bool operator!=(const const_iterator& rhs) const { return (_current != rhs._current); }
        next_t getInternalIndex() const  { return _current; }
    private:
        void advanceToNextValidHash() {
            ++_current;
            while ((_current < _hashTable->initializedSize()) && ! _hashTable->_nodes[_current].valid()) {
                ++_current;
            }
        }
        next_t            _current;
        const hashtable * _hashTable;
    };
    typedef std::pair<iterator, bool> insert_result;

public:
    hashtable(hashtable &&) noexcept = default;
    hashtable & operator = (hashtable &&) noexcept = default;
    hashtable(const hashtable &);
    hashtable & operator = (const hashtable &);
    hashtable(size_t reservedSpace);
    hashtable(size_t reservedSpace, const Hash & hasher, const Equal & equal);
    virtual ~hashtable();
    iterator begin()             { return iterator(this); }
    iterator end()               { return iterator::end(this); }
    const_iterator begin() const { return const_iterator(this); }
    const_iterator end()   const { return const_iterator::end(this); }
    size_t capacity()      const { return _nodes.capacity(); }
    size_t size()          const { return _count; }
    bool empty()           const { return _count == 0; }
    template< typename AltKey>
    iterator find(const AltKey & key);
    iterator find(const Key & key);

    template< typename AltKey>
    const_iterator find(const AltKey & key) const;
    const_iterator find(const Key & key) const;
    template <typename V>
    insert_result insert(V && node) {
        return insertInternal(std::forward<V>(node));
    }
    // This will insert unconditionally, without checking presence, and might cause duplicates.
    // Use at you own risk.
    void force_insert(Value && value);
    
    /// This gives faster iteration than can be achieved by the iterators.
    template <typename Func>
    void for_each(Func func) const;

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
    template <typename V>
    insert_result insertInternal(V && node);
    /// These two methods are only for the ones that know what they are doing.
    /// valid input here are stuff returned from iterator.getInternalIndex.
    Value & getByInternalIndex(size_t index)             { return _nodes[index].getValue(); }
    const Value & getByInternalIndex(size_t index) const { return _nodes[index].getValue(); }
    template <typename MoveHandler>
    void erase(MoveHandler & moveHandler, next_t h, const const_iterator & key);
    template<typename K>
    next_t hash(const K & key) const { return modulator(_hasher(key)); }
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
    size_t initializedSize() const { return _nodes.size(); }
    template <typename MoveHandler>
    void move(MoveHandler & moveHandler, next_t from, next_t to) {
        _nodes[to] = std::move(_nodes[from]);
        moveHandler.move(from, to);
    }
    template <typename MoveHandler>
    void reclaim(MoveHandler & moveHandler, next_t node);
};

}
