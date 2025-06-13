// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/traits.h>

using namespace vespalib;

//-----------------------------------------------------------------------------

template <size_t fill_size>
struct Object {
    bool alive;
    int check1;
    int check2;
    int check3;
    size_t &destructed;
    char bloat[fill_size];
    explicit Object(size_t &dref)
        : alive(true), check1(0x1111), check2(0x2222), check3(0x5555),
          destructed(dref), bloat()
    {
        for (size_t i = 0; i < fill_size; ++i) {
            bloat[i] = 0xee;
        }
    }
    ~Object() {
        EXPECT_TRUE(alive);
        EXPECT_TRUE(check1 == 0x1111);
        EXPECT_TRUE(check2 == 0x2222);
        EXPECT_TRUE(check3 == 0x5555);
        alive = false;
        check1 = 0;
        check2 = 0;
        check3 = 0;
        ++destructed;
    }
};

using SmallObject = Object<8>;
using LargeObject = Object<10000>;

struct Small : SmallObject {
    Small(size_t &dref) : SmallObject(dref) {}
};

struct Large : LargeObject {
    Large(size_t &dref) : LargeObject(dref) {}
};

struct Small_NoDelete : SmallObject {
    Small_NoDelete(size_t &dref) : SmallObject(dref) {}
};

struct Large_NoDelete : LargeObject {
    Large_NoDelete(size_t &dref) : LargeObject(dref) {}
};

VESPA_CAN_SKIP_DESTRUCTION(Small_NoDelete);
VESPA_CAN_SKIP_DESTRUCTION(Large_NoDelete);

//-----------------------------------------------------------------------------

struct Pair {
    int a;
    double b;
    Pair() : a(42), b(4.2) {}
    Pair(int a_in, double b_in) : a(a_in), b(b_in) {}
};

struct PairD {
    int a;
    double b;
    PairD() : a(42), b(4.2) {}
    PairD(int a_in, double b_in) : a(a_in), b(b_in) {}
    ~PairD() {}
};

//-----------------------------------------------------------------------------

size_t sum(std::initializer_list<size_t> list) {
    size_t ret = 0;
    for (auto i: list) {
        ret += i;
    }
    return ret;
}

//-----------------------------------------------------------------------------

size_t char_ptr_size() { return sizeof(char*); }
size_t chunk_header_size() { return sizeof(stash::Chunk); }
size_t dtor_hook_size() { return sizeof(stash::DestructObject<Small>); }
size_t free_hook_size() { return sizeof(stash::DeleteMemory); }
size_t array_dtor_hook_size() { return sizeof(stash::DestructArray<Small>); }

//-----------------------------------------------------------------------------

TEST(StashTest, require_that_base_types_have_expected_size) {
    EXPECT_EQ(8u, char_ptr_size());
    EXPECT_EQ(16u, chunk_header_size());
    EXPECT_EQ(16u, dtor_hook_size());
    EXPECT_EQ(24u, free_hook_size());
    EXPECT_EQ(24u, array_dtor_hook_size());
}

TEST(StashTest, require_that_raw_memory_can_be_allocated_inside_the_stash) {
    Stash stash;
    EXPECT_EQ(0u, stash.count_used());
    char *mem1 = stash.alloc(512);
    EXPECT_EQ(sum({chunk_header_size(), 512}), stash.count_used());
    char *mem2 = stash.alloc(512);
    EXPECT_EQ(sum({chunk_header_size(), 512, 512}), stash.count_used());
    char *mem3 = stash.alloc(512);
    EXPECT_EQ(sum({chunk_header_size(), 512, 512, 512}), stash.count_used());
    EXPECT_TRUE(mem1 + 512 == mem2);
    EXPECT_TRUE(mem2 + 512 == mem3);
}

TEST(StashTest, require_that_raw_memory_can_be_allocated_outside_the_stash) {
    Stash stash;
    EXPECT_EQ(0u, stash.count_used());
    EXPECT_TRUE(stash.alloc(10000) != nullptr);
    EXPECT_EQ(0u, stash.count_used());
    EXPECT_TRUE(stash.alloc(10000) != nullptr);
    EXPECT_EQ(0u, stash.count_used());
}

