// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/btree/btree.h>
#include <vespa/vespalib/btree/btreebuilder.h>
#include <vespa/vespalib/btree/btreenodeallocator.h>
#include <vespa/vespalib/btree/btreeroot.h>
#include <vespa/vespalib/btree/btreestore.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/rand48.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreenode.hpp>
#include <vespa/vespalib/btree/btreenodestore.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreebuilder.hpp>
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/btree/btreestore.hpp>
#include <vespa/vespalib/btree/btreeaggregator.hpp>
#include <vespa/vespalib/datastore/atomic_entry_ref.h>
#include <vespa/vespalib/datastore/buffer_type.hpp>
#include <vespa/vespalib/datastore/compaction_spec.h>
#include <vespa/vespalib/datastore/compaction_strategy.h>
#include <vespa/vespalib/datastore/entry_ref_filter.h>

#include <vespa/log/log.h>
LOG_SETUP("btree_stress_test");

using GenerationHandler = vespalib::GenerationHandler;
using RefType = vespalib::datastore::EntryRefT<22>;
using vespalib::btree::NoAggregated;
using vespalib::datastore::AtomicEntryRef;
using vespalib::datastore::CompactionSpec;
using vespalib::datastore::CompactionStrategy;
using vespalib::datastore::EntryRef;
using vespalib::datastore::EntryRefFilter;
using vespalib::makeLambdaTask;
using generation_t = GenerationHandler::generation_t;

namespace {

constexpr uint32_t value_offset = 1000000000;

bool smoke_test = false;
const vespalib::string smoke_test_option = "--smoke-test";

class RealIntStore {
    using StoreType = vespalib::datastore::DataStore<uint32_t>;
    using StoreRefType = StoreType::RefType;
    StoreType _store;
public:
    RealIntStore();
    ~RealIntStore();
    EntryRef add(uint32_t value) { return _store.addEntry(value); }
    AtomicEntryRef add_relaxed(uint32_t value) { return AtomicEntryRef(add(value)); }
    void hold(const AtomicEntryRef& ref) { _store.holdElem(ref.load_relaxed(), 1); }
    EntryRef move(EntryRef ref);
    void assign_generation(generation_t current_gen) { _store.assign_generation(current_gen); }
    void reclaim_memory(generation_t gen) { _store.reclaim_memory(gen); }
    uint32_t get(EntryRef ref) const { return _store.getEntry(ref); }
    uint32_t get_acquire(const AtomicEntryRef& ref) const { return get(ref.load_acquire()); }
    uint32_t get_relaxed(const AtomicEntryRef& ref) const { return get(ref.load_relaxed()); }
    std::unique_ptr<vespalib::datastore::CompactingBuffers> start_compact();
    static constexpr bool is_indirect = true;
    bool has_held_buffers() const noexcept { return _store.has_held_buffers(); }
};

RealIntStore::RealIntStore()
    : _store()
{
}

RealIntStore::~RealIntStore() = default;

std::unique_ptr<vespalib::datastore::CompactingBuffers>
RealIntStore::start_compact()
{
    // Use a compaction strategy that will compact all active buffers
    auto compaction_strategy = CompactionStrategy::make_compact_all_active_buffers_strategy();
    CompactionSpec compaction_spec(true, false);
    return _store.start_compact_worst_buffers(compaction_spec, compaction_strategy);
}

EntryRef
RealIntStore::move(EntryRef ref)
{
    return add(get(ref));
}

class RealIntStoreCompare
{
    const RealIntStore& _store;
    uint32_t _lookup_key;
public:
    RealIntStoreCompare(const RealIntStore& store, uint32_t lookup_key)
        : _store(store),
          _lookup_key(lookup_key)
    {
    }
    uint32_t get(EntryRef ref) const {
        return (ref.valid() ? _store.get(ref) : _lookup_key);
    }
    bool operator()(const AtomicEntryRef& lhs, const AtomicEntryRef& rhs) const {
        return get(lhs.load_acquire()) < get(rhs.load_acquire());
    }
    static AtomicEntryRef lookup_key() noexcept { return AtomicEntryRef(); }
    const RealIntStoreCompare& get_compare() const noexcept { return *this; }
};

class NoIntStore {
public:
    NoIntStore() = default;
    ~NoIntStore() = default;
    static uint32_t add(uint32_t value) noexcept { return value; }
    static uint32_t add_relaxed(uint32_t value) noexcept { return value; }
    static void hold(uint32_t) noexcept { }
    static void assign_generation(generation_t) noexcept { }
    static void reclaim_memory(generation_t) noexcept { }
    static uint32_t get(uint32_t value) noexcept { return value; }
    static uint32_t get_acquire(uint32_t value) noexcept { return value; }
    static uint32_t get_relaxed(uint32_t value) noexcept { return value; }
    static constexpr bool is_indirect = false;
};

class NoIntStoreCompare
{
    uint32_t _lookup_key;
public:
    NoIntStoreCompare(const NoIntStore&, uint32_t lookup_key)
        : _lookup_key(lookup_key)
    {
    }
    bool operator()(uint32_t lhs, uint32_t rhs) const noexcept {
        return lhs < rhs;
    }
    uint32_t lookup_key() const noexcept { return _lookup_key; }
    static std::less<uint32_t> get_compare() noexcept { return {}; }
};

}

