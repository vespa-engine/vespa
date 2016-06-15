// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
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
    bool &destructed;
    char bloat[fill_size];
    explicit Object(bool &dref)
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
        destructed = true;
    }
};

typedef Object<8>     SmallObject;
typedef Object<10000> LargeObject;

struct Small : SmallObject {
    Small(bool &dref) : SmallObject(dref) {}
};

struct Large : LargeObject {
    Large(bool &dref) : LargeObject(dref) {}
};

struct Small_NoDelete : SmallObject {
    Small_NoDelete(bool &dref) : SmallObject(dref) {}
};

struct Large_NoDelete : LargeObject {
    Large_NoDelete(bool &dref) : LargeObject(dref) {}
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

//-----------------------------------------------------------------------------

TEST("require that base types have expected size") {
    EXPECT_EQUAL(8u, char_ptr_size());
    EXPECT_EQUAL(16u, chunk_header_size());
    EXPECT_EQUAL(16u, dtor_hook_size());
    EXPECT_EQUAL(16u, free_hook_size());
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
    bool destructed = false;
    {
        Stash stash;
        stash.create<Small>(destructed);
        EXPECT_EQUAL(sum({chunk_header_size(), dtor_hook_size(), sizeof(Small)}), stash.count_used());
        EXPECT_FALSE(destructed);
    }
    EXPECT_TRUE(destructed);
}

TEST("require that large object creation and destruction works") {
    bool destructed = false;
    {
        Stash stash;
        stash.create<Large>(destructed);
        EXPECT_EQUAL(0u, stash.count_used());
        EXPECT_GREATER(sizeof(Large), 1024u);
        EXPECT_FALSE(destructed);
    }
    EXPECT_TRUE(destructed);
}

TEST("require that small objects can skip destruction") {
    bool destructed = false;
    {
        Stash stash;
        stash.create<Small_NoDelete>(destructed);
        EXPECT_EQUAL(sum({chunk_header_size(), sizeof(Small_NoDelete)}), stash.count_used());
    }
    EXPECT_FALSE(destructed);
}

TEST("require that large objects can skip destruction") {
    bool destructed = false;
    {
        Stash stash;
        stash.create<Large_NoDelete>(destructed);
        EXPECT_EQUAL(0u, stash.count_used());
        EXPECT_GREATER(sizeof(Large_NoDelete), 1024u);
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
    EXPECT_TRUE(can_skip_destruction<Pair>::value);
    EXPECT_FALSE(can_skip_destruction<PairD>::value);
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
    EXPECT_EQUAL(4096u, stash.get_chunk_size());
}

TEST("require that the chunk size can be adjusted") {
    Stash stash(64000);
    EXPECT_EQUAL(64000u, stash.get_chunk_size());
}

TEST("require that minimal chunk size is 4096") {
    Stash stash(128);
    EXPECT_EQUAL(4096u, stash.get_chunk_size());
}

TEST("require that a stash can be moved by construction") {
    bool destructed = false;
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
    bool destructed = false;
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
    bool destructed = false;
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

TEST_MAIN() { TEST_RUN_ALL(); }
