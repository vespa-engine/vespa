// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/test/memory_allocator_observer.h>
#include <vespa/vespalib/util/rcuvector.h>
#include <vespa/vespalib/util/round_up_to_page_size.h>
#include <vespa/vespalib/util/size_literals.h>

using namespace vespalib;

using vespalib::alloc::Alloc;
using vespalib::alloc::MemoryAllocator;
using MyMemoryAllocator = vespalib::alloc::test::MemoryAllocatorObserver;
using AllocStats = MyMemoryAllocator::Stats;

bool
assertUsage(const MemoryUsage & exp, const MemoryUsage & act)
{
    bool retval = true;
    if (!EXPECT_EQUAL(exp.allocatedBytes(), act.allocatedBytes())) retval = false;
    if (!EXPECT_EQUAL(exp.usedBytes(), act.usedBytes())) retval = false;
    if (!EXPECT_EQUAL(exp.deadBytes(), act.deadBytes())) retval = false;
    if (!EXPECT_EQUAL(exp.allocatedBytesOnHold(), act.allocatedBytesOnHold())) retval = false;
    return retval;
}

TEST("test generation holder")
{
    GenerationHolder gh;
    gh.hold(std::make_unique<RcuVectorHeld<int32_t>>(sizeof(int32_t), 0));
    gh.transferHoldLists(0);
    gh.hold(std::make_unique<RcuVectorHeld<int32_t>>(sizeof(int32_t), 1));
    gh.transferHoldLists(1);
    gh.hold(std::make_unique<RcuVectorHeld<int32_t>>(sizeof(int32_t), 2));
    gh.transferHoldLists(2);
    gh.hold(std::make_unique<RcuVectorHeld<int32_t>>(sizeof(int32_t), 4));
    gh.transferHoldLists(4);
    EXPECT_EQUAL(4u * sizeof(int32_t), gh.getHeldBytes());
    gh.trimHoldLists(0);
    EXPECT_EQUAL(4u * sizeof(int32_t), gh.getHeldBytes());
    gh.trimHoldLists(1);
    EXPECT_EQUAL(3u * sizeof(int32_t), gh.getHeldBytes());
    gh.trimHoldLists(2);
    EXPECT_EQUAL(2u * sizeof(int32_t), gh.getHeldBytes());
    gh.hold(std::make_unique<RcuVectorHeld<int32_t>>(sizeof(int32_t), 6));
    gh.transferHoldLists(6);
    EXPECT_EQUAL(3u * sizeof(int32_t), gh.getHeldBytes());
    gh.trimHoldLists(6);
    EXPECT_EQUAL(1u * sizeof(int32_t), gh.getHeldBytes());
    gh.trimHoldLists(7);
    EXPECT_EQUAL(0u * sizeof(int32_t), gh.getHeldBytes());
    gh.trimHoldLists(7);
    EXPECT_EQUAL(0u * sizeof(int32_t), gh.getHeldBytes());
}

TEST("test basic")
{
    { // insert
        RcuVector<int32_t> v(4, 0, 4);
        for (int32_t i = 0; i < 100; ++i) {
            v.push_back(i);
            EXPECT_EQUAL(i, v[i]);
            EXPECT_EQUAL((size_t)i + 1, v.size());
        }
        for (int32_t i = 0; i < 100; ++i) {
            v[i] = i + 1;
            EXPECT_EQUAL(i + 1, v[i]);
            EXPECT_EQUAL(100u, v.size());
        }
    }
}

TEST("test resize")
{
    { // resize percent
        RcuVector<int32_t> v(2, 50, 0);
        EXPECT_EQUAL(2u, v.capacity());
        v.push_back(0);
        EXPECT_EQUAL(2u, v.capacity());
        v.push_back(0);
        EXPECT_EQUAL(2u, v.capacity());
        EXPECT_TRUE(v.isFull());
        v.push_back(0);
        EXPECT_EQUAL(3u, v.capacity());
        EXPECT_TRUE(v.isFull());
    }
    { // resize delta
        RcuVector<int32_t> v(1, 0, 3);
        EXPECT_EQUAL(1u, v.capacity());
        v.push_back(0);
        EXPECT_EQUAL(1u, v.capacity());
        EXPECT_TRUE(v.isFull());
        v.push_back(0);
        EXPECT_EQUAL(4u, v.capacity());
        EXPECT_TRUE(!v.isFull());
    }
    { // resize both
        RcuVector<int32_t> v(2, 200, 3);
        EXPECT_EQUAL(2u, v.capacity());
        v.push_back(0);
        EXPECT_EQUAL(2u, v.capacity());
        v.push_back(0);
        EXPECT_EQUAL(2u, v.capacity());
        EXPECT_TRUE(v.isFull());
        v.push_back(0);
        EXPECT_EQUAL(9u, v.capacity());
        EXPECT_TRUE(!v.isFull());
    }
    { // reserve
        RcuVector<int32_t> v(2, 0, 0);
        EXPECT_EQUAL(2u, v.capacity());
        v.unsafe_reserve(8);
        EXPECT_EQUAL(8u, v.capacity());
    }
    { // explicit resize
        GenerationHolder g;
        RcuVectorBase<int8_t> v(g);
        v.push_back(1);
        v.push_back(2);
        g.transferHoldLists(0);
        g.trimHoldLists(1);
        const int8_t *old = &v[0];
        EXPECT_EQUAL(16u, v.capacity());
        EXPECT_EQUAL(2u, v.size());
        v.ensure_size(32, 3);
        v[0] = 3;
        v[1] = 3;
        g.transferHoldLists(1);
        EXPECT_EQUAL(1, old[0]);
        EXPECT_EQUAL(2, old[1]);
        EXPECT_EQUAL(3, v[0]);
        EXPECT_EQUAL(3, v[1]);
        EXPECT_EQUAL(3, v[2]);
        EXPECT_EQUAL(3, v[31]);
        EXPECT_EQUAL(64u, v.capacity());
        EXPECT_EQUAL(32u, v.size());
        g.trimHoldLists(2);
    }
}

