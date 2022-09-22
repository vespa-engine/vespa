// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
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
        ASSERT_TRUE(alive);
        ASSERT_TRUE(check1 == 0x1111);
        ASSERT_TRUE(check2 == 0x2222);
        ASSERT_TRUE(check3 == 0x5555);
        alive = false;
        check1 = 0;
        check2 = 0;
        check3 = 0;
        ++destructed;
    }
};

typedef Object<8>     SmallObject;
typedef Object<10000> LargeObject;

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

TEST("require that base types have expected size") {
    EXPECT_EQUAL(8u, char_ptr_size());
    EXPECT_EQUAL(16u, chunk_header_size());
    EXPECT_EQUAL(16u, dtor_hook_size());
    EXPECT_EQUAL(24u, free_hook_size());
    EXPECT_EQUAL(24u, array_dtor_hook_size());
}

TEST("require that raw memory can be allocated inside the stash") {
    Stash stash;
    EXPECT_EQUAL(0u, stash.count_used());
    char *mem1 = stash.alloc(512);
    EXPECT_EQUAL(sum({chunk_header_size(), 512}), stash.count_used());
    char *mem2 = stash.alloc(512);
    EXPECT_EQUAL(sum({chunk_header_size(), 512, 512}), stash.count_used());
    char *mem3 = stash.alloc(512);
    EXPECT_EQUAL(sum({chunk_header_size(), 512, 512, 512}), stash.count_used());
    EXPECT_TRUE(mem1 + 512 == mem2);
    EXPECT_TRUE(mem2 + 512 == mem3);
}

TEST("require that raw memory can be allocated outside the stash") {
    Stash stash;
    EXPECT_EQUAL(0u, stash.count_used());
    EXPECT_TRUE(stash.alloc(10000) != nullptr);
    EXPECT_EQUAL(0u, stash.count_used());
    EXPECT_TRUE(stash.alloc(10000) != nullptr);
    EXPECT_EQUAL(0u, stash.count_used());
}

TEST("require that allocations are aligned to pointer size") {
    Stash stash;
    EXPECT_EQUAL(0u, stash.count_used());
    char *mem1 = stash.alloc(1);
    EXPECT_EQUAL(sum({chunk_header_size(), char_ptr_size()}), stash.count_used());
    char *mem2 = stash.alloc(char_ptr_size() - 1);
    EXPECT_EQUAL(sum({chunk_header_size(), char_ptr_size(), char_ptr_size()}), stash.count_used());
    char *mem3 = stash.alloc(char_ptr_size());
    EXPECT_EQUAL(sum({chunk_header_size(), char_ptr_size(), char_ptr_size(), char_ptr_size()}), stash.count_used());
    EXPECT_TRUE(mem1 + char_ptr_size() == mem2);
    EXPECT_TRUE(mem2 + char_ptr_size() == mem3);
}

TEST("require that valid empty memory may be allocated") {
    Stash stash;
    EXPECT_EQUAL(0u, stash.count_used());
    char *mem1 = stash.alloc(0);
    EXPECT_EQUAL(sum({chunk_header_size()}), stash.count_used());
    char *mem2 = stash.alloc(0);
    EXPECT_EQUAL(sum({chunk_header_size()}), stash.count_used());
    char *mem3 = stash.alloc(char_ptr_size());
    EXPECT_EQUAL(sum({chunk_header_size(), char_ptr_size()}), stash.count_used());
    char *mem4 = stash.alloc(0);
    EXPECT_EQUAL(sum({chunk_header_size(), char_ptr_size()}), stash.count_used());
    EXPECT_TRUE(mem1 == mem2);
    EXPECT_TRUE(mem2 == mem3);
    EXPECT_TRUE(mem3 + char_ptr_size() == mem4);
}

TEST("require that small object creation and destruction works") {
    size_t destructed = 0;
    {
        Stash stash;
        stash.create<Small>(destructed);
        EXPECT_EQUAL(sum({chunk_header_size(), dtor_hook_size(), sizeof(Small)}), stash.count_used());
        EXPECT_FALSE(destructed);
    }
    EXPECT_TRUE(destructed);
}

TEST("require that large object creation and destruction works") {
    size_t destructed = 0;
    {
        Stash stash;
        stash.create<Large>(destructed);
        EXPECT_EQUAL(0u, stash.count_used());
        EXPECT_GREATER(sizeof(Large), 1_Ki);
        EXPECT_FALSE(destructed);
    }
    EXPECT_TRUE(destructed);
}

TEST("require that small objects can skip destruction") {
    size_t destructed = 0;
    {
        Stash stash;
        stash.create<Small_NoDelete>(destructed);
        EXPECT_EQUAL(sum({chunk_header_size(), sizeof(Small_NoDelete)}), stash.count_used());
    }
    EXPECT_FALSE(destructed);
}