struct IndirectKeyValueParams {
    using IntStore = RealIntStore;
    using MyCompare = RealIntStoreCompare;
    using MyTree = vespalib::btree::BTree<AtomicEntryRef, AtomicEntryRef, NoAggregated, RealIntStoreCompare>;
};

struct DirectKeyValueParams {
    using IntStore = NoIntStore;
    using MyCompare = NoIntStoreCompare;
    using MyTree = vespalib::btree::BTree<uint32_t, uint32_t>;
};

template <uint32_t divisor, uint32_t remainder>
class ConsiderCompact {
    uint32_t _count;
    bool _want_compact;
public:
    ConsiderCompact()
        : _count(0u),
          _want_compact(false)
    {
    }
    bool consider(uint32_t idx) {
        if ((idx % divisor) == remainder) {
            _want_compact = true;
        }
        return _want_compact;
    }
    void track_compacted() {
        ++_count;
        _want_compact = false;
    }
    uint32_t get_count() const noexcept { return _count; }
};

template <typename Params>
class Fixture : public testing::Test
{
protected:
    using IntStore = typename Params::IntStore;
    using MyCompare = typename Params::MyCompare;
    using MyTree = typename Params::MyTree;
    using MyTreeIterator = typename MyTree::Iterator;
    using MyTreeConstIterator = typename MyTree::ConstIterator;
    using KeyStore = IntStore;
    using ValueStore = IntStore;
    GenerationHandler _generationHandler;
    KeyStore _keys;
    ValueStore _values;
    MyTree _tree;
    MyTreeIterator _writeItr;
    vespalib::ThreadStackExecutor _writer; // 1 write thread
    vespalib::ThreadStackExecutor _readers; // multiple reader threads
    vespalib::Rand48 _rnd;
    uint32_t _keyLimit;
    std::atomic<long> _readSeed;
    std::atomic<long> _doneWriteWork;
    std::atomic<long> _doneReadWork;
    std::atomic<bool> _stopRead;
    bool _reportWork;
    ConsiderCompact<1000, 0> _compact_tree;
    ConsiderCompact<1000, 300> _compact_keys;
    ConsiderCompact<1000, 600> _compact_values;

    Fixture();
    ~Fixture() override;
    void commit();
    bool adjustWriteIterator(uint32_t key);
    void insert(uint32_t key);
    void remove(uint32_t key);
    void compact_tree();
    void compact_keys();
    void compact_values();
    void consider_compact(uint32_t idx);

    void readWork(uint32_t cnt);
    void readWork();
    void writeWork(uint32_t cnt);

    void basic_lower_bound();
    void single_lower_bound_reader_without_updates();
    void single_lower_bound_reader_during_updates();
    void multiple_lower_bound_readers_during_updates();
};


template <typename Params>
Fixture<Params>::Fixture()
    : testing::Test(),
      _generationHandler(),
      _tree(),
      _writeItr(_tree.begin()),
      _writer(1, 128_Ki),
      _readers(4, 128_Ki),
      _rnd(),
      _keyLimit(1000000),
      _readSeed(50),
      _doneWriteWork(0),
      _doneReadWork(0),
      _stopRead(false),
      _reportWork(false),
      _compact_tree(),
      _compact_keys(),
      _compact_values()
{
    _rnd.srand48(32);
}

template <typename Params>
Fixture<Params>::~Fixture()
{
    _readers.sync();
    _readers.shutdown();
    _writer.sync();
    _writer.shutdown();
    commit();
    if (_reportWork) {
        LOG(info,
            "readWork=%ld, writeWork=%ld",
            _doneReadWork.load(), _doneWriteWork.load());
    }
}


