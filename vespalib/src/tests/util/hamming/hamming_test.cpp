// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/hamming_distance.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <cstdlib>
#include <cstring>

using namespace vespalib;

constexpr size_t ALIGN = 8;
constexpr size_t SZ = 64;

void flip_one_bit(void *memory, void *other) {
    auto buf = (uint8_t *)memory;
    auto cmp = (uint8_t *)other;
    while (true) {
        size_t byte_idx = random() % SZ;
        size_t bit_idx = random() % 8;
        uint8_t lookaside = cmp[byte_idx];
        uint8_t old = buf[byte_idx];
        uint8_t bit = 1u << bit_idx;
        if ((old & bit) == (lookaside & bit)) {
            uint8_t new_val = old ^ bit;
            REQUIRE(old != new_val);
            buf[byte_idx] = new_val;
            return;
        }
    }
}

void *my_alloc(int unalignment = 0) {
    void *mem;
    int r = posix_memalign(&mem, ALIGN, SZ*2);
    REQUIRE_EQ(0, r);
    uintptr_t addr = (uintptr_t) mem;
    addr += unalignment;
    return (void *)addr;
}

void check_with_flipping(void *mem_a, void *mem_b) {
    memset(mem_a, 0, SZ);
    memset(mem_b, 0, SZ);
    size_t dist = 0;
    EXPECT_EQ(binary_hamming_distance(mem_a, mem_b, SZ), dist);
    while (dist < 100) {
        flip_one_bit(mem_a, mem_b);
        ++dist;
        EXPECT_EQ(binary_hamming_distance(mem_a, mem_b, SZ), dist);
        flip_one_bit(mem_b, mem_a);
        ++dist;
        EXPECT_EQ(binary_hamming_distance(mem_a, mem_b, SZ), dist);
    }
}

TEST(BinaryHammingTest, aligned_usage) {
    void *mem_a = my_alloc(0);
    void *mem_b = my_alloc(0);
    check_with_flipping(mem_a, mem_b);
}

TEST(BinaryHammingTest, one_unaligned) {
    void *mem_a = my_alloc(3);
    void *mem_b = my_alloc(0);
    check_with_flipping(mem_a, mem_b);
}

TEST(BinaryHammingTest, other_unaligned) {
    void *mem_a = my_alloc(0);
    void *mem_b = my_alloc(7);
    check_with_flipping(mem_a, mem_b);
}

TEST(BinaryHammingTest, both_unaligned) {
    void *mem_a = my_alloc(2);
    void *mem_b = my_alloc(6);
    check_with_flipping(mem_a, mem_b);
}

GTEST_MAIN_RUN_ALL_TESTS()
