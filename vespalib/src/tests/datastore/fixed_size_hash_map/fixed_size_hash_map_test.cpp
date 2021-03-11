// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/datastore/fixed_size_hash_map.h>
#include <vespa/vespalib/datastore/unique_store_allocator.h>
#include <vespa/vespalib/datastore/unique_store_comparator.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/rand48.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/vespalib/datastore/unique_store_allocator.hpp>
#include <vespa/vespalib/stllike/hash_map.hpp>

#include <vespa/log/log.h>
LOG_SETUP("vespalib_fixed_size_hash_map_test");

using vespalib::datastore::EntryRef;
using RefT = vespalib::datastore::EntryRefT<22>;
using MyAllocator = vespalib::datastore::UniqueStoreAllocator<uint32_t, RefT>;
using MyDataStore = vespalib::datastore::DataStoreT<RefT>;
using MyCompare = vespalib::datastore::UniqueStoreComparator<uint32_t, RefT>;
using GenerationHandler = vespalib::GenerationHandler;
using vespalib::makeLambdaTask;
using vespalib::GenerationHolder;
using vespalib::datastore::FixedSizeHashMap;

class FixedSizeHashMapHeld : public vespalib::GenerationHeldBase
{
    std::unique_ptr<const FixedSizeHashMap> _data;
public:
    FixedSizeHashMapHeld(size_t size, std::unique_ptr<const FixedSizeHashMap> data);
    ~FixedSizeHashMapHeld();
};

FixedSizeHashMapHeld::FixedSizeHashMapHeld(size_t size, std::unique_ptr<const FixedSizeHashMap> data)
    : GenerationHeldBase(size),
      _data(std::move(data))
{
}

FixedSizeHashMapHeld::~FixedSizeHashMapHeld() = default;

struct DataStoreFixedSizeHashTest : public ::testing::Test
{
    GenerationHandler _generation_handler;
    GenerationHolder  _generation_holder;
    MyAllocator       _allocator;
    MyDataStore&      _store;
    std::unique_ptr<const vespalib::datastore::EntryComparator> _comp;
    std::atomic<FixedSizeHashMap *>  _hash_map;
    vespalib::ThreadStackExecutor _writer; // 1 write thread
    vespalib::ThreadStackExecutor _readers; // multiple reader threads
    vespalib::Rand48 _rnd;
    uint32_t _keyLimit;
    std::atomic<long> _read_seed;
    std::atomic<long> _done_write_work;
    std::atomic<long> _done_read_work;
    std::atomic<long> _found_count;
    std::atomic<int> _stop_read;
    size_t _modulo_limit;
    bool _report_work;

    DataStoreFixedSizeHashTest();
    ~DataStoreFixedSizeHashTest();
    void commit();
    void grow();
    size_t size() const noexcept;
    void insert(uint32_t key);
    void remove(uint32_t key);

    void read_work(uint32_t cnt);
    void read_work();
    void write_work(uint32_t cnt);
};


DataStoreFixedSizeHashTest::DataStoreFixedSizeHashTest()
    : _generation_handler(),
      _generation_holder(),
      _allocator(),
      _store(_allocator.get_data_store()),
      _comp(std::make_unique<MyCompare>(_store)),
      _hash_map(),
      _writer(1, 128_Ki),
      _readers(4, 128_Ki),
      _rnd(),
      _keyLimit(1000000),
      _read_seed(50),
      _done_write_work(0),
      _done_read_work(0),
      _found_count(0),
      _stop_read(0),
      _modulo_limit(std::numeric_limits<uint32_t>::max()),
      _report_work(false)
{
    _rnd.srand48(32);
    _hash_map = new FixedSizeHashMap(1, 2, 1);
}


DataStoreFixedSizeHashTest::~DataStoreFixedSizeHashTest()
{
    _readers.sync();
    _readers.shutdown();
    _writer.sync();
    _writer.shutdown();
    commit();
    auto hash_map = _hash_map.load(std::memory_order_relaxed);
    delete hash_map;
    if (_report_work) {
        LOG(info,
            "read_work=%ld, write_work=%ld, found_count=%ld",
            _done_read_work.load(), _done_write_work.load(), _found_count.load());
    }
}


void
DataStoreFixedSizeHashTest::commit()
{
    _store.transferHoldLists(_generation_handler.getCurrentGeneration());
    auto hash_map =_hash_map.load(std::memory_order_relaxed);
    hash_map->transfer_hold_lists(_generation_handler.getCurrentGeneration());
    _generation_holder.transferHoldLists(_generation_handler.getCurrentGeneration());
    _generation_handler.incGeneration();
    _store.trimHoldLists(_generation_handler.getFirstUsedGeneration());
    hash_map->trim_hold_lists(_generation_handler.getFirstUsedGeneration());
    _generation_holder.trimHoldLists(_generation_handler.getFirstUsedGeneration());
}

void
DataStoreFixedSizeHashTest::grow()
{
    auto hash_map = _hash_map.load(std::memory_order_relaxed);
    size_t size = hash_map->size();
    auto new_hash_map = std::make_unique<FixedSizeHashMap>(std::min(size * 2 + 2, _modulo_limit), size * 3 + 3, 1, *hash_map, *_comp);
    _hash_map.store(new_hash_map.release(), std::memory_order_release);
    auto hold = std::make_unique<FixedSizeHashMapHeld>(0, std::unique_ptr<const FixedSizeHashMap>(hash_map));
    _generation_holder.hold(std::move(hold));
}

size_t
DataStoreFixedSizeHashTest::size() const noexcept
{
    auto hash_map = _hash_map.load(std::memory_order_relaxed);
    return hash_map->size();
}

