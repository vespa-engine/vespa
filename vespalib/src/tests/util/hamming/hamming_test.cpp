// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/binary_hamming_distance.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <cstdlib>
#include <cstring>
#include <vector>

using namespace vespalib;

constexpr size_t ALIGN = 8;
constexpr size_t ALLOC_SZ = 256;

void flip_one_bit(void *memory, const void *other_memory, size_t sz) {
    auto buf = (uint8_t *)memory;
    auto other_buf = (const uint8_t *)other_memory;
    while (true) {
        size_t byte_idx = random() % sz;
        size_t bit_idx = random() % 8;
        uint8_t cmp = other_buf[byte_idx];
        uint8_t old = buf[byte_idx];
        uint8_t bit = 1u << bit_idx;
        if ((old & bit) == (cmp & bit)) {
            uint8_t new_val = old ^ bit;
            REQUIRE(old != new_val);
            buf[byte_idx] = new_val;
            return;
        }
    }
}

std::vector<void *> allocated;

void *my_alloc(int unalignment = 0) {
    void *mem;
    int r = posix_memalign(&mem, ALIGN, ALLOC_SZ);
    REQUIRE_EQ(0, r);
    allocated.push_back(mem);
    uintptr_t addr = (uintptr_t) mem;
    addr += unalignment;
    return (void *)addr;
}

void check_with_flipping(void *mem_a, void *mem_b, size_t sz) {
    memset(mem_a, 0, sz);
    memset(mem_b, 0, sz);
    size_t dist = 0;
    EXPECT_EQ(binary_hamming_distance(mem_a, mem_b, sz), dist);
    while (dist * 2 < sz) {
        flip_one_bit(mem_a, mem_b, sz);
        ++dist;
        EXPECT_EQ(binary_hamming_distance(mem_a, mem_b, sz), dist);
        flip_one_bit(mem_b, mem_a, sz);
        ++dist;
        EXPECT_EQ(binary_hamming_distance(mem_a, mem_b, sz), dist);
    }
}

void check_with_sizes(void *mem_a, void *mem_b) {
    for (size_t sz : { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 16, 32, 63, 64, 65 }) {
        check_with_flipping(mem_a, mem_b, sz);
    }
}    

TEST(BinaryHammingTest, aligned_usage) {
    void *mem_a = my_alloc(0);
    void *mem_b = my_alloc(0);
    check_with_sizes(mem_a, mem_b);
}

TEST(BinaryHammingTest, one_unaligned) {
    void *mem_a = my_alloc(3);
    void *mem_b = my_alloc(0);
    check_with_sizes(mem_a, mem_b);
}

TEST(BinaryHammingTest, other_unaligned) {
    void *mem_a = my_alloc(0);
    void *mem_b = my_alloc(7);
    check_with_sizes(mem_a, mem_b);
}

TEST(BinaryHammingTest, both_unaligned) {
    void *mem_a = my_alloc(2);
    void *mem_b = my_alloc(6);
    check_with_sizes(mem_a, mem_b);
}

int main(int argc, char* argv[]) {
    ::testing::InitGoogleTest(&argc, argv);
    int r = RUN_ALL_TESTS();
    for (void * mem : allocated) {
        free(mem);
    }
    return r;
}
