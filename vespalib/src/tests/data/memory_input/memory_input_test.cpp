// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/data/memory_input.h>

using namespace vespalib;

TEST(MemoryInputTest, require_that_MemoryInput_wrapper_works_as_expected) {
    const char *data = "1234567890";
    Memory memory(data);
    EXPECT_EQ(memory.size, 10u);
    MemoryInput input(memory);
    EXPECT_EQ(input.obtain(), memory);
    input.evict(5);
    EXPECT_EQ(input.obtain(), Memory(data + 5));    
    EXPECT_EQ(input.obtain(), Memory(data + 5));    
    input.evict(5);
    EXPECT_EQ(input.obtain(), Memory());
}

GTEST_MAIN_RUN_ALL_TESTS()