void
DataStoreFixedSizeHashTest::insert(uint32_t key)
{
    auto hash_map = _hash_map.load(std::memory_order_relaxed);
    if (hash_map->full()) {
        grow();
        hash_map = _hash_map.load(std::memory_order_relaxed);
    }
    MyCompare comp(_store, key);
    std::function<EntryRef(void)> insert_entry([this, key]() -> EntryRef { return _allocator.allocate(key); });
    auto& result = hash_map->add(comp, insert_entry);
    auto ref = result.first.load_relaxed();
    auto &wrapped_entry = _allocator.get_wrapped(ref);
    EXPECT_EQ(key, wrapped_entry.value());
}

void
DataStoreFixedSizeHashTest::remove(uint32_t key)
{
    MyCompare comp(_store, key);
    auto hash_map = _hash_map.load(std::memory_order_relaxed);
    auto result = hash_map->remove(comp, EntryRef());
    if (result != nullptr) {
        auto ref = result->first.load_relaxed();
        auto &wrapped_entry = _allocator.get_wrapped(ref);
        EXPECT_EQ(key, wrapped_entry.value());
        _allocator.hold(ref);
    }
}

void
DataStoreFixedSizeHashTest::read_work(uint32_t cnt)
{
    vespalib::Rand48 rnd;
    long found = 0;
    rnd.srand48(++_read_seed);
    uint32_t i;
    for (i = 0; i < cnt && _stop_read.load() == 0; ++i) {
        auto guard = _generation_handler.takeGuard();
        uint32_t key = rnd.lrand48() % (_keyLimit + 1);
        MyCompare comp(_store, key);
        auto hash_map = _hash_map.load(std::memory_order_acquire);
        auto result = hash_map->find(comp, EntryRef());
        if (result != nullptr) {
            auto ref = result->first.load_relaxed();
            auto &wrapped_entry = _allocator.get_wrapped(ref);
            EXPECT_EQ(key, wrapped_entry.value());
            ++found;
        }
    }
    _done_read_work += i;
    _found_count += found;
    LOG(info, "done %u read work", i);
}


void
DataStoreFixedSizeHashTest::read_work()
{
    read_work(std::numeric_limits<uint32_t>::max());
}


void
DataStoreFixedSizeHashTest::write_work(uint32_t cnt)
{
    vespalib::Rand48 &rnd(_rnd);
    for (uint32_t i = 0; i < cnt; ++i) {
        uint32_t key = rnd.lrand48() % _keyLimit;
        if ((rnd.lrand48() & 1) == 0) {
            insert(key);
        } else {
            remove(key);
        }
        commit();
    }
    _done_write_work += cnt;
    _stop_read = 1;
    LOG(info, "done %u write work", cnt);
}


TEST_F(DataStoreFixedSizeHashTest, smoke_test)
{
    EXPECT_EQ(0, size());
    insert(1);
    EXPECT_EQ(1, size());
    remove(2);
    EXPECT_EQ(1, size());
    insert(1);
    EXPECT_EQ(1, size());
    insert(5);
    EXPECT_EQ(2, size());
    insert(4);
    EXPECT_EQ(3, size());
    remove(3);
    EXPECT_EQ(3, size());
    remove(5);
    EXPECT_EQ(2, size());
    commit();
    MyCompare comp3(_store, 3);
    auto hash_map = _hash_map.load(std::memory_order_acquire);
    auto result3 = hash_map->find(comp3, EntryRef());
    EXPECT_TRUE(result3 == nullptr);
    MyCompare comp4(_store, 4);
    auto result4 = hash_map->find(comp4, EntryRef());
    EXPECT_TRUE(result4 != nullptr);
    auto ref4 = result4->first.load_relaxed();
    auto& wrapped_entry4 = _allocator.get_wrapped(ref4);
    EXPECT_EQ(4, wrapped_entry4.value());
}

TEST_F(DataStoreFixedSizeHashTest, lookups_works_after_insert_and_remove)
{
    _modulo_limit = 1; // Force single hash chain
    vespalib::hash_map<uint32_t, bool> expected;
    vespalib::Rand48 &rnd(_rnd);
    for (uint32_t i = 0; i < 40; ++i) {
        uint32_t key = rnd.lrand48() % 10;
        if ((rnd.lrand48() & 1) == 0) {
            insert(key);
            expected[key] = true;
        } else {
            remove(key);
            expected[key] = false;
        }
        commit();
    }
    auto hash_map = _hash_map.load(std::memory_order_acquire);
    for (auto &kv : expected) {
        MyCompare comp(_store, kv.first);
        EXPECT_EQ(kv.second, hash_map->find(comp, EntryRef()) != nullptr);
    }
}

TEST_F(DataStoreFixedSizeHashTest, single_threaded_reader_without_updates)
{
    _report_work = true;
    write_work(10);
    _stop_read = 0;
    read_work(10);
}

TEST_F(DataStoreFixedSizeHashTest, single_threaded_reader_during_updates)
{
    uint32_t cnt = 1000000;
    _report_work = true;
    _writer.execute(makeLambdaTask([this, cnt]() { write_work(cnt); }));
    _readers.execute(makeLambdaTask([this]() { read_work(); }));
}

TEST_F(DataStoreFixedSizeHashTest, multi_threaded_reader_during_updates)
{
    uint32_t cnt = 1000000;
    _report_work = true;
    _writer.execute(makeLambdaTask([this, cnt]() { write_work(cnt); }));
    for (size_t i = 0; i < 4; ++i) {
        _readers.execute(makeLambdaTask([this]() { read_work(); }));
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