TEST("test generation handling")
{
    RcuVector<int32_t> v(2, 0, 2);
    v.push_back(0);
    v.push_back(10);
    EXPECT_EQUAL(0u, v.getMemoryUsage().allocatedBytesOnHold());
    v.push_back(20); // new array
    EXPECT_EQUAL(8u, v.getMemoryUsage().allocatedBytesOnHold());

    v.setGeneration(1);
    v.push_back(30);
    EXPECT_EQUAL(8u, v.getMemoryUsage().allocatedBytesOnHold());
    v.push_back(40); // new array
    EXPECT_EQUAL(24u, v.getMemoryUsage().allocatedBytesOnHold());

    v.setGeneration(2);
    v.push_back(50);
    v.removeOldGenerations(3);
    EXPECT_EQUAL(0u, v.getMemoryUsage().allocatedBytesOnHold());
    v.push_back(60); // new array
    EXPECT_EQUAL(24u, v.getMemoryUsage().allocatedBytesOnHold());
}

TEST("test reserve") {
    RcuVector<int32_t> v(2, 0, 2);
    EXPECT_EQUAL(2u, v.capacity());
    EXPECT_EQUAL(0u, v.size());
    v.push_back(0);
    v.push_back(10);
    EXPECT_EQUAL(2u, v.size());
    EXPECT_EQUAL(2u, v.capacity());
    EXPECT_EQUAL(0u, v.getMemoryUsage().allocatedBytesOnHold());
    v.reserve(30);
    EXPECT_EQUAL(2u, v.size());
    EXPECT_EQUAL(32u, v.capacity());
    EXPECT_EQUAL(8u, v.getMemoryUsage().allocatedBytesOnHold());
    v.reserve(32);
    EXPECT_EQUAL(2u, v.size());
    EXPECT_EQUAL(32u, v.capacity());
    EXPECT_EQUAL(8u, v.getMemoryUsage().allocatedBytesOnHold());
    v.reserve(100);
    EXPECT_EQUAL(2u, v.size());
    EXPECT_EQUAL(102u, v.capacity());
    EXPECT_EQUAL(8u + 32u*4u, v.getMemoryUsage().allocatedBytesOnHold());
}

TEST("test memory usage")
{
    RcuVector<int8_t> v(2, 0, 2);
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
    v.removeOldGenerations(1);
    EXPECT_TRUE(assertUsage(MemoryUsage(6,5,0,0), v.getMemoryUsage()));
}

TEST("test shrink() with buffer copying")
{
    GenerationHolder g;
    RcuVectorBase<int8_t> v(16, 100, 0, g);
    v.push_back(1);
    v.push_back(2);
    v.push_back(3);
    v.push_back(4);
    g.transferHoldLists(0);
    g.trimHoldLists(1);
    MemoryUsage mu;
    mu = v.getMemoryUsage();
    mu.incAllocatedBytesOnHold(g.getHeldBytes());
    EXPECT_TRUE(assertUsage(MemoryUsage(16, 4, 0, 0), mu));
    EXPECT_EQUAL(4u, v.size());
    EXPECT_EQUAL(16u, v.capacity());
    EXPECT_EQUAL(1, v[0]);
    EXPECT_EQUAL(2, v[1]);
    EXPECT_EQUAL(3, v[2]);
    EXPECT_EQUAL(4, v[3]);
    const int8_t *old = &v[0];
    v.shrink(2);
    g.transferHoldLists(1);
    EXPECT_EQUAL(2u, v.size());
    EXPECT_EQUAL(4u, v.capacity());
    EXPECT_EQUAL(1, v[0]);
    EXPECT_EQUAL(2, v[1]);
    EXPECT_EQUAL(1, old[0]);
    EXPECT_EQUAL(2, old[1]);
    g.trimHoldLists(2);
    EXPECT_EQUAL(1, v[0]);
    EXPECT_EQUAL(2, v[1]);
    mu = v.getMemoryUsage();
    mu.incAllocatedBytesOnHold(g.getHeldBytes());
    EXPECT_TRUE(assertUsage(MemoryUsage(4, 2, 0, 0), mu));
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
                      vec(initial_capacity, 50, 0, g, alloc::Alloc::allocMMap()), oldPtr()
    {
        for (size_t i = 0; i < initial_size; ++i) {
            vec.push_back(7);
        }
        EXPECT_EQUAL(initial_size, vec.size());
        EXPECT_EQUAL(initial_capacity, vec.capacity());
        assertEmptyHoldList();
        oldPtr = &vec[0];
    }
    void assertOldEqualNewBuffer() {
        EXPECT_EQUAL(oldPtr, &vec[0]);
    }
    void assertEmptyHoldList() {
        EXPECT_EQUAL(0u, g.getHeldBytes());
    }
    static size_t page_ints() { return round_up_to_page_size(1) / sizeof(int); }
};

