// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
using MyComparator = vespalib::datastore::UniqueStoreComparator<uint32_t, RefT>;
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
    const MyComparator _comparator;
    std::unique_ptr<FixedSizeHashMap> _hash_map;
    vespalib::Rand48 _rnd;

    DataStoreFixedSizeHashTest();
    ~DataStoreFixedSizeHashTest();
    void commit();
    size_t size() const noexcept;
    void insert(uint32_t key);
    void remove(uint32_t key);
    bool has_key(uint32_t key);
    void use_single_hash_chain();
    void setup_single_hash_chain_three_elems();
    std::vector<bool> check_three_elems();
};


DataStoreFixedSizeHashTest::DataStoreFixedSizeHashTest()
    : _generation_handler(),
      _generation_holder(),
      _allocator({}),
      _store(_allocator.get_data_store()),
      _comparator(_store),
      _hash_map(),
      _rnd()
{
    _rnd.srand48(32);
    _hash_map = std::make_unique<FixedSizeHashMap>(20, 40, 1);
}


DataStoreFixedSizeHashTest::~DataStoreFixedSizeHashTest()
{
    commit();
}


void
DataStoreFixedSizeHashTest::commit()
{
    _store.assign_generation(_generation_handler.getCurrentGeneration());
    _hash_map->assign_generation(_generation_handler.getCurrentGeneration());
    _generation_holder.assign_generation(_generation_handler.getCurrentGeneration());
    _generation_handler.incGeneration();
    _store.reclaim_memory(_generation_handler.get_oldest_used_generation());
    _hash_map->reclaim_memory(_generation_handler.get_oldest_used_generation());
    _generation_holder.reclaim(_generation_handler.get_oldest_used_generation());
}

size_t
DataStoreFixedSizeHashTest::size() const noexcept
{
    return _hash_map->size();
}

void
DataStoreFixedSizeHashTest::insert(uint32_t key)
{
    auto comp = _comparator.make_for_lookup(key);
    std::function<EntryRef()> insert_entry([this, key]() -> EntryRef { return _allocator.allocate(key); });
    auto& result = _hash_map->add(_hash_map->get_comp(comp), insert_entry);
    auto ref = result.first.load_relaxed();
    auto &wrapped_entry = _allocator.get_wrapped(ref);
    EXPECT_EQ(key, wrapped_entry.value());
}

void
DataStoreFixedSizeHashTest::remove(uint32_t key)
{
    auto comp = _comparator.make_for_lookup(key);
    auto result = _hash_map->remove(_hash_map->get_comp(comp));
    if (result != nullptr) {
        auto ref = result->first.load_relaxed();
        auto &wrapped_entry = _allocator.get_wrapped(ref);
        EXPECT_EQ(key, wrapped_entry.value());
        _allocator.hold(ref);
    }
}

bool
DataStoreFixedSizeHashTest::has_key(uint32_t key)
{
    auto comp = _comparator.make_for_lookup(key);
    auto result = _hash_map->find(_hash_map->get_comp(comp));
    if (result != nullptr) {
        auto ref = result->first.load_relaxed();
        auto& wrapped_entry = _allocator.get_wrapped(ref);
        EXPECT_EQ(key, wrapped_entry.value());
        return true;
    }
    return false;
}

void
DataStoreFixedSizeHashTest::use_single_hash_chain()
{
    _hash_map = std::make_unique<FixedSizeHashMap>(1, 40, 1);
}

void
DataStoreFixedSizeHashTest::setup_single_hash_chain_three_elems()
{
    use_single_hash_chain();
    for (uint32_t key = 1; key < 4; ++key) {
        insert(key);
    }
}

std::vector<bool>
DataStoreFixedSizeHashTest::check_three_elems()
{
    std::vector<bool> result;
    for (uint32_t key = 1; key < 4; ++key) {
        result.push_back(has_key(key));
    }
    return result;
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
    EXPECT_FALSE(has_key(3));
    EXPECT_TRUE(has_key(4));
}

