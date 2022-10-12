// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/datastore/sharded_hash_map.h>
#include <vespa/vespalib/datastore/entry_ref_filter.h>
#include <vespa/vespalib/datastore/i_compactable.h>
#include <vespa/vespalib/datastore/unique_store_allocator.h>
#include <vespa/vespalib/datastore/unique_store_comparator.h>

#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/rand48.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/vespalib/datastore/unique_store_allocator.hpp>
#include <iostream>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP("vespalib_datastore_shared_hash_test");

using vespalib::datastore::EntryRef;
using vespalib::datastore::EntryRefFilter;
using vespalib::datastore::ICompactable;
using RefT = vespalib::datastore::EntryRefT<22>;
using MyAllocator = vespalib::datastore::UniqueStoreAllocator<uint32_t, RefT>;
using MyDataStore = vespalib::datastore::DataStoreT<RefT>;
using MyCompare = vespalib::datastore::UniqueStoreComparator<uint32_t, RefT>;
using MyHashMap = vespalib::datastore::ShardedHashMap;
using GenerationHandler = vespalib::GenerationHandler;
using vespalib::makeLambdaTask;

constexpr uint32_t small_population = 50;
/*
 * large_population should trigger multiple callbacks from normalize_values
 * and foreach_value
 */
constexpr uint32_t large_population = 1200;

namespace vespalib::datastore {

/*
 * Print EntryRef as RefT which is used by test_normalize_values and
 * test_foreach_value to differentiate between buffers
 */
void PrintTo(const EntryRef &ref, std::ostream* os) {
    RefT iref(ref);
    *os << "RefT(" << iref.offset() << "," << iref.bufferId() << ")";
}

}

namespace {

void consider_yield(uint32_t i)
{
    if ((i % 1000) == 0) {
        // Need to yield sometimes to avoid livelock when running unit test with valgrind
        std::this_thread::yield();
    }
}

class MyCompactable : public ICompactable
{
    MyAllocator& _allocator;
    std::vector<EntryRef>& _new_refs;
public:
    MyCompactable(MyAllocator& allocator, std::vector<EntryRef>& new_refs)
        : ICompactable(),
          _allocator(allocator),
          _new_refs(new_refs)
    {
    }
    ~MyCompactable() override = default;

    EntryRef move_on_compact(EntryRef ref) override {
        auto new_ref = _allocator.move_on_compact(ref);
        _allocator.hold(ref);
        _new_refs.emplace_back(new_ref);
        return new_ref;
    }
};

uint32_t select_buffer(uint32_t i) {
    if ((i % 2) == 0) {
        return 0;
    }
    if ((i % 3) == 0) {
        return 1;
    }
    if ((i % 5) == 0) {
        return 2;
    }
    return 3;
}

}

struct DataStoreShardedHashTest : public ::testing::Test
{
    GenerationHandler _generationHandler;
    MyAllocator       _allocator;
    MyDataStore&      _store;
    MyHashMap         _hash_map;
    vespalib::ThreadStackExecutor _writer; // 1 write thread
    vespalib::ThreadStackExecutor _readers; // multiple reader threads
    vespalib::Rand48 _rnd;
    uint32_t _keyLimit;
    std::atomic<long> _read_seed;
    std::atomic<long> _done_write_work;
    std::atomic<long> _done_read_work;
    std::atomic<long> _found_count;
    std::atomic<int> _stop_read;
    bool _report_work;

    DataStoreShardedHashTest();
    ~DataStoreShardedHashTest();
    void commit();
    void insert(uint32_t key);
    void remove(uint32_t key);

    void read_work(uint32_t cnt);
    void read_work();
    void write_work(uint32_t cnt);
    void populate_sample_data(uint32_t cnt);
    void populate_sample_values(uint32_t cnt);
    void clear_sample_values(uint32_t cnt);
    void test_normalize_values(bool use_filter, bool one_filter);
    void test_foreach_value(bool one_filter);
};


