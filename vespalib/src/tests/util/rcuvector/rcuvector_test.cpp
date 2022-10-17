// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/memory_allocator_observer.h>
#include <vespa/vespalib/datastore/atomic_value_wrapper.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/rcuvector.h>
#include <vespa/vespalib/util/rcuvector.hpp>
#include <vespa/vespalib/util/round_up_to_page_size.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <random>

using namespace vespalib;

using vespalib::alloc::Alloc;
using vespalib::alloc::MemoryAllocator;
using vespalib::datastore::AtomicValueWrapper;
using vespalib::makeLambdaTask;
using MyMemoryAllocator = vespalib::alloc::test::MemoryAllocatorObserver;
using AllocStats = MyMemoryAllocator::Stats;

bool
assertUsage(const MemoryUsage & exp, const MemoryUsage & act)
{
    bool retval = true;
    EXPECT_EQ(exp.allocatedBytes(), act.allocatedBytes()) << (retval = false, "");
    EXPECT_EQ(exp.usedBytes(), act.usedBytes()) << (retval = false, "");
    EXPECT_EQ(exp.deadBytes(), act.deadBytes()) << (retval = false, "");
    EXPECT_EQ(exp.allocatedBytesOnHold(), act.allocatedBytesOnHold()) << ( retval = false, "");
    return retval;
}

GrowStrategy
growStrategy(size_t initial, float factor, size_t delta, size_t minimal = 0) {
    return GrowStrategy(initial, factor, delta, minimal);
}

TEST(RcuVectorTest, basic)
{
    { // insert
        RcuVector<int32_t> v(growStrategy(4, 0, 4));
        for (int32_t i = 0; i < 100; ++i) {
            v.push_back(i);
            EXPECT_EQ(i, v[i]);
            EXPECT_EQ(i, v.acquire_elem_ref(i));
            EXPECT_EQ((size_t)i + 1, v.size());
        }
        for (int32_t i = 0; i < 100; ++i) {
            v[i] = i + 1;
            EXPECT_EQ(i + 1, v[i]);
            EXPECT_EQ(i + 1, v.acquire_elem_ref(i));
            EXPECT_EQ(100u, v.size());
        }
    }
}

TEST(RcuVectorTest, resize)
{
    { // resize percent
        RcuVector<int32_t> v(growStrategy(2, 0.50, 0));
        EXPECT_EQ(2u, v.capacity());
        v.push_back(0);
        EXPECT_EQ(2u, v.capacity());
        v.push_back(0);
        EXPECT_EQ(2u, v.capacity());
        EXPECT_TRUE(v.isFull());
        v.push_back(0);
        EXPECT_EQ(3u, v.capacity());
        EXPECT_TRUE(v.isFull());
    }
    { // resize delta
        RcuVector<int32_t> v(growStrategy(1, 0, 3));
        EXPECT_EQ(1u, v.capacity());
        v.push_back(0);
        EXPECT_EQ(1u, v.capacity());
        EXPECT_TRUE(v.isFull());
        v.push_back(0);
        EXPECT_EQ(4u, v.capacity());
        EXPECT_TRUE(!v.isFull());
    }
    { // resize both
        RcuVector<int32_t> v(growStrategy(2, 2.0, 3));
        EXPECT_EQ(2u, v.capacity());
        v.push_back(0);
        EXPECT_EQ(2u, v.capacity());
        v.push_back(0);
        EXPECT_EQ(2u, v.capacity());
        EXPECT_TRUE(v.isFull());
        v.push_back(0);
        EXPECT_EQ(9u, v.capacity());
        EXPECT_TRUE(!v.isFull());
    }
    { // reserve
        RcuVector<int32_t> v(growStrategy(2, 0, 0));
        EXPECT_EQ(2u, v.capacity());
        v.unsafe_reserve(8);
        EXPECT_EQ(8u, v.capacity());
    }
    { // explicit resize
        GenerationHolder g;
        RcuVectorBase<int8_t> v(growStrategy(16, 1.0, 0), g);
        v.push_back(1);
        v.push_back(2);
        g.assign_generation(0);
        g.reclaim(1);
        const int8_t *old = &v[0];
        EXPECT_EQ(16u, v.capacity());
        EXPECT_EQ(2u, v.size());
        v.ensure_size(32, 3);
        v[0] = 3;
        v[1] = 3;
        g.assign_generation(1);
        EXPECT_EQ(1, old[0]);
        EXPECT_EQ(2, old[1]);
        EXPECT_EQ(3, v[0]);
        EXPECT_EQ(3, v[1]);
        EXPECT_EQ(3, v[2]);
        EXPECT_EQ(3, v[31]);
        EXPECT_EQ(64u, v.capacity());
        EXPECT_EQ(32u, v.size());
        g.reclaim(2);
    }
}