TEST("require that large objects can skip destruction") {
    size_t destructed = 0;
    {
        Stash stash;
        stash.create<Large_NoDelete>(destructed);
        EXPECT_EQUAL(0u, stash.count_used());
        EXPECT_GREATER(sizeof(Large_NoDelete), 1_Ki);
    }
    EXPECT_FALSE(destructed);
}

TEST("require that constructor parameters are passed correctly") {
    Stash stash;
    {
        PairD &pair = stash.create<PairD>();
        Pair &pair_nodelete = stash.create<Pair>();
        EXPECT_EQUAL(pair.a, pair_nodelete.a);
        EXPECT_EQUAL(pair.b, pair_nodelete.b);
        EXPECT_EQUAL(42, pair.a);
        EXPECT_EQUAL(4.2, pair.b);
    }
    {
        PairD &pair = stash.create<PairD>(50, 100.5);
        Pair &pair_nodelete = stash.create<Pair>(50, 100.5);
        EXPECT_EQUAL(pair.a, pair_nodelete.a);
        EXPECT_EQUAL(pair.b, pair_nodelete.b);
        EXPECT_EQUAL(50, pair.a);
        EXPECT_EQUAL(100.5, pair.b);
    }
}

TEST("require that trivially destructable objects are detected") {
    Stash stash;
    EXPECT_TRUE(can_skip_destruction<Pair>);
    EXPECT_FALSE(can_skip_destruction<PairD>);
    stash.create<Pair>();
    EXPECT_EQUAL(sum({chunk_header_size(), sizeof(Pair)}), stash.count_used());
    stash.create<PairD>();
    EXPECT_EQUAL(sum({chunk_header_size(), sizeof(Pair), dtor_hook_size(), sizeof(PairD)}), stash.count_used());    
}

TEST("require that multiple chunks can be used by the stash") {
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
    EXPECT_EQUAL(100 * 512 + count * chunk_header_size(), stash.count_used());
}

TEST("require that default chunk size is 4096") {
    Stash stash;
    EXPECT_EQUAL(4_Ki, stash.get_chunk_size());
}

TEST("require that the chunk size can be adjusted") {
    Stash stash(64000);
    EXPECT_EQUAL(64000u, stash.get_chunk_size());
}

TEST("require that minimal chunk size is 128") {
    Stash stash(50);
    EXPECT_EQUAL(128u, stash.get_chunk_size());
}