DataStoreShardedHashTest::DataStoreShardedHashTest()
    : _generationHandler(),
      _allocator({}),
      _store(_allocator.get_data_store()),
      _hash_map(std::make_unique<MyCompare>(_store)),
      _writer(1, 128_Ki),
      _readers(4, 128_Ki),
      _rnd(),
      _keyLimit(1000000),
      _read_seed(50),
      _done_write_work(0),
      _done_read_work(0),
      _found_count(0),
      _stop_read(0),
      _report_work(false)
{
    _rnd.srand48(32);
}


DataStoreShardedHashTest::~DataStoreShardedHashTest()
{
    _readers.sync();
    _readers.shutdown();
    _writer.sync();
    _writer.shutdown();
    commit();
    if (_report_work) {
        LOG(info,
            "read_work=%ld, write_work=%ld, found_count=%ld",
            _done_read_work.load(), _done_write_work.load(), _found_count.load());
    }
}


void
DataStoreShardedHashTest::commit()
{
    _store.assign_generation(_generationHandler.getCurrentGeneration());
    _hash_map.assign_generation(_generationHandler.getCurrentGeneration());
    _generationHandler.incGeneration();
    _store.reclaim_memory(_generationHandler.get_oldest_used_generation());
    _hash_map.reclaim_memory(_generationHandler.get_oldest_used_generation());
}

void
DataStoreShardedHashTest::insert(uint32_t key)
{
    MyCompare comp(_store, key);
    std::function<EntryRef(void)> insert_entry([this, key]() -> EntryRef { return _allocator.allocate(key); });
    auto& result = _hash_map.add(comp, EntryRef(), insert_entry);
    auto ref = result.first.load_relaxed();
    auto &wrapped_entry = _allocator.get_wrapped(ref);
    EXPECT_EQ(key, wrapped_entry.value());
}

void
DataStoreShardedHashTest::remove(uint32_t key)
{
    MyCompare comp(_store, key);
    auto result = _hash_map.remove(comp, EntryRef());
    if (result != nullptr) {
        auto ref = result->first.load_relaxed();
        auto &wrapped_entry = _allocator.get_wrapped(ref);
        EXPECT_EQ(key, wrapped_entry.value());
        _allocator.hold(ref);
    }
}


void
DataStoreShardedHashTest::read_work(uint32_t cnt)
{
    vespalib::Rand48 rnd;
    long found = 0;
    rnd.srand48(++_read_seed);
    uint32_t i;
    for (i = 0; i < cnt && _stop_read.load() == 0; ++i) {
        auto guard = _generationHandler.takeGuard();
        uint32_t key = rnd.lrand48() % (_keyLimit + 1);
        MyCompare comp(_store, key);
        auto result = _hash_map.find(comp, EntryRef());
        if (result != nullptr) {
            auto ref = result->first.load_relaxed();
            auto &wrapped_entry = _allocator.get_wrapped(ref);
            EXPECT_EQ(key, wrapped_entry.value());
            ++found;
        }
        consider_yield(i);
    }
    _done_read_work += i;
    _found_count += found;
    LOG(info, "done %u read work", i);
}


void
DataStoreShardedHashTest::read_work()
{
    read_work(std::numeric_limits<uint32_t>::max());
}


void
DataStoreShardedHashTest::write_work(uint32_t cnt)
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
        consider_yield(i);
    }
    _done_write_work += cnt;
    _stop_read = 1;
    LOG(info, "done %u write work", cnt);
}

void
DataStoreShardedHashTest::populate_sample_data(uint32_t cnt)
{
    for (uint32_t i = 0; i < cnt; ++i) {
        insert(i);
    }
}

void
DataStoreShardedHashTest::populate_sample_values(uint32_t cnt)
{
    for (uint32_t i = 0; i < cnt; ++i) {
        MyCompare comp(_store, i);
        auto result = _hash_map.find(comp, EntryRef());
        ASSERT_NE(result, nullptr);
        EXPECT_EQ(i, _allocator.get_wrapped(result->first.load_relaxed()).value());
        result->second.store_relaxed(RefT(i + 200, select_buffer(i)));
    }
}