TEST(StashTest, require_that_allocations_are_aligned_to_pointer_size) {
    Stash stash;
    EXPECT_EQ(0u, stash.count_used());
    char *mem1 = stash.alloc(1);
    EXPECT_EQ(sum({chunk_header_size(), char_ptr_size()}), stash.count_used());
    char *mem2 = stash.alloc(char_ptr_size() - 1);
    EXPECT_EQ(sum({chunk_header_size(), char_ptr_size(), char_ptr_size()}), stash.count_used());
    char *mem3 = stash.alloc(char_ptr_size());
    EXPECT_EQ(sum({chunk_header_size(), char_ptr_size(), char_ptr_size(), char_ptr_size()}), stash.count_used());
    EXPECT_TRUE(mem1 + char_ptr_size() == mem2);
    EXPECT_TRUE(mem2 + char_ptr_size() == mem3);
}

TEST(StashTest, require_that_valid_empty_memory_may_be_allocated) {
    Stash stash;
    EXPECT_EQ(0u, stash.count_used());
    char *mem1 = stash.alloc(0);
    EXPECT_EQ(sum({chunk_header_size()}), stash.count_used());
    char *mem2 = stash.alloc(0);
    EXPECT_EQ(sum({chunk_header_size()}), stash.count_used());
    char *mem3 = stash.alloc(char_ptr_size());
    EXPECT_EQ(sum({chunk_header_size(), char_ptr_size()}), stash.count_used());
    char *mem4 = stash.alloc(0);
    EXPECT_EQ(sum({chunk_header_size(), char_ptr_size()}), stash.count_used());
    EXPECT_TRUE(mem1 == mem2);
    EXPECT_TRUE(mem2 == mem3);
    EXPECT_TRUE(mem3 + char_ptr_size() == mem4);
}

TEST(StashTest, require_that_small_object_creation_and_destruction_works) {
    size_t destructed = 0;
    {
        Stash stash;
        stash.create<Small>(destructed);
        EXPECT_EQ(sum({chunk_header_size(), dtor_hook_size(), sizeof(Small)}), stash.count_used());
        EXPECT_FALSE(destructed);
    }
    EXPECT_TRUE(destructed);
}

TEST(StashTest, require_that_large_object_creation_and_destruction_works) {
    size_t destructed = 0;
    {
        Stash stash;
        stash.create<Large>(destructed);
        EXPECT_EQ(0u, stash.count_used());
        EXPECT_GT(sizeof(Large), 1_Ki);
        EXPECT_FALSE(destructed);
    }
    EXPECT_TRUE(destructed);
}

TEST(StashTest, require_that_small_objects_can_skip_destruction) {
    size_t destructed = 0;
    {
        Stash stash;
        stash.create<Small_NoDelete>(destructed);
        EXPECT_EQ(sum({chunk_header_size(), sizeof(Small_NoDelete)}), stash.count_used());
    }
    EXPECT_FALSE(destructed);
}

TEST(StashTest, require_that_large_objects_can_skip_destruction) {
    size_t destructed = 0;
    {
        Stash stash;
        stash.create<Large_NoDelete>(destructed);
        EXPECT_EQ(0u, stash.count_used());
        EXPECT_GT(sizeof(Large_NoDelete), 1_Ki);
    }
    EXPECT_FALSE(destructed);
}

TEST(StashTest, require_that_constructor_parameters_are_passed_correctly) {
    Stash stash;
    {
        PairD &pair = stash.create<PairD>();
        Pair &pair_nodelete = stash.create<Pair>();
        EXPECT_EQ(pair.a, pair_nodelete.a);
        EXPECT_EQ(pair.b, pair_nodelete.b);
        EXPECT_EQ(42, pair.a);
        EXPECT_EQ(4.2, pair.b);
    }
    {
        PairD &pair = stash.create<PairD>(50, 100.5);
        Pair &pair_nodelete = stash.create<Pair>(50, 100.5);
        EXPECT_EQ(pair.a, pair_nodelete.a);
        EXPECT_EQ(pair.b, pair_nodelete.b);
        EXPECT_EQ(50, pair.a);
        EXPECT_EQ(100.5, pair.b);
    }
}