TEST(RcuVectorTest, generation_handling)
{
    RcuVector<int32_t> v(growStrategy(2, 0, 2));
    v.push_back(0);
    v.push_back(10);
    EXPECT_EQ(0u, v.getMemoryUsage().allocatedBytesOnHold());
    v.push_back(20); // new array
    EXPECT_EQ(8u, v.getMemoryUsage().allocatedBytesOnHold());

    v.setGeneration(1);
    v.push_back(30);
    EXPECT_EQ(8u, v.getMemoryUsage().allocatedBytesOnHold());
    v.push_back(40); // new array
    EXPECT_EQ(24u, v.getMemoryUsage().allocatedBytesOnHold());

    v.setGeneration(2);
    v.push_back(50);
    v.reclaim_memory(3);
    EXPECT_EQ(0u, v.getMemoryUsage().allocatedBytesOnHold());
    v.push_back(60); // new array
    EXPECT_EQ(24u, v.getMemoryUsage().allocatedBytesOnHold());
}

TEST(RcuVectorTest, reserve)
{
    RcuVector<int32_t> v(growStrategy(2, 0, 2));
    EXPECT_EQ(2u, v.capacity());
    EXPECT_EQ(0u, v.size());
    v.push_back(0);
    v.push_back(10);
    EXPECT_EQ(2u, v.size());
    EXPECT_EQ(2u, v.capacity());
    EXPECT_EQ(0u, v.getMemoryUsage().allocatedBytesOnHold());
    v.reserve(30);
    EXPECT_EQ(2u, v.size());
    EXPECT_EQ(32u, v.capacity());
    EXPECT_EQ(8u, v.getMemoryUsage().allocatedBytesOnHold());
    v.reserve(32);
    EXPECT_EQ(2u, v.size());
    EXPECT_EQ(32u, v.capacity());
    EXPECT_EQ(8u, v.getMemoryUsage().allocatedBytesOnHold());
    v.reserve(100);
    EXPECT_EQ(2u, v.size());
    EXPECT_EQ(102u, v.capacity());
    EXPECT_EQ(8u + 32u*4u, v.getMemoryUsage().allocatedBytesOnHold());
}

TEST(RcuVectorTest, memory_usage)
{
    RcuVector<int8_t> v(growStrategy(2, 0, 2));
    EXPECT_TRUE(assertUsage(MemoryUsage(2,0,0,0), v.getMemoryUsage()));
    v.push_back(0);
    EXPECT_TRUE(assertUsage(MemoryUsage(2,1,0,0), v.getMemoryUsage()));
    v.push_back(1);
    EXPECT_TRUE(assertUsage(MemoryUsage(2,2,0,0), v.getMemoryUsage()));
    v.push_back(2);
    EXPECT_TRUE(assertUsage(MemoryUsage(6,5,0,2), v.getMemoryUsage()));
    v.push_back(3);
    EXPECT_TRUE(assertUsage(MemoryUsage(6,6,0,2), v.getMemoryUsage()));
    v.push_back(4);
    EXPECT_TRUE(assertUsage(MemoryUsage(12,11,0,6), v.getMemoryUsage()));
    v.reclaim_memory(1);
    EXPECT_TRUE(assertUsage(MemoryUsage(6,5,0,0), v.getMemoryUsage()));
}

void verify_shrink_with_buffer_copying(size_t initial_size, size_t absolute_minimum) {
    const size_t minimal_capacity = std::max(4ul, absolute_minimum);
    const size_t initial_capacity = std::max(initial_size, minimal_capacity);
    GenerationHolder g;
    RcuVectorBase<int8_t> v(growStrategy(initial_size, 1.0, 0, absolute_minimum), g);
    v.push_back(1);
    v.push_back(2);
    v.push_back(3);
    v.push_back(4);
    g.assign_generation(0);
    g.reclaim(1);
    MemoryUsage mu;
    mu = v.getMemoryUsage();
    mu.incAllocatedBytesOnHold(g.get_held_bytes());
    EXPECT_TRUE(assertUsage(MemoryUsage(initial_capacity, 4, 0, 0), mu));
    EXPECT_EQ(4u, v.size());
    EXPECT_EQ(initial_capacity, v.capacity());
    EXPECT_EQ(1, v[0]);
    EXPECT_EQ(2, v[1]);
    EXPECT_EQ(3, v[2]);
    EXPECT_EQ(4, v[3]);
    const int8_t *old = &v[0];
    v.shrink(2);
    g.assign_generation(1);
    EXPECT_EQ(2u, v.size());
    EXPECT_EQ(minimal_capacity, v.capacity());
    EXPECT_EQ(1, v[0]);
    EXPECT_EQ(2, v[1]);
    EXPECT_EQ(1, old[0]);
    EXPECT_EQ(2, old[1]);
    g.reclaim(2);
    EXPECT_EQ(1, v[0]);
    EXPECT_EQ(2, v[1]);
    mu = v.getMemoryUsage();
    mu.incAllocatedBytesOnHold(g.get_held_bytes());
    EXPECT_TRUE(assertUsage(MemoryUsage(minimal_capacity, 2, 0, 0), mu));
}

