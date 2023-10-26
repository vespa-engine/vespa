// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/data/memory_input.h>

using namespace vespalib;

TEST("require that MemoryInput wrapper works as expected") {
    const char *data = "1234567890";
    Memory memory(data);
    EXPECT_EQUAL(memory.size, 10u);
    MemoryInput input(memory);
    EXPECT_EQUAL(input.obtain(), memory);
    input.evict(5);
    EXPECT_EQUAL(input.obtain(), Memory(data + 5));    
    EXPECT_EQUAL(input.obtain(), Memory(data + 5));    
    input.evict(5);
    EXPECT_EQUAL(input.obtain(), Memory());
}

TEST_MAIN() { TEST_RUN_ALL(); }