TEST(StashTest, require_that_trivially_destructable_objects_are_detected) {
    Stash stash;
    EXPECT_TRUE(can_skip_destruction<Pair>);
    EXPECT_FALSE(can_skip_destruction<PairD>);
    stash.create<Pair>();
    EXPECT_EQ(sum({chunk_header_size(), sizeof(Pair)}), stash.count_used());
    stash.create<PairD>();
    EXPECT_EQ(sum({chunk_header_size(), sizeof(Pair), dtor_hook_size(), sizeof(PairD)}), stash.count_used());    
}

TEST(StashTest, require_that_multiple_chunks_can_be_used_by_the_stash) {
    Stash stash;
    char *prev = nullptr;
    size_t count = 0;
    for (size_t i = 0; i < 100; ++i) {
        char *ptr = stash.alloc(512);
        if (prev == nullptr || (prev + 512) != ptr) {
            ++count;
        }
        prev = ptr;
    }
    EXPECT_TRUE(count > 10);
    EXPECT_EQ(100 * 512 + count * chunk_header_size(), stash.count_used());
}

TEST(StashTest, require_that_default_chunk_size_is_4096) {
    Stash stash;
    EXPECT_EQ(4_Ki, stash.get_chunk_size());
}

TEST(StashTest, require_that_the_chunk_size_can_be_adjusted) {
    Stash stash(64000);
    EXPECT_EQ(64000u, stash.get_chunk_size());
}

TEST(StashTest, require_that_minimal_chunk_size_is_128) {
    Stash stash(50);
    EXPECT_EQ(128u, stash.get_chunk_size());
}

TEST(StashTest, require_that_a_stash_can_be_moved_by_construction) {
    size_t destructed = 0;
    {
        Stash outer_stash;
        outer_stash.create<Small>(destructed);
        {
            EXPECT_TRUE(outer_stash.count_used() > 0);
            Stash inner_stash(std::move(outer_stash));
            EXPECT_TRUE(inner_stash.count_used() > 0);
            EXPECT_TRUE(outer_stash.count_used() == 0);
            EXPECT_FALSE(destructed);
        }
        EXPECT_TRUE(destructed);
    }
}

TEST(StashTest, require_that_a_stash_can_be_moved_by_assignment) {
    size_t destructed = 0;
    {
        Stash outer_stash;
        outer_stash.create<Small>(destructed);
        {
            EXPECT_TRUE(outer_stash.count_used() > 0);
            Stash inner_stash;
            EXPECT_TRUE(inner_stash.count_used() == 0);
            inner_stash = std::move(outer_stash);
            EXPECT_TRUE(inner_stash.count_used() > 0);
            EXPECT_TRUE(outer_stash.count_used() == 0);
            EXPECT_FALSE(destructed);
        }
        EXPECT_TRUE(destructed);
    }
}

TEST(StashTest, require_that_an_empty_stash_can_be_cleared) {
    Stash stash;
    EXPECT_EQ(0u, stash.count_used());
    stash.clear();
    EXPECT_EQ(0u, stash.count_used());
}

TEST(StashTest, require_that_a_stash_retains_memory_when_cleared) {
    size_t destructed = 0;
    {
        Stash stash;
        stash.create<Small>(destructed);
        EXPECT_EQ(sum({chunk_header_size(), dtor_hook_size(), sizeof(Small)}), stash.count_used());
        EXPECT_FALSE(destructed);
        stash.clear();
        EXPECT_EQ(sum({chunk_header_size()}), stash.count_used());
        EXPECT_TRUE(destructed);
    }
}

