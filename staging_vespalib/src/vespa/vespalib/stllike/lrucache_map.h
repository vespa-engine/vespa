// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/hashtable.h>

namespace vespalib {

struct LinkedValueBase {
    static const uint32_t npos = static_cast<uint32_t>(-1);
    LinkedValueBase() : _prev(npos), _next(npos) { }
    LinkedValueBase(uint32_t prev, uint32_t next) : _prev(prev), _next(next) { }
    uint32_t _prev;
    uint32_t _next;
};

template<typename V>
struct LinkedValue : public LinkedValueBase
{
    LinkedValue() {}
    LinkedValue(const V & v) : LinkedValueBase(), _value(v) { }
    V _value;
};

template<typename K, typename V, typename H = vespalib::hash<K>, typename EQ = std::equal_to<K> >
struct LruParam
{
    typedef LinkedValue<V> LV;
    typedef std::pair< K, LV > value_type;
    typedef std::_Select1st< value_type > select_key;
    typedef K Key;
    typedef V Value;
    typedef H Hash;
    typedef EQ Equal;
    typedef hashtable< Key, value_type, Hash, Equal, select_key > HashTable;
};

template< typename P >
class lrucache_map : private P::HashTable
{
private:
    typedef typename P::HashTable   HashTable;
    typedef typename P::Value       V;
    typedef typename P::Key         K;
    typedef typename P::value_type  value_type;
    typedef typename P::LV  LV;
    typedef typename HashTable::iterator internal_iterator;
    typedef typename HashTable::next_t next_t;
    typedef typename HashTable::NodeStore NodeStore;
protected:
    static constexpr size_t UNLIMITED = std::numeric_limits<size_t>::max();
public:
    typedef typename HashTable::insert_result insert_result;

    class iterator {
    public:
        iterator(lrucache_map * cache, uint32_t current) : _cache(cache), _current(current) { }
        V & operator * ()  const { return _cache->getByInternalIndex(_current).second._value; }
        V * operator -> () const { return & _cache->getByInternalIndex(_current).second._value; }
        iterator & operator ++ () {
            if (_current != LinkedValueBase::npos) {
                _current = _cache->getByInternalIndex(_current).second._next;
            }
            return *this;
        }
        iterator operator ++ (int) {
            iterator prev = *this;
            ++(*this);
            return prev;
        }
        bool operator==(const iterator& rhs) const { return (_current == rhs._current); }
        bool operator!=(const iterator& rhs) const { return (_current != rhs._current); }
    private:
        lrucache_map * _cache;
        uint32_t _current;

        friend class lrucache_map;
    };

    /**
     * Will create a lrucache with max elements. Use the chained setter
     * @ref reserve to control initial size.
     *
     * @param maxElements in cache unless you override @ref removeOldest.
     */
    lrucache_map(size_t maxElems);

    lrucache_map & maxElements(size_t elems) {
        _maxElements = elems;
        return *this;
    }
    lrucache_map & reserve(size_t elems) {
        HashTable::reserve(elems);
        return *this;
    }


    size_t capacity()                  const { return _maxElements; }
    size_t size()                      const { return HashTable::size(); }
    bool empty()                       const { return HashTable::empty(); }
    iterator begin()                         { return iterator(this, _head); }
    iterator end()                           { return iterator(this, LinkedValueBase::npos); }

    /**
     * This fetches the object without modifying the lru list.
     */
    const V & get(const K & key) { return HashTable::find(key)->second._value; }

    /**
     * This simply erases the object.
     */
    void erase(const K & key);

    /**
     * Erase object pointed to by iterator.
     */
    iterator erase(const iterator & it);

    /**
     * Object is inserted in cache with given key.
     * Object is then put at head of LRU list.
     */
    insert_result insert(const K & key, const V & value) {
        return insert(value_type(key, LV(value)));
    }

    /**
     * Return the object with the given key. If it does not exist an empty one will be created.
     * This can be used as an insert.
     * Object is then put at head of LRU list.
     */
    const V & operator [] (const K & key) const {
        return const_cast<lrucache_map<P> *>(this)->findAndRef(key).second._value;
    }

    /**
     * Return the object with the given key. If it does not exist an empty one will be created.
     * This can be used as an insert.
     * Object is then put at head of LRU list.
     */
    V & operator [] (const K & key);

    /**
     * Tell if an object with given key exists in the cache.
     * Does not alter the LRU list.
     */
    bool hasKey(const K & key) const { return HashTable::find(key) != HashTable::end(); }

    /**
     * Called when an object is inserted, to see if the LRU should be removed.
     * Default is to obey the maxsize given in constructor.
     * The obvious extension is when you are storing pointers and want to cap
     * on the real size of the object pointed to.
     */
    virtual bool removeOldest(const value_type & v) {
        (void) v;
        return (size() > capacity());
    }

    /**
     * Method for testing that internal consitency is good.
     */
    bool verifyInternals();

    /**
     * Implements the move callback from the hashtable
     */
    void move(next_t from, next_t to);

    void swap(lrucache_map & rhs);

private:
    typedef std::pair<uint32_t, uint32_t> MoveRecord;
    typedef std::vector<MoveRecord> MoveRecords;
    /**
     * Implements the resize of the hashtable
     */
    void move(NodeStore && oldStore) override;
    internal_iterator findAndRef(const K & key);
    void ref(const internal_iterator & it);
    insert_result insert(value_type && value);
    void removeOld();
    class RecordMoves : public noncopyable {
    public:
        RecordMoves(lrucache_map & lru) :
            _lru(lru)
        {
            _lru._moveRecordingEnabled = true;
        }
        uint32_t movedTo(uint32_t from) {
            for (size_t i(0); i < _lru._moved.size(); i++) {
                const MoveRecord & mr(_lru._moved[i]);
                if (mr.first == from) {
                    from = mr.second;
                }
            }
            return from;
        }
        ~RecordMoves() {
            _lru._moveRecordingEnabled = false;
            _lru._moved.clear();
        }
    private:
        lrucache_map & _lru;
    };

    size_t            _maxElements;
    mutable uint32_t  _head;
    mutable uint32_t  _tail;
    bool              _moveRecordingEnabled;
    MoveRecords       _moved;
};

template< typename P >
lrucache_map<P>::lrucache_map(size_t maxElems) :
    HashTable(0),
    _maxElements(maxElems),
    _head(LinkedValueBase::npos),
    _tail(LinkedValueBase::npos),
    _moveRecordingEnabled(false),
    _moved()
{
}

template< typename P >
void
lrucache_map<P>::swap(lrucache_map & rhs) {
    std::swap(_maxElements, rhs._maxElements);
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
        HashTable::erase(*this, it);
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
            HashTable::erase(*this, HashTable::find(last->first));
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
typename lrucache_map<P>::internal_iterator
lrucache_map<P>::findAndRef(const K & key)
{
    internal_iterator found = HashTable::find(key);
    if (found != HashTable::end()) {
        ref(found);
    }
    return found;
}

}