void
DataStoreShardedHashTest::clear_sample_values(uint32_t cnt)
{
    for (uint32_t i = 0; i < cnt; ++i) {
        MyCompare comp(_store, i);
        auto result = _hash_map.find(comp, EntryRef());
        ASSERT_NE(result, nullptr);
        EXPECT_EQ(i, _allocator.get_wrapped(result->first.load_relaxed()).value());
        result->second.store_relaxed(EntryRef());
    }
}

namespace {

template <typename RefT>
EntryRefFilter
make_entry_ref_filter(bool one_filter)
{
    if (one_filter) {
        EntryRefFilter filter(RefT::numBuffers(), RefT::offset_bits);
        filter.add_buffer(3);
        return filter;
    }
    return EntryRefFilter::create_all_filter(RefT::numBuffers(), RefT::offset_bits);
}

}

void
DataStoreShardedHashTest::test_normalize_values(bool use_filter, bool one_filter)
{
    populate_sample_data(large_population);
    populate_sample_values(large_population);
    if (use_filter) {
        auto filter = make_entry_ref_filter<RefT>(one_filter);
        EXPECT_TRUE(_hash_map.normalize_values([](std::vector<EntryRef> &refs) noexcept { for (auto &ref : refs) { RefT iref(ref); ref = RefT(iref.offset() + 300, iref.bufferId()); } }, filter));
    } else {
        EXPECT_TRUE(_hash_map.normalize_values([](EntryRef ref) noexcept { RefT iref(ref); return RefT(iref.offset() + 300, iref.bufferId()); }));
    }
    for (uint32_t i = 0; i < large_population; ++i) {
        MyCompare comp(_store, i);
        auto result = _hash_map.find(comp, EntryRef());
        ASSERT_NE(result, nullptr);
        EXPECT_EQ(i, _allocator.get_wrapped(result->first.load_relaxed()).value());
        ASSERT_EQ(select_buffer(i), RefT(result->second.load_relaxed()).bufferId());
        if (use_filter && one_filter && select_buffer(i) != 3) {
            ASSERT_EQ(i + 200, RefT(result->second.load_relaxed()).offset());
        } else {
            ASSERT_EQ(i + 500, RefT(result->second.load_relaxed()).offset());
        }
        result->second.store_relaxed(EntryRef());
    }
}

void
DataStoreShardedHashTest::test_foreach_value(bool one_filter)
{
    populate_sample_data(large_population);
    populate_sample_values(large_population);

    auto filter = make_entry_ref_filter<RefT>(one_filter);
    std::vector<EntryRef> exp_refs;
    EXPECT_FALSE(_hash_map.normalize_values([&exp_refs](std::vector<EntryRef>& refs) { exp_refs.insert(exp_refs.end(), refs.begin(), refs.end()); }, filter));
    std::vector<EntryRef> act_refs;
    _hash_map.foreach_value([&act_refs](const std::vector<EntryRef> &refs) { act_refs.insert(act_refs.end(), refs.begin(), refs.end()); }, filter);
    EXPECT_EQ(exp_refs, act_refs);
    clear_sample_values(large_population);
}

TEST_F(DataStoreShardedHashTest, single_threaded_reader_without_updates)
{
    _report_work = true;
    write_work(10);
    _stop_read = 0;
    read_work(10);
}

TEST_F(DataStoreShardedHashTest, single_threaded_reader_during_updates)
{
    uint32_t cnt = 1000000;
    _report_work = true;
    _writer.execute(makeLambdaTask([this, cnt]() { write_work(cnt); }));
    _readers.execute(makeLambdaTask([this]() { read_work(); }));
}