template <typename Params>
void
Fixture<Params>::commit()
{
    auto &allocator = _tree.getAllocator();
    allocator.freeze();
    auto current_gen = _generationHandler.getCurrentGeneration();
    allocator.assign_generation(current_gen);
    _keys.assign_generation(current_gen);
    _values.assign_generation(current_gen);
    allocator.assign_generation(_generationHandler.getCurrentGeneration());
    _generationHandler.incGeneration();
    auto oldest_used_gen = _generationHandler.get_oldest_used_generation();
    allocator.reclaim_memory(oldest_used_gen);
    _keys.reclaim_memory(oldest_used_gen);
    _values.reclaim_memory(oldest_used_gen);
}

template <typename Params>
bool
Fixture<Params>::adjustWriteIterator(uint32_t key)
{
    MyCompare compare(_keys, key);
    if (_writeItr.valid() && _keys.get_relaxed(_writeItr.getKey()) < key) {
        _writeItr.binarySeek(compare.lookup_key(), compare.get_compare());
    } else {
        _writeItr.lower_bound(compare.lookup_key(), compare.get_compare());
    }
    assert(!_writeItr.valid() || _keys.get_relaxed(_writeItr.getKey()) >= key);
    return (_writeItr.valid() && _keys.get_relaxed(_writeItr.getKey()) == key);
}

template <typename Params>
void
Fixture<Params>::insert(uint32_t key)
{
    if (!adjustWriteIterator(key)) {
        _tree.insert(_writeItr, _keys.add_relaxed(key), _values.add_relaxed(key + value_offset));
    } else {
        EXPECT_EQ(key + value_offset, _values.get_relaxed(_writeItr.getData()));
    }
}

template <typename Params>
void
Fixture<Params>::remove(uint32_t key)
{
    if (adjustWriteIterator(key)) {
        EXPECT_EQ(key + value_offset, _values.get_relaxed(_writeItr.getData()));
        _keys.hold(_writeItr.getKey());
        _values.hold(_writeItr.getData());
        _tree.remove(_writeItr);
    }
}

template <typename Params>
void
Fixture<Params>::compact_tree()
{
    // Use a compaction strategy that will compact all active buffers
    auto compaction_strategy = CompactionStrategy::make_compact_all_active_buffers_strategy();
    _tree.compact_worst(compaction_strategy);
    _writeItr = _tree.begin();
    _compact_tree.track_compacted();
}

template <typename Params>
void
Fixture<Params>::compact_keys()
{
    if constexpr (KeyStore::is_indirect) {
        auto compacting_buffers = _keys.start_compact();
        auto filter = compacting_buffers->make_entry_ref_filter();
        auto itr = _tree.begin();
        while (itr.valid()) {
            auto old_ref = itr.getKey().load_relaxed();
            if (filter.has(old_ref)) {
                auto new_ref = _keys.move(old_ref);
                itr.writeKey(AtomicEntryRef(new_ref));
            }
            ++itr;
        }
        compacting_buffers->finish();
    }
    _compact_keys.track_compacted();
}

template <typename Params>
void
Fixture<Params>::compact_values()
{
    if constexpr (ValueStore::is_indirect) {
        auto compacting_buffers = _values.start_compact();
        auto filter = compacting_buffers->make_entry_ref_filter();
        auto itr = _tree.begin();
        while (itr.valid()) {
            auto old_ref = itr.getData().load_relaxed();
            if (filter.has(old_ref)) {
                auto new_ref = _values.move(old_ref);
                itr.getWData().store_release(new_ref);
            }
            ++itr;
        }
        compacting_buffers->finish();
    }
    _compact_values.track_compacted();
}

template <typename Params>
void
Fixture<Params>::consider_compact(uint32_t idx)
{
    if (_compact_tree.consider(idx) && !_tree.getAllocator().getNodeStore().has_held_buffers()) {
        compact_tree();
    }
    if constexpr (KeyStore::is_indirect) {
        if (_compact_keys.consider(idx) && !_keys.has_held_buffers()) {
            compact_keys();
        }
    }
    if constexpr (ValueStore::is_indirect) {
        if (_compact_values.consider(idx) && !_values.has_held_buffers()) {
            compact_values();
        }
    }
}