TEST(RcuVectorTest, shrink_with_buffer_copying)
{
    verify_shrink_with_buffer_copying(16, 8);
    verify_shrink_with_buffer_copying(0, 8);
    verify_shrink_with_buffer_copying(0, 0);
}

struct ShrinkFixture {
    GenerationHolder g;
    size_t initial_capacity;
    size_t initial_size;
    RcuVectorBase<int> vec;
    int *oldPtr;
    ShrinkFixture() : g(),
                      initial_capacity(4 * page_ints()),
                      initial_size(initial_capacity / 1024 * 1000),
                      vec(growStrategy(initial_capacity, 0.50, 0), g, alloc::Alloc::allocMMap()), oldPtr()
    {
        for (size_t i = 0; i < initial_size; ++i) {
            vec.push_back(7);
        }
        EXPECT_EQ(initial_size, vec.size());
        EXPECT_EQ(initial_capacity, vec.capacity());
        assertEmptyHoldList();
        oldPtr = &vec[0];
    }
    void assertOldEqualNewBuffer() {
        EXPECT_EQ(oldPtr, &vec[0]);
    }
    void assertEmptyHoldList() {
        EXPECT_EQ(0u, g.get_held_bytes());
    }
    static size_t page_ints() { return round_up_to_page_size(1) / sizeof(int); }
};

TEST(RcuVectorTest, shrink_does_not_increase_allocated_memory)
{
    ShrinkFixture f;
    size_t shrink_size = f.initial_capacity * 2 / 3 + 2;
    f.vec.shrink(shrink_size);
    EXPECT_EQ(shrink_size, f.vec.size());
    EXPECT_EQ(f.initial_capacity, f.vec.capacity());
    f.assertOldEqualNewBuffer();
    f.assertEmptyHoldList();
}

TEST(RcuVectorTest, shrink_can_shrink_mmap_allocation)
{
    ShrinkFixture f;
    f.vec.shrink(2 * f.page_ints());
    EXPECT_EQ(2 * f.page_ints(), f.vec.size());
    EXPECT_EQ(3 * f.page_ints(), f.vec.capacity());
    f.assertOldEqualNewBuffer();
    f.assertEmptyHoldList();
}

TEST(RcuVectorTest, small_expand)
{
    GenerationHolder g;
    RcuVectorBase<int8_t> v(growStrategy(1, 0.50, 0), g);
    EXPECT_EQ(1u, v.capacity());
    EXPECT_EQ(0u, v.size());
    v.push_back(1);
    EXPECT_EQ(1u, v.capacity());
    EXPECT_EQ(1u, v.size());
    v.push_back(2);
    EXPECT_EQ(2u, v.capacity());
    EXPECT_EQ(2u, v.size());
    g.assign_generation(1);
    g.reclaim(2);
}

struct FixtureBase {
    using generation_t = GenerationHandler::generation_t;

    AllocStats stats;
    std::unique_ptr<MemoryAllocator> allocator;
    Alloc initial_alloc;
    GenerationHolder g;

    FixtureBase();
    ~FixtureBase();
};

FixtureBase::FixtureBase()
    : stats(),
      allocator(std::make_unique<MyMemoryAllocator>(stats)),
      initial_alloc(Alloc::alloc_with_allocator(allocator.get())),
      g()
{
}

FixtureBase::~FixtureBase() = default;

struct Fixture : public FixtureBase {
    RcuVectorBase<int> arr;

    Fixture();
    ~Fixture();
    void assign_and_reclaim(generation_t assign_gen, generation_t reclaim_gen)
    {
        g.assign_generation(assign_gen);
        g.reclaim(reclaim_gen);
    }
};

Fixture::Fixture()
    : FixtureBase(),
      arr(growStrategy(16, 1.0, 0), g, initial_alloc)
{
    arr.reserve(100);
}

Fixture::~Fixture() = default;

TEST(RcuVectorTest, memory_allocator_can_be_set)
{
    Fixture f;
    EXPECT_EQ(AllocStats(2, 0), f.stats);
    f.assign_and_reclaim(1, 2);
    EXPECT_EQ(AllocStats(2, 1), f.stats);
}

