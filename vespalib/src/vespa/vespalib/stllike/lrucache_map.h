// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/hashtable.h>
#include <vespa/vespalib/stllike/hash_fun.h>
#include <vespa/vespalib/stllike/select.h>
#include <atomic>
#include <limits>
#include <vector>

namespace vespalib {

struct LinkedValueBase {
    static const uint32_t npos = static_cast<uint32_t>(-1);
    constexpr LinkedValueBase() noexcept : _prev(npos), _next(npos) { }
    constexpr LinkedValueBase(uint32_t prev, uint32_t next) noexcept : _prev(prev), _next(next) { }
    uint32_t _prev;
    uint32_t _next;
};

template<typename V>
struct LinkedValue : public LinkedValueBase
{
    constexpr LinkedValue() noexcept {}
    constexpr LinkedValue(const V & v) noexcept : LinkedValueBase(), _value(v) { }
    constexpr LinkedValue(V && v) noexcept : LinkedValueBase(), _value(std::move(v)) { }
    V _value;
};

template<typename K, typename V, typename H = vespalib::hash<K>, typename EQ = std::equal_to<K> >
struct LruParam
{
    using LV = LinkedValue<V>;
    using value_type = std::pair< K, LV >;
    using select_key = vespalib::Select1st< value_type >;
    using Key = K;
    using Value = V;
    using Hash = H;
    using Equal = EQ;
    using HashTable = hashtable< Key, value_type, Hash, Equal, select_key >;
};

template< typename P >
class lrucache_map : private P::HashTable
{
    using HashTable = typename P::HashTable;
    using V = typename P::Value;
    using K = typename P::Key;
    using LV = typename P::LV;
    using internal_iterator = typename HashTable::iterator;
    using next_t = typename HashTable::next_t;
    using NodeStore = typename HashTable::NodeStore;
protected:
    static constexpr size_t UNLIMITED = std::numeric_limits<size_t>::max();
public:
    using insert_result = typename HashTable::insert_result;
    using value_type = typename P::value_type;

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

        [[nodiscard]] const K& key() const noexcept {
            return _cache->getByInternalIndex(_current).first;
        }
    private:
        lrucache_map * _cache;
        uint32_t _current;

        friend class lrucache_map;
    };

    /**
     * Will create a lrucache with max elements. Use the chained setter
     * @ref reserve to control initial size.
     *
     * @param maxElems in cache unless you override @ref removeOldest.
     */
    lrucache_map(size_t maxElems);
    virtual ~lrucache_map();

    lrucache_map & maxElements(size_t elems) {
        _maxElements.store(elems, std::memory_order_relaxed);
        return *this;
    }
    lrucache_map & reserve(size_t elems) {
        HashTable::reserve(elems);
        return *this;
    }


    [[nodiscard]] size_t capacity() const noexcept { return _maxElements.load(std::memory_order_relaxed); }
    [[nodiscard]] size_t size()     const noexcept { return HashTable::size(); }
    [[nodiscard]] bool empty()      const noexcept { return HashTable::empty(); }
    [[nodiscard]] iterator begin()        noexcept { return iterator(this, _head); }
    [[nodiscard]] iterator end()          noexcept { return iterator(this, LinkedValueBase::npos); }

    /**
     * This fetches the object without modifying the lru list.
     */
    const V & get(const K & key) const { return HashTable::find(key)->second._value; }

    /**
     * This simply erases the object.
     */
    void erase(const K & key);

    /**
     * Erase object pointed to by iterator.
     */
    iterator erase(const iterator & it);

    /**
     * Trims the cache size so that it is within its capacity limits. Since
     * the cache itself will normally do this during inserts, this can be
     * used to explicitly trim the cache when higher-level capacities (used
     * via removeOldest()) change, which are not picked up directly by the
     * cache itself.
     *
     * Note that this does not use soft limits; if the cache has only a single
     * element, and it is over-sized, trimming will remove the entry.
     */
    void trim();

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
     * Return pointer to the object with the given key.
     * Object is then put at head of LRU list if found.
     * If not found nullptr is returned.
     */
    [[nodiscard]] V* find_and_ref(const K& key);

    /**
     * Return pointer to the object with the given key. Object is then put at
     * head of LRU list if found and the size of the cache is more than half
     * its capacity. Otherwise, the LRU is not updated.
     *
     * If not found nullptr is returned.
     */
    [[nodiscard]] V* find_and_lazy_ref(const K& key);

    /**
     * Returns an iterator to the element with the given key iff it exists, without
     * updating the LRU. Otherwise returns end().
     */
    [[nodiscard]] iterator find_no_ref(const K& key);

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
    [[nodiscard]] bool hasKey(const K & key) const __attribute__((noinline));

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
     * Method for testing that internal consistency is good.
     */
    void verifyInternals();

    /**
     * Implements the move callback from the hashtable
     */
    void move(next_t from, next_t to);

    void swap(lrucache_map & rhs);

private:
    using MoveRecord = std::pair<uint32_t, uint32_t>;
    using MoveRecords = std::vector<MoveRecord>;
    /**
     * Implements the resize of the hashtable
     */
    void move(NodeStore && oldStore) override;
    void ref(const internal_iterator & it);
    insert_result insert(value_type && value);
    void removeOld();
    /**
     * Trims the cache by removing elements in an old-to-new order, stopping as
     * soon as either removeOldest() returns false, or:
     *   - if PreserveHead == true, the current element is the LRU head element
     *   - if PreserveHead == false, if the LRU list is empty
     */
    template <bool PreserveHead> void trim_impl();

    class RecordMoves {
    public:
        RecordMoves(const RecordMoves &) = delete;
        RecordMoves & operator = (const RecordMoves &) = delete;
        RecordMoves(lrucache_map & lru) noexcept
            : _lru(lru)
        {
            _lru._moveRecordingEnabled = true;
        }
        ~RecordMoves();
        uint32_t movedTo(uint32_t from);
    private:
        lrucache_map & _lru;
    };

    std::atomic<size_t> _maxElements;
    mutable uint32_t  _head;
    mutable uint32_t  _tail;
    bool              _moveRecordingEnabled;
    MoveRecords       _moved;
};

}