TEST_F(DataStoreFixedSizeHashTest, free_list_works)
{
    _hash_map = std::make_unique<FixedSizeHashMap>(1, 3, 1);
    insert(1);
    insert(2);
    insert(3);
    EXPECT_TRUE(_hash_map->full());
    auto guard = _generation_handler.takeGuard();
    remove(1);
    remove(2);
    EXPECT_TRUE(_hash_map->full());
    guard = GenerationHandler::Guard();
    commit();
    EXPECT_FALSE(_hash_map->full());
    insert(4);
    EXPECT_FALSE(_hash_map->full());
    insert(5);
    EXPECT_TRUE(_hash_map->full());
}

TEST_F(DataStoreFixedSizeHashTest, remove_last_inserted_works)
{
    setup_single_hash_chain_three_elems();
    remove(3);
    EXPECT_EQ((std::vector<bool>{true, true, false}), check_three_elems());
}

TEST_F(DataStoreFixedSizeHashTest, remove_middle_inserted_works)
{
    setup_single_hash_chain_three_elems();
    remove(2);
    EXPECT_EQ((std::vector<bool>{true, false, true}), check_three_elems());
}

TEST_F(DataStoreFixedSizeHashTest, remove_first_inserted_works)
{
    setup_single_hash_chain_three_elems();
    remove(1);
    EXPECT_EQ((std::vector<bool>{false, true, true}), check_three_elems());
}

TEST_F(DataStoreFixedSizeHashTest, add_existing_works)
{
    use_single_hash_chain();
    EXPECT_FALSE(has_key(1));
    EXPECT_EQ(0, size());
    insert(1);
    EXPECT_TRUE(has_key(1));
    EXPECT_EQ(1, size());
    insert(1);
    EXPECT_TRUE(has_key(1));
    EXPECT_EQ(1, size());
    remove(1);
    EXPECT_FALSE(has_key(1));
    EXPECT_EQ(0, size());
}

TEST_F(DataStoreFixedSizeHashTest, remove_nonexisting_works)
{
    use_single_hash_chain();
    EXPECT_FALSE(has_key(1));
    remove(1);
    EXPECT_FALSE(has_key(1));
}

TEST_F(DataStoreFixedSizeHashTest, lookups_works_after_insert_and_remove)
{
    use_single_hash_chain();
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
    for (auto &kv : expected) {
        auto comp = _comparator.make_for_lookup(kv.first);
        EXPECT_EQ(kv.second, _hash_map->find(_hash_map->get_comp(comp)) != nullptr);
    }
}

TEST_F(DataStoreFixedSizeHashTest, memory_usage_is_reported)
{
    auto initial_usage = _hash_map->get_memory_usage();
    EXPECT_LT(0, initial_usage.allocatedBytes());
    EXPECT_LT(0, initial_usage.usedBytes());
    EXPECT_LT(initial_usage.usedBytes(), initial_usage.allocatedBytes());
    EXPECT_EQ(0, initial_usage.deadBytes());
    EXPECT_EQ(0, initial_usage.allocatedBytesOnHold());
    auto guard = _generation_handler.takeGuard();
    insert(10);
    remove(10);
    commit();
    auto usage1 = _hash_map->get_memory_usage();
    EXPECT_EQ(initial_usage.allocatedBytes(), usage1.allocatedBytes());
    EXPECT_LT(initial_usage.usedBytes(), usage1.usedBytes());
    EXPECT_LT(usage1.usedBytes(), usage1.allocatedBytes());
    EXPECT_EQ(0, usage1.deadBytes());
    EXPECT_LT(0, usage1.allocatedBytesOnHold());
    guard = GenerationHandler::Guard();
    commit();
    auto usage2 = _hash_map->get_memory_usage();
    EXPECT_EQ(initial_usage.allocatedBytes(), usage2.allocatedBytes());
    EXPECT_EQ(usage1.usedBytes(), usage2.usedBytes());
    EXPECT_LT(0, usage2.deadBytes());
    EXPECT_EQ(0, usage2.allocatedBytesOnHold());
}

GTEST_MAIN_RUN_ALL_TESTS()