template <typename Params>
void
Fixture<Params>::readWork(uint32_t cnt)
{
    vespalib::Rand48 rnd;
    rnd.srand48(++_readSeed);
    uint32_t i;
    uint32_t hits = 0u;
    for (i = 0; i < cnt && !_stopRead.load(); ++i) {
        auto guard = _generationHandler.takeGuard();
        uint32_t key = rnd.lrand48() % (_keyLimit + 1);
        MyCompare compare(_keys, key);
        MyTreeConstIterator itr = _tree.getFrozenView().lowerBound(compare.lookup_key(), compare.get_compare());
        assert(!itr.valid() || _keys.get_acquire(itr.getKey()) >= key);
        if (itr.valid() && _keys.get_acquire(itr.getKey()) == key) {
            EXPECT_EQ(key + value_offset, _values.get_acquire(itr.getData()));
            ++hits;
        }
    }
    _doneReadWork += i;
    LOG(info, "done %u read work, %u hits", i, hits);
}

template <typename Params>
void
Fixture<Params>::readWork()
{
    readWork(std::numeric_limits<uint32_t>::max());
}

template <typename Params>
void
Fixture<Params>::writeWork(uint32_t cnt)
{
    vespalib::Rand48 &rnd(_rnd);
    for (uint32_t i = 0; i < cnt; ++i) {
        consider_compact(i);
        uint32_t key = rnd.lrand48() % _keyLimit;
        if ((rnd.lrand48() & 1) == 0) {
            insert(key);
        } else {
            remove(key);
        }
        commit();
    }
    _doneWriteWork += cnt;
    _stopRead = true;
    LOG(info, "done %u write work, %u compact tree, %u compact keys, %u compact values", cnt,
        _compact_tree.get_count(),
        _compact_keys.get_count(),
        _compact_values.get_count());
}

template <typename Params>
void
Fixture<Params>::basic_lower_bound()
{
    insert(1);
    remove(2);
    insert(1);
    insert(5);
    insert(4);
    remove(3);
    remove(5);
    commit();
    MyCompare compare(_keys, 3);
    auto itr = _tree.getFrozenView().lowerBound(compare.lookup_key(), compare.get_compare());
    ASSERT_TRUE(itr.valid());
    EXPECT_EQ(4u, _keys.get_acquire(itr.getKey()));
}

template <typename Params>
void
Fixture<Params>::single_lower_bound_reader_without_updates()
{
    _reportWork = true;
    writeWork(10);
    _stopRead = false;
    readWork(10);
}

template <typename Params>
void
Fixture<Params>::single_lower_bound_reader_during_updates()
{
    uint32_t cnt = smoke_test ? 10000 : 1000000;
    _reportWork = true;
    _writer.execute(makeLambdaTask([this, cnt]() { writeWork(cnt); }));
    _readers.execute(makeLambdaTask([this]() { readWork(); }));
    _writer.sync();
    _readers.sync();
}

template <typename Params>
void
Fixture<Params>::multiple_lower_bound_readers_during_updates()
{
    uint32_t cnt = smoke_test ? 10000 : 1000000;
    _reportWork = true;
    _writer.execute(makeLambdaTask([this, cnt]() { writeWork(cnt); }));
    _readers.execute(makeLambdaTask([this]() { readWork(); }));
    _readers.execute(makeLambdaTask([this]() { readWork(); }));
    _readers.execute(makeLambdaTask([this]() { readWork(); }));
    _readers.execute(makeLambdaTask([this]() { readWork(); }));
    _writer.sync();
    _readers.sync();
}

template <typename Params>
using BTreeStressTest = Fixture<Params>;

using TestTypes = testing::Types<DirectKeyValueParams, IndirectKeyValueParams>;

VESPA_GTEST_TYPED_TEST_SUITE(BTreeStressTest, TestTypes);

// Disable warnings emitted by gtest generated files when using typed tests
#pragma GCC diagnostic push
#ifndef __clang__
#pragma GCC diagnostic ignored "-Wsuggest-override"
#endif

TYPED_TEST(BTreeStressTest, basic_lower_bound)
{
    this->basic_lower_bound();
}

TYPED_TEST(BTreeStressTest, single_lower_bound_reader_without_updates)
{
    this->single_lower_bound_reader_without_updates();
}

TYPED_TEST(BTreeStressTest, single_lower_bound_reader_during_updates)
{
    this->single_lower_bound_reader_during_updates();
}

TYPED_TEST(BTreeStressTest, multiple_lower_bound_readers_during_updates)
{
    this->multiple_lower_bound_readers_during_updates();
}

#pragma GCC diagnostic pop

int main(int argc, char **argv) {
    if (argc > 1 && argv[1] == smoke_test_option) {
        smoke_test = true;
        ++argv;
        --argc;
    }
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