TEST_F(DataStoreShardedHashTest, multi_threaded_reader_during_updates)
{
    uint32_t cnt = 1000000;
    _report_work = true;
    _writer.execute(makeLambdaTask([this, cnt]() { write_work(cnt); }));
    for (size_t i = 0; i < 4; ++i) {
        _readers.execute(makeLambdaTask([this]() { read_work(); }));
    }
}

TEST_F(DataStoreShardedHashTest, memory_usage_is_reported)
{
    auto initial_usage = _hash_map.get_memory_usage();
    EXPECT_LT(0, initial_usage.allocatedBytes());
    EXPECT_LT(0, initial_usage.usedBytes());
    EXPECT_EQ(0, initial_usage.deadBytes());
    EXPECT_EQ(0, initial_usage.allocatedBytesOnHold());
    auto guard = _generationHandler.takeGuard();
    for (uint32_t i = 0; i < small_population; ++i) {
        insert(i);
    }
    auto usage = _hash_map.get_memory_usage();
    EXPECT_EQ(0, usage.deadBytes());
    EXPECT_LT(0, usage.allocatedBytesOnHold());
}

TEST_F(DataStoreShardedHashTest, foreach_key_works)
{
    populate_sample_data(small_population);
    std::vector<uint32_t> keys;
    _hash_map.foreach_key([this, &keys](EntryRef ref) { keys.emplace_back(_allocator.get_wrapped(ref).value()); });
    std::sort(keys.begin(), keys.end());
    EXPECT_EQ(small_population, keys.size());
    for (uint32_t i = 0; i < small_population; ++i) {
        EXPECT_EQ(i, keys[i]);
    }
}

TEST_F(DataStoreShardedHashTest, move_keys_on_compact_works)
{
    populate_sample_data(small_population);
    std::vector<EntryRef> refs;
    _hash_map.foreach_key([&refs](EntryRef ref) { refs.emplace_back(ref); });
    std::vector<EntryRef> new_refs;
    MyCompactable my_compactable(_allocator, new_refs);
    auto filter = make_entry_ref_filter<RefT>(false);
    _hash_map.move_keys_on_compact(my_compactable, filter);
    std::vector<EntryRef> verify_new_refs;
    _hash_map.foreach_key([&verify_new_refs](EntryRef ref) { verify_new_refs.emplace_back(ref); });
    EXPECT_EQ(small_population, refs.size());
    EXPECT_NE(refs, new_refs);
    EXPECT_EQ(new_refs, verify_new_refs);
    for (uint32_t i = 0; i < small_population; ++i) {
        EXPECT_NE(refs[i], new_refs[i]);
        auto value = _allocator.get_wrapped(refs[i]).value();
        auto new_value = _allocator.get_wrapped(refs[i]).value();
        EXPECT_EQ(value, new_value);
    }
}

TEST_F(DataStoreShardedHashTest, normalize_values_works)
{
    test_normalize_values(false, false);
}

TEST_F(DataStoreShardedHashTest, normalize_values_all_filter_works)
{
    test_normalize_values(true, false);
}

TEST_F(DataStoreShardedHashTest, normalize_values_one_filter_works)
{
    test_normalize_values(true, true);
}

TEST_F(DataStoreShardedHashTest, foreach_value_all_filter_works)
{
    test_foreach_value(false);
}

TEST_F(DataStoreShardedHashTest, foreach_value_one_filter_works)
{
    test_foreach_value(true);
}

TEST_F(DataStoreShardedHashTest, compact_worst_shard_works)
{
    populate_sample_data(small_population);
    for (uint32_t i = 10; i < small_population; ++i) {
        remove(i);
    }
    commit();
    auto usage_before = _hash_map.get_memory_usage();
    _hash_map.compact_worst_shard();
    auto usage_after = _hash_map.get_memory_usage();
    EXPECT_GT(usage_before.deadBytes(), usage_after.deadBytes());
}

GTEST_MAIN_RUN_ALL_TESTS()