TEST(StashTest, require_that_a_stash_only_retains_a_single_chunk_when_cleared) {
    Stash stash;
    char *prev = nullptr;
    size_t count = 0;
    for (size_t i = 0; i < 100; ++i) {
        char *ptr = stash.alloc(512);
        if (prev == nullptr || (prev + 512) != ptr) {
            ++count;
        }
        prev = ptr;
    }
    EXPECT_TRUE(count > 10);
    EXPECT_EQ(100 * 512 + count * chunk_header_size(), stash.count_used());
    stash.clear();
    EXPECT_EQ(sum({chunk_header_size()}), stash.count_used());    
}

TEST(StashTest, require_that_array_constructor_parameters_are_passed_correctly) {
    Stash stash;
    {
        std::span<Pair> pair_array_nodelete = stash.create_array<Pair>(3);
        std::span<PairD> pair_array = stash.create_array<PairD>(3);
        ASSERT_EQ(pair_array_nodelete.size(), 3u);
        ASSERT_EQ(pair_array.size(), 3u);
        for (size_t i = 0; i < 3; ++i) {
            ASSERT_EQ(pair_array_nodelete[i].a, 42);
            ASSERT_EQ(pair_array_nodelete[i].b, 4.2);
            ASSERT_EQ(pair_array[i].a, 42);
            ASSERT_EQ(pair_array[i].b, 4.2);
        }
    }
    {
        std::span<Pair> pair_array_nodelete = stash.create_array<Pair>(3,50,100.5);
        std::span<PairD> pair_array = stash.create_array<PairD>(3,50,100.5);
        ASSERT_EQ(pair_array_nodelete.size(), 3u);
        ASSERT_EQ(pair_array.size(), 3u);
        for (size_t i = 0; i < 3; ++i) {
            ASSERT_EQ(pair_array_nodelete[i].a, 50);
            ASSERT_EQ(pair_array_nodelete[i].b, 100.5);
            ASSERT_EQ(pair_array[i].a, 50);
            ASSERT_EQ(pair_array[i].b, 100.5);
        }
    }
}

TEST(StashTest, require_that_arrays_can_be_copied_into_the_stash) {    
    Stash stash;
    std::vector<Pair> pair_vector({Pair(1,1.5),Pair(2,2.5),Pair(3,3.5)});
    std::vector<PairD> paird_vector({PairD(1,1.5),PairD(2,2.5),PairD(3,3.5)});
    std::span<Pair> pair_array_nodelete = stash.copy_array<Pair>(std::span<const Pair>(pair_vector));
    std::span<PairD> pair_array = stash.copy_array<PairD>(std::span<const PairD>(paird_vector));
    ASSERT_EQ(pair_array_nodelete.size(), 3u);
    ASSERT_EQ(pair_array.size(), 3u);
    for (int i = 0; i < 3; ++i) {
        ASSERT_EQ(pair_array_nodelete[i].a, i + 1);
        ASSERT_EQ(pair_array_nodelete[i].b, i + 1.5);
        ASSERT_EQ(pair_array[i].a, i + 1);
        ASSERT_EQ(pair_array[i].b, i + 1.5);
    }
}

TEST(StashTest, require_that_created_arrays_are_destructed__or_not__correctly) {
    size_t destruct = 0;
    size_t destruct_nodelete = 0;
    {
        Stash stash;
        stash.create_array<Small>(5,destruct);
        EXPECT_EQ(sum({chunk_header_size(), array_dtor_hook_size(), 5 * sizeof(Small)}), stash.count_used());
        stash.create_array<Small_NoDelete>(7,destruct_nodelete);
        EXPECT_EQ(sum({chunk_header_size(), array_dtor_hook_size(), 5 * sizeof(Small), 7 * sizeof(Small_NoDelete)}), stash.count_used());
        EXPECT_EQ(0u, destruct);
        EXPECT_EQ(0u, destruct_nodelete);
    }
    EXPECT_EQ(5u, destruct);
    EXPECT_EQ(0u, destruct_nodelete);
}