TEST(RcuVectorTest, memory_allocator_is_preserved_across_reset)
{
    Fixture f;
    f.arr.reset();
    f.arr.reserve(100);
    EXPECT_EQ(AllocStats(4, 1), f.stats);
    f.assign_and_reclaim(1, 2);
    EXPECT_EQ(AllocStats(4, 3), f.stats);
}

TEST(RcuVectorTest, created_replacement_vector_uses_same_memory_allocator)
{
    Fixture f;
    auto arr2 = f.arr.create_replacement_vector();
    EXPECT_EQ(AllocStats(2, 0), f.stats);
    arr2.reserve(100);
    EXPECT_EQ(AllocStats(3, 0), f.stats);
    f.assign_and_reclaim(1, 2);
    EXPECT_EQ(AllocStats(3, 1), f.stats);
}

TEST(RcuVectorTest, ensure_size_and_shrink_use_same_memory_allocator)
{
    Fixture f;
    f.arr.ensure_size(2000);
    EXPECT_EQ(AllocStats(3, 0), f.stats);
    f.arr.shrink(1000);
    EXPECT_EQ(AllocStats(4, 0), f.stats);
    f.assign_and_reclaim(1, 2);
    EXPECT_EQ(AllocStats(4, 3), f.stats);
}

namespace {

class ReadStopper {
    std::atomic<bool>& _stop_read;
public:
    ReadStopper(std::atomic<bool>& stop_read)
        : _stop_read(stop_read)
    {
    }
    ~ReadStopper() {
        _stop_read = true;
    }
};

}

struct StressFixture : public FixtureBase {
    using AtomicIntWrapper = AtomicValueWrapper<int>;
    RcuVectorBase<AtomicIntWrapper> arr;
    std::atomic<bool> stop_read;
    uint32_t read_area;
    GenerationHandler generation_handler;
    vespalib::ThreadStackExecutor writer; // 1 write thread
    vespalib::ThreadStackExecutor readers; // multiple reader threads
    StressFixture();
    ~StressFixture();
    void commit();
    void sync();
    void read_work();
    void write_work(uint32_t cnt);
    void run_test(uint32_t cnt, uint32_t num_readers);
};

StressFixture::StressFixture()
    : FixtureBase(),
      arr(growStrategy(16, 1.0, 0), g, initial_alloc),
      stop_read(false),
      read_area(1000),
      generation_handler(),
      writer(1, 128_Ki),
      readers(4, 128_Ki)
{
    arr.ensure_size(read_area, AtomicIntWrapper(0));
}

StressFixture::~StressFixture() = default;

void
StressFixture::commit()
{
    auto current_gen = generation_handler.getCurrentGeneration();
    g.assign_generation(current_gen);
    generation_handler.incGeneration();
    auto first_used_gen = generation_handler.get_oldest_used_generation();
    g.reclaim(first_used_gen);
}

void
StressFixture::sync()
{
    writer.sync();
    readers.sync();
}

void
StressFixture::read_work()
{
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<uint32_t> distrib(0, read_area - 1);
    std::vector<int> old(read_area);
    while (!stop_read.load(std::memory_order_relaxed)) {
        uint32_t idx = distrib(gen);
        auto guard = generation_handler.takeGuard();
        int value = arr.acquire_elem_ref(idx).load_acquire();
        EXPECT_LE(old[idx], value);
        old[idx] = value;
    }
}

void
StressFixture::write_work(uint32_t cnt)
{
    ReadStopper read_stopper(stop_read);
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<uint32_t> distrib(0, read_area - 1);
    for (uint32_t i = 0; i < cnt; ++i) {
        if ((i % 1000) == 0) {
            arr.ensure_size(64_Ki + 1, AtomicIntWrapper(0));
        }
        if ((i % 1000) == 500) {
            arr.shrink(read_area);
        }
        uint32_t idx = distrib(gen);
        arr[idx].store_release(arr[idx].load_relaxed() + 1);
        commit();
    }
}

void
StressFixture::run_test(uint32_t cnt, uint32_t num_readers)
{
    auto failed_write_task = writer.execute(makeLambdaTask([this, cnt]() { write_work(cnt); }));
    ASSERT_FALSE(failed_write_task);
    for (uint32_t i = 0; i < num_readers; ++i) {
        readers.execute(makeLambdaTask([this]() { read_work(); }));
    }
    sync();
    commit();
    EXPECT_LE((cnt / 1000) * 2, stats.alloc_cnt);
}

TEST(RcuVectorTest, single_writer_four_readers)
{
    StressFixture f;
    f.run_test(20000, 4);
}

GTEST_MAIN_RUN_ALL_TESTS()