TEST("require that a stash can be moved by construction") {
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

TEST("require that a stash can be moved by assignment") {
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

TEST("require that an empty stash can be cleared") {
    Stash stash;
    EXPECT_EQUAL(0u, stash.count_used());
    stash.clear();
    EXPECT_EQUAL(0u, stash.count_used());
}

TEST("require that a stash retains memory when cleared") {
    size_t destructed = 0;
    {
        Stash stash;
        stash.create<Small>(destructed);
        EXPECT_EQUAL(sum({chunk_header_size(), dtor_hook_size(), sizeof(Small)}), stash.count_used());
        EXPECT_FALSE(destructed);
        stash.clear();
        EXPECT_EQUAL(sum({chunk_header_size()}), stash.count_used());
        EXPECT_TRUE(destructed);
    }
}

TEST("require that a stash only retains a single chunk when cleared") {
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
    EXPECT_EQUAL(100 * 512 + count * chunk_header_size(), stash.count_used());
    stash.clear();
    EXPECT_EQUAL(sum({chunk_header_size()}), stash.count_used());    
}

TEST("require that array constructor parameters are passed correctly") {
    Stash stash;
    {
        ArrayRef<Pair> pair_array_nodelete = stash.create_array<Pair>(3);
        ArrayRef<PairD> pair_array = stash.create_array<PairD>(3);
        ASSERT_EQUAL(pair_array_nodelete.size(), 3u);
        ASSERT_EQUAL(pair_array.size(), 3u);
        for (size_t i = 0; i < 3; ++i) {
            ASSERT_EQUAL(pair_array_nodelete[i].a, 42);
            ASSERT_EQUAL(pair_array_nodelete[i].b, 4.2);
            ASSERT_EQUAL(pair_array[i].a, 42);
            ASSERT_EQUAL(pair_array[i].b, 4.2);
        }
    }
    {
        ArrayRef<Pair> pair_array_nodelete = stash.create_array<Pair>(3,50,100.5);
        ArrayRef<PairD> pair_array = stash.create_array<PairD>(3,50,100.5);
        ASSERT_EQUAL(pair_array_nodelete.size(), 3u);
        ASSERT_EQUAL(pair_array.size(), 3u);
        for (size_t i = 0; i < 3; ++i) {
            ASSERT_EQUAL(pair_array_nodelete[i].a, 50);
            ASSERT_EQUAL(pair_array_nodelete[i].b, 100.5);
            ASSERT_EQUAL(pair_array[i].a, 50);
            ASSERT_EQUAL(pair_array[i].b, 100.5);
        }
    }
}

TEST("require that arrays can be copied into the stash") {    
    Stash stash;
    std::vector<Pair> pair_vector({Pair(1,1.5),Pair(2,2.5),Pair(3,3.5)});
    std::vector<PairD> paird_vector({PairD(1,1.5),PairD(2,2.5),PairD(3,3.5)});
    ArrayRef<Pair> pair_array_nodelete = stash.copy_array<Pair>(ConstArrayRef<Pair>(pair_vector));
    ArrayRef<PairD> pair_array = stash.copy_array<PairD>(ConstArrayRef<PairD>(paird_vector));
    ASSERT_EQUAL(pair_array_nodelete.size(), 3u);
    ASSERT_EQUAL(pair_array.size(), 3u);
    for (int i = 0; i < 3; ++i) {
        ASSERT_EQUAL(pair_array_nodelete[i].a, i + 1);
        ASSERT_EQUAL(pair_array_nodelete[i].b, i + 1.5);
        ASSERT_EQUAL(pair_array[i].a, i + 1);
        ASSERT_EQUAL(pair_array[i].b, i + 1.5);
    }
}

TEST("require that created arrays are destructed (or not) correctly") {
    size_t destruct = 0;
    size_t destruct_nodelete = 0;
    {
        Stash stash;
        stash.create_array<Small>(5,destruct);
        EXPECT_EQUAL(sum({chunk_header_size(), array_dtor_hook_size(), 5 * sizeof(Small)}), stash.count_used());
        stash.create_array<Small_NoDelete>(7,destruct_nodelete);
        EXPECT_EQUAL(sum({chunk_header_size(), array_dtor_hook_size(), 5 * sizeof(Small), 7 * sizeof(Small_NoDelete)}), stash.count_used());
        EXPECT_EQUAL(0u, destruct);
        EXPECT_EQUAL(0u, destruct_nodelete);
    }
    EXPECT_EQUAL(5u, destruct);
    EXPECT_EQUAL(0u, destruct_nodelete);
}

TEST("require that copied arrays are destructed (or not) correctly") {
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
            stash.copy_array<Small>(ConstArrayRef<Small>(small_vector));
            EXPECT_EQUAL(sum({chunk_header_size(), array_dtor_hook_size(), 5 * sizeof(Small)}), stash.count_used());
            stash.copy_array<Small_NoDelete>(ConstArrayRef<Small_NoDelete>(small_nodelete_vector));
            EXPECT_EQUAL(sum({chunk_header_size(), array_dtor_hook_size(), 5 * sizeof(Small), 7 * sizeof(Small_NoDelete)}), stash.count_used());
            EXPECT_EQUAL(collateral_destruct, destruct);
            EXPECT_EQUAL(collateral_destruct_nodelete, destruct_nodelete);
        }
        EXPECT_EQUAL(collateral_destruct + 5, destruct);
        EXPECT_EQUAL(collateral_destruct_nodelete, destruct_nodelete);
    }
    EXPECT_EQUAL(collateral_destruct + 5 + 5, destruct);
    EXPECT_EQUAL(collateral_destruct_nodelete + 7, destruct_nodelete);
}

TEST("require that mark/revert works as expected") {
    Stash stash;
    EXPECT_EQUAL(stash.count_used(), 0u);
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
    EXPECT_EQUAL(stash.count_used(), used_after);
    EXPECT_EQUAL(destruct_small, 0u);
    EXPECT_EQUAL(destruct_large, 0u);
    stash.revert(between);
    EXPECT_EQUAL(stash.count_used(), used_between);
    EXPECT_EQUAL(destruct_small, 42u);
    EXPECT_EQUAL(destruct_large, 1u);
    Stash::Mark empty;
    stash.revert(empty);
    EXPECT_EQUAL(destruct_small, 100u);
    EXPECT_EQUAL(destruct_large, 2u);
    EXPECT_EQUAL(stash.count_used(), 0u);
}

void check_array(ArrayRef<float> arr, size_t expect_size) {
    EXPECT_EQUAL(arr.size(), expect_size);
    for (size_t i = 0; i < arr.size(); ++i) {
        arr[i] = float(i);
    }
    for (size_t i = 0; i < arr.size(); ++i) {
        EXPECT_EQUAL(arr[i], float(i));
    }
}

TEST("require that uninitialized arrays can be created") {
    Stash stash(4_Ki);
    EXPECT_EQUAL(0u, stash.count_used());
    ArrayRef<float> small_arr = stash.create_uninitialized_array<float>(64);
    TEST_DO(check_array(small_arr, 64));
    EXPECT_EQUAL(sum({chunk_header_size(), sizeof(float) * 64}), stash.count_used());
    ArrayRef<float> big_arr = stash.create_uninitialized_array<float>(2500);
    TEST_DO(check_array(big_arr, 2500));
    EXPECT_EQUAL(sum({chunk_header_size(), sizeof(float) * 64}), stash.count_used());
}

TEST_MAIN() { TEST_RUN_ALL(); }