TEST_F("require that shrink() does not increase allocated memory", ShrinkFixture)
{
    size_t shrink_size = f.initial_capacity * 2 / 3 + 2;
    f.vec.shrink(shrink_size);
    EXPECT_EQUAL(shrink_size, f.vec.size());
    EXPECT_EQUAL(f.initial_capacity, f.vec.capacity());
    TEST_DO(f.assertOldEqualNewBuffer());
    TEST_DO(f.assertEmptyHoldList());
}

TEST_F("require that shrink() can shrink mmap allocation", ShrinkFixture)
{
    f.vec.shrink(2 * f.page_ints());
    EXPECT_EQUAL(2 * f.page_ints(), f.vec.size());
    EXPECT_EQUAL(3 * f.page_ints(), f.vec.capacity());
    TEST_DO(f.assertOldEqualNewBuffer());
    TEST_DO(f.assertEmptyHoldList());
}

TEST("test small expand")
{
    GenerationHolder g;
    RcuVectorBase<int8_t> v(1, 50, 0, g);
    EXPECT_EQUAL(1u, v.capacity());
    EXPECT_EQUAL(0u, v.size());
    v.push_back(1);
    EXPECT_EQUAL(1u, v.capacity());
    EXPECT_EQUAL(1u, v.size());
    v.push_back(2);
    EXPECT_EQUAL(2u, v.capacity());
    EXPECT_EQUAL(2u, v.size());
    g.transferHoldLists(1);
    g.trimHoldLists(2);
}

struct Fixture {
    using generation_t = GenerationHandler::generation_t;

    AllocStats stats;
    std::unique_ptr<MemoryAllocator> allocator;
    Alloc initial_alloc;
    GenerationHolder g;
    RcuVectorBase<int> arr;

    Fixture();
    ~Fixture();
    void transfer_and_trim(generation_t transfer_gen, generation_t trim_gen)
    {
        g.transferHoldLists(transfer_gen);
        g.trimHoldLists(trim_gen);
    }
};

Fixture::Fixture()
    : stats(),
      allocator(std::make_unique<MyMemoryAllocator>(stats)),
      initial_alloc(Alloc::alloc_with_allocator(allocator.get())),
      g(),
      arr(g, initial_alloc)
{
    arr.reserve(100);
}

Fixture::~Fixture() = default;

TEST_F("require that memory allocator can be set", Fixture)
{
    EXPECT_EQUAL(AllocStats(2, 0), f.stats);
    f.transfer_and_trim(1, 2);
    EXPECT_EQUAL(AllocStats(2, 1), f.stats);
}

TEST_F("require that memory allocator is preserved across reset", Fixture)
{
    f.arr.reset();
    f.arr.reserve(100);
    EXPECT_EQUAL(AllocStats(4, 1), f.stats);
    f.transfer_and_trim(1, 2);
    EXPECT_EQUAL(AllocStats(4, 3), f.stats);
}

TEST_F("require that created replacement vector uses same memory allocator", Fixture)
{
    auto arr2 = f.arr.create_replacement_vector();
    EXPECT_EQUAL(AllocStats(2, 0), f.stats);
    arr2.reserve(100);
    EXPECT_EQUAL(AllocStats(3, 0), f.stats);
    f.transfer_and_trim(1, 2);
    EXPECT_EQUAL(AllocStats(3, 1), f.stats);
}

TEST_F("require that ensure_size and shrink use same memory allocator", Fixture)
{
    f.arr.ensure_size(2000);
    EXPECT_EQUAL(AllocStats(3, 0), f.stats);
    f.arr.shrink(1000);
    EXPECT_EQUAL(AllocStats(4, 0), f.stats);
    f.transfer_and_trim(1, 2);
    EXPECT_EQUAL(AllocStats(4, 3), f.stats);
}

TEST_MAIN() { TEST_RUN_ALL(); }
