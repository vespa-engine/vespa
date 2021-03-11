// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/datastore/simple_hash_map.h>
#include <vespa/vespalib/datastore/unique_store_allocator.h>
#include <vespa/vespalib/datastore/unique_store_comparator.h>

#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/rand48.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/vespalib/datastore/unique_store_allocator.hpp>

#include <vespa/log/log.h>
LOG_SETUP("vespalib_datastore_simple_hash_test");

using vespalib::datastore::EntryRef;
using RefT = vespalib::datastore::EntryRefT<22>;
using MyAllocator = vespalib::datastore::UniqueStoreAllocator<uint32_t, RefT>;
using MyDataStore = vespalib::datastore::DataStoreT<RefT>;
using MyCompare = vespalib::datastore::UniqueStoreComparator<uint32_t, RefT>;
using MyHashMap = vespalib::datastore::SimpleHashMap;
using GenerationHandler = vespalib::GenerationHandler;
using vespalib::makeLambdaTask;

struct DataStoreSimpleHashTest : public ::testing::Test
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

    DataStoreSimpleHashTest();
    ~DataStoreSimpleHashTest();
    void commit();
    void insert(uint32_t key);
    void remove(uint32_t key);

    void read_work(uint32_t cnt);
    void read_work();
    void write_work(uint32_t cnt);
};


DataStoreSimpleHashTest::DataStoreSimpleHashTest()
    : _generationHandler(),
      _allocator(),
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


DataStoreSimpleHashTest::~DataStoreSimpleHashTest()
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
DataStoreSimpleHashTest::commit()
{
    _store.transferHoldLists(_generationHandler.getCurrentGeneration());
    _hash_map.transfer_hold_lists(_generationHandler.getCurrentGeneration());
    _generationHandler.incGeneration();
    _store.trimHoldLists(_generationHandler.getFirstUsedGeneration());
    _hash_map.trim_hold_lists(_generationHandler.getFirstUsedGeneration());
}

void
DataStoreSimpleHashTest::insert(uint32_t key)
{
    MyCompare comp(_store, key);
std::function<EntryRef(void)> insert_entry([this, key]() -> EntryRef { return _allocator.allocate(key); });
    auto& result = _hash_map.add(comp, insert_entry);
    auto ref = result.first.load_relaxed();
    auto &wrapped_entry = _allocator.get_wrapped(ref);
    EXPECT_EQ(key, wrapped_entry.value());
}

void
DataStoreSimpleHashTest::remove(uint32_t key)
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
DataStoreSimpleHashTest::read_work(uint32_t cnt)
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
    }
    _done_read_work += i;
    _found_count += found;
    LOG(info, "done %u read work", i);
}


void
DataStoreSimpleHashTest::read_work()
{
    read_work(std::numeric_limits<uint32_t>::max());
}


void
DataStoreSimpleHashTest::write_work(uint32_t cnt)
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


TEST_F(DataStoreSimpleHashTest, single_threaded_reader_without_updates)
{
    _report_work = true;
    write_work(10);
    _stop_read = 0;
    read_work(10);
}

TEST_F(DataStoreSimpleHashTest, single_threaded_reader_during_updates)
{
    uint32_t cnt = 1000000;
    _report_work = true;
    _writer.execute(makeLambdaTask([this, cnt]() { write_work(cnt); }));
    _readers.execute(makeLambdaTask([this]() { read_work(); }));
}

TEST_F(DataStoreSimpleHashTest, multi_threaded_reader_during_updates)
{
    uint32_t cnt = 1000000;
    _report_work = true;
    _writer.execute(makeLambdaTask([this, cnt]() { write_work(cnt); }));
    for (size_t i = 0; i < 4; ++i) {
        _readers.execute(makeLambdaTask([this]() { read_work(); }));
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