TEST(StashTest, require_that_copied_arrays_are_destructed__or_not__correctly) {
    size_t destruct = 0;
    size_t destruct_nodelete = 0;
    size_t collateral_destruct = 0;
    size_t collateral_destruct_nodelete = 0;
    {
        std::vector<Small> small_vector(5, Small(destruct));
        std::vector<Small_NoDelete> small_nodelete_vector(7, Small_NoDelete(destruct_nodelete));
        collateral_destruct = destruct;
        collateral_destruct_nodelete = destruct_nodelete;
        {
            Stash stash;
            stash.copy_array<Small>(std::span<const Small>(small_vector));
            EXPECT_EQ(sum({chunk_header_size(), array_dtor_hook_size(), 5 * sizeof(Small)}), stash.count_used());
            stash.copy_array<Small_NoDelete>(std::span<const Small_NoDelete>(small_nodelete_vector));
            EXPECT_EQ(sum({chunk_header_size(), array_dtor_hook_size(), 5 * sizeof(Small), 7 * sizeof(Small_NoDelete)}), stash.count_used());
            EXPECT_EQ(collateral_destruct, destruct);
            EXPECT_EQ(collateral_destruct_nodelete, destruct_nodelete);
        }
        EXPECT_EQ(collateral_destruct + 5, destruct);
        EXPECT_EQ(collateral_destruct_nodelete, destruct_nodelete);
    }
    EXPECT_EQ(collateral_destruct + 5 + 5, destruct);
    EXPECT_EQ(collateral_destruct_nodelete + 7, destruct_nodelete);
}

TEST(StashTest, require_that_mark_revert_works_as_expected) {
    Stash stash;
    EXPECT_EQ(stash.count_used(), 0u);
    size_t destruct_small = 0;
    size_t destruct_large = 0;
    size_t used_between = 0;
    Stash::Mark between;
    stash.create<Large>(destruct_large);
    for (size_t i = 0; i < 100; ++i) {
        if (i == 58) {
            used_between = stash.count_used();
            between = stash.mark();
        }
        stash.alloc(512);
        stash.create<Small>(destruct_small);
    }
    stash.create<Large>(destruct_large);
    size_t used_after = stash.count_used();
    Stash::Mark after = stash.mark();
    stash.revert(after);
    EXPECT_EQ(stash.count_used(), used_after);
    EXPECT_EQ(destruct_small, 0u);
    EXPECT_EQ(destruct_large, 0u);
    stash.revert(between);
    EXPECT_EQ(stash.count_used(), used_between);
    EXPECT_EQ(destruct_small, 42u);
    EXPECT_EQ(destruct_large, 1u);
    Stash::Mark empty;
    stash.revert(empty);
    EXPECT_EQ(destruct_small, 100u);
    EXPECT_EQ(destruct_large, 2u);
    EXPECT_EQ(stash.count_used(), 0u);
}

void check_array(std::span<float> arr, size_t expect_size) {
    EXPECT_EQ(arr.size(), expect_size);
    for (size_t i = 0; i < arr.size(); ++i) {
        arr[i] = float(i);
    }
    for (size_t i = 0; i < arr.size(); ++i) {
        EXPECT_EQ(arr[i], float(i));
    }
}

TEST(StashTest, require_that_uninitialized_arrays_can_be_created) {
    Stash stash(4_Ki);
    EXPECT_EQ(0u, stash.count_used());
    std::span<float> small_arr = stash.create_uninitialized_array<float>(64);
    GTEST_DO(check_array(small_arr, 64));
    EXPECT_EQ(sum({chunk_header_size(), sizeof(float) * 64}), stash.count_used());
    std::span<float> big_arr = stash.create_uninitialized_array<float>(2500);
    GTEST_DO(check_array(big_arr, 2500));
    EXPECT_EQ(sum({chunk_header_size(), sizeof(float) * 64}), stash.count_used());
}

GTEST_MAIN_RUN_ALL_TESTS()
