// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "allocator.h"
#include <vespa/vespalib/util/traits.h>
#include <vespa/vespalib/util/alloc.h>
#include <algorithm>
#include <iterator>
#include <vector>

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
        prime_modulator(next_t sizeOfHashTable) noexcept : _modulo(sizeOfHashTable) { }
        next_t modulo(next_t hash) const noexcept { return hash % _modulo; }
        next_t getTableSize() const noexcept { return _modulo; }
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
        and_modulator(next_t sizeOfHashTable) noexcept : _mask(sizeOfHashTable-1) { }
        next_t modulo(next_t hash) const noexcept { return hash & _mask; }
        next_t getTableSize() const noexcept { return _mask + 1; }
        static next_t selectHashTableSize(size_t sz) noexcept { return hashtable_base::getModuloSimple(sz); }
    private:
        next_t _mask;
    };
    static size_t getModuloStl(size_t size) noexcept;
    static size_t getModuloSimple(size_t size) noexcept {
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
    static size_t getModulo(size_t newSize, const unsigned long * list, size_t sz) noexcept;
};

template<typename V>
class hash_node {
public:
    using next_t=hashtable_base::next_t;
    enum {npos=-1u, invalid=-2u};
    constexpr hash_node() noexcept
        : _next(invalid)
    {}
    constexpr hash_node(const V & node, next_t next=npos) noexcept(std::is_nothrow_copy_constructible_v<V>)
        : _next(next)
    {
        new (_node) V(node);
    }
    constexpr hash_node(V &&node, next_t next=npos) noexcept
        : _next(next)
    {
        new (_node) V(std::move(node));
    }
    constexpr hash_node(hash_node && rhs) noexcept
        : _next(rhs._next)
    {
        if (rhs.valid()) {
            new (_node) V(std::move(rhs.getValue()));
        }
    }
    hash_node &operator=(hash_node && rhs) noexcept {
        destruct();
        if (rhs.valid()) {
            new (_node) V(std::move(rhs.getValue()));
            _next = rhs._next;
        } else {
            _next = invalid;
        }
        return *this;
    }
    constexpr hash_node(const hash_node & rhs) noexcept(std::is_nothrow_copy_constructible_v<V>)
        : _next(rhs._next)
    {
        if (rhs.valid()) {
            new (_node) V(rhs.getValue());
        }
    }
    hash_node &operator=(const hash_node & rhs) noexcept (std::is_nothrow_copy_constructible_v<V>) {
        destruct();
        if (rhs.valid()) {
            new (_node) V(rhs.getValue());
            _next = rhs._next;
        } else {
            _next = invalid;
        }
        return *this;
    }
    ~hash_node() noexcept {
        destruct();
    }
    constexpr bool operator == (const hash_node & rhs) const noexcept {
        return (_next == rhs._next) && (!valid() || (getValue() == rhs.getValue()));
    }
    V & getValue()             noexcept { return *reinterpret_cast<V *>(_node); }
    constexpr const V & getValue() const noexcept { return *reinterpret_cast<const V *>(_node); }
    constexpr next_t getNext()     const noexcept { return _next; }
    void setNext(next_t next)  noexcept { _next = next; }
    void invalidate()          noexcept {
        destruct();
        _next = invalid;
    }
    void terminate()           noexcept { _next = npos; }
    constexpr bool valid()         const noexcept { return _next != invalid; }
    constexpr bool hasNext()       const noexcept { return valid() && (_next != npos); }
private:
    void destruct() noexcept {
        if constexpr (!can_skip_destruction<V>) {
            if (valid()) {
                getValue().~V();
            }
        }
    }
    alignas(V) char    _node[sizeof(V)];
    next_t  _next;
};

template< typename Key, typename Value, typename Hash, typename Equal, typename KeyExtract, typename Modulator = hashtable_base::prime_modulator>
class hashtable : public hashtable_base
{
private:
    using Node=hash_node<Value>;
protected:
    using NodeStore = std::vector<Node, allocator_large<Node>>;
    virtual void move(NodeStore && oldStore);
public:
    class const_iterator;
    class iterator {
    public:
        using difference_type = std::ptrdiff_t;
        using value_type = Value;
        using reference = Value&;
        using pointer = Value*;
        using iterator_category = std::forward_iterator_tag;

        constexpr iterator(hashtable * hash) noexcept : _current(0), _hashTable(hash) {
            if (! _hashTable->_nodes[_current].valid()) {
                advanceToNextValidHash();
            }
        }
        constexpr iterator(hashtable * hash, next_t pos) noexcept : _current(pos), _hashTable(hash) { }
        constexpr static iterator end(hashtable *hash) noexcept { return iterator(hash, hash->initializedSize()); }

