// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/hashtable.h>
#include <vespa/vespalib/stllike/hash_fun.h>
#include <vespa/vespalib/stllike/select.h>
#include <vector>

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
    LinkedValue(V && v) : LinkedValueBase(), _value(std::move(v)) { }
    V _value;
};

template<typename K, typename V, typename H = vespalib::hash<K>, typename EQ = std::equal_to<K> >
struct LruParam
{
    typedef LinkedValue<V> LV;
    typedef std::pair< K, LV > value_type;
    typedef vespalib::Select1st< value_type > select_key;
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
    virtual ~lrucache_map();

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
    insert_result insert(const K & key, const V & value);

    /**
     * Object is inserted in cache with given key.
     * Object is then put at head of LRU list.
     */
    insert_result insert(const K & key, V && value);

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
    virtual bool removeOldest(const value_type & v);
    virtual void onRemove(const K & key);
    virtual void onInsert(const K & key);

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
    class RecordMoves {
    public:
        RecordMoves(const RecordMoves &) = delete;
        RecordMoves & operator = (const RecordMoves &) = delete;
        RecordMoves(lrucache_map & lru) :
            _lru(lru)
        {
            _lru._moveRecordingEnabled = true;
        }
        ~RecordMoves();
        uint32_t movedTo(uint32_t from);
    private:
        lrucache_map & _lru;
    };

    size_t            _maxElements;
    mutable uint32_t  _head;
    mutable uint32_t  _tail;
    bool              _moveRecordingEnabled;
    MoveRecords       _moved;
};

}