        constexpr Value & operator * ()  const noexcept { return _hashTable->get(_current); }
        constexpr Value * operator -> () const noexcept { return & _hashTable->get(_current); }
        iterator & operator ++ () noexcept {
            advanceToNextValidHash();
            return *this;
        }
        iterator operator ++ (int) noexcept {
            iterator prev = *this;
            ++(*this);
            return prev;
        }
        constexpr bool operator==(const iterator& rhs) const noexcept { return (_current == rhs._current); }
        constexpr bool operator!=(const iterator& rhs) const noexcept { return (_current != rhs._current); }
        /// Carefull about this one. Only used by lrucache.
        constexpr next_t getInternalIndex() const noexcept  { return _current; }
        void setInternalIndex(next_t n)  noexcept { _current = n; }
    private:
        void advanceToNextValidHash() noexcept {
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
        using difference_type = std::ptrdiff_t;
        using value_type = const Value;
        using reference = const Value&;
        using pointer = const Value*;
        using iterator_category = std::forward_iterator_tag;

        constexpr const_iterator(const hashtable * hash) noexcept : _current(0), _hashTable(hash) {
            if (! _hashTable->_nodes[_current].valid()) {
                advanceToNextValidHash();
            }
        }
        constexpr const_iterator(const hashtable * hash, next_t pos) noexcept : _current(pos), _hashTable(hash) { }
        constexpr const_iterator(const iterator &i) noexcept :  _current(i._current), _hashTable(i._hashTable) {}
        static constexpr const_iterator end(const hashtable *hash) noexcept { return const_iterator(hash, hash->initializedSize()); }

        constexpr const Value & operator * ()  const noexcept { return _hashTable->get(_current); }
        constexpr const Value * operator -> () const noexcept { return & _hashTable->get(_current); }
        const_iterator & operator ++ () noexcept {
            advanceToNextValidHash();
            return *this;
        }
        const_iterator operator ++ (int) noexcept {
            const_iterator prev = *this;
            ++(*this);
            return prev;
        }
        constexpr bool operator==(const const_iterator& rhs) const { return (_current == rhs._current); }
        constexpr bool operator!=(const const_iterator& rhs) const { return (_current != rhs._current); }
        constexpr next_t getInternalIndex() const  { return _current; }
    private:
        void advanceToNextValidHash() noexcept {
            ++_current;
            while ((_current < _hashTable->initializedSize()) && ! _hashTable->_nodes[_current].valid()) {
                ++_current;
            }
        }
        next_t            _current;
        const hashtable * _hashTable;
    };
    using insert_result = std::pair<iterator, bool>;

public:
    hashtable(hashtable &&) noexcept = default;
    hashtable & operator = (hashtable &&) noexcept = default;
    hashtable(const hashtable &);
    hashtable & operator = (const hashtable &);
    hashtable(size_t reservedSpace);
    hashtable(size_t reservedSpace, const Hash & hasher, const Equal & equal);
    virtual ~hashtable();
    constexpr iterator begin()             noexcept { return iterator(this); }
    constexpr iterator end()               noexcept { return iterator::end(this); }
    constexpr const_iterator begin() const noexcept { return const_iterator(this); }
    constexpr const_iterator end()   const noexcept { return const_iterator::end(this); }
    constexpr size_t capacity()      const noexcept { return _nodes.capacity(); }
    constexpr size_t size()          const noexcept { return _count; }
    constexpr bool empty()           const noexcept { return _count == 0; }
    template< typename AltKey>
    iterator find(const AltKey & key) noexcept;
    iterator find(const Key & key) noexcept;

    template< typename AltKey>
    constexpr const_iterator find(const AltKey & key) const noexcept {
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
    constexpr const_iterator find(const Key & key) const noexcept {
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
    template <typename V>
    insert_result insert(V && node) {
        return insert_internal(std::forward<V>(node));
    }
    // This will insert unconditionally, without checking presence, and might cause duplicates.
    // Use at you own risk.
    VESPA_DLL_LOCAL void force_insert(Value && value);

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
    void resize(size_t newSize) __attribute__((noinline));
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
    insert_result insert_internal(V && node);
    /// These two methods are only for the ones that know what they are doing.
    /// valid input here are stuff returned from iterator.getInternalIndex.
    Value & getByInternalIndex(size_t index)             noexcept { return _nodes[index].getValue(); }
    constexpr const Value & getByInternalIndex(size_t index) const noexcept { return _nodes[index].getValue(); }
    template <typename MoveHandler>
    void erase(MoveHandler & moveHandler, next_t h, const const_iterator & key);
    template<typename K>
    constexpr next_t hash(const K & key) const noexcept { return modulator(_hasher(key)); }
private:
    Modulator   _modulator;
    size_t      _count;
    NodeStore   _nodes;
    Hash        _hasher;
    Equal       _equal;
    KeyExtract  _keyExtractor;
    Value & get(size_t index)             noexcept { return _nodes[index].getValue(); }
    constexpr const Value & get(size_t index) const noexcept { return _nodes[index].getValue(); }
    constexpr next_t modulator(next_t key) const noexcept { return _modulator.modulo(key); }
    constexpr next_t getTableSize() const noexcept { return _modulator.getTableSize(); }
    constexpr size_t initializedSize() const noexcept { return _nodes.size(); }
    template <typename MoveHandler>
    void move(MoveHandler & moveHandler, next_t from, next_t to) {
        _nodes[to] = std::move(_nodes[from]);
        moveHandler.move(from, to);
    }
    template <typename MoveHandler>
    VESPA_DLL_LOCAL void reclaim(MoveHandler & moveHandler, next_t node);
    template <typename V>
    VESPA_DLL_LOCAL insert_result insert_internal_cold(V && node, next_t) __attribute__((noinline));
};

}
