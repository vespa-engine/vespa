// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/memory_trap.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <cstdlib>

using namespace vespalib;
using namespace ::testing;

template <typename T>
void do_not_optimize_away(T&& t) noexcept {
    asm volatile("" : : "m"(t) : "memory"); // Clobber the value to avoid losing it to compiler optimizations
}

struct MemoryTrapTest : Test {
    static void SetUpTestSuite() {
        // Don't overwrite env var if already set; we'll assume it's done for a good reason.
        setenv("VESPA_USE_MPROTECT_TRAP", "true", 0);
    }
};

TEST_F(MemoryTrapTest, untouched_memory_traps_do_not_trigger) {
    InlineMemoryTrap<2> stack_trap;
    HeapMemoryTrap heap_trap(4);
    // No touching == no crashing. Good times.
}

TEST_F(MemoryTrapTest, write_to_stack_trap_eventually_discovered) {
    // We don't explicitly test death messages since the way the process dies depends on
    // whether mprotect is enabled, whether ASAN instrumentation is enabled etc.
    ASSERT_DEATH({
        InlineMemoryTrap<2> stack_trap;
        // This may trigger immediately or on destruction. Either way it eventually kills the process.
        stack_trap.trapper().buffer()[0] = 0x01;
    },"");
}

TEST_F(MemoryTrapTest, write_to_heap_trap_eventually_discovered) {
    ASSERT_DEATH({
        HeapMemoryTrap heap_trap(4);
        // This may trigger immediately or on destruction. Either way it eventually kills the process.
        heap_trap.trapper().buffer()[heap_trap.trapper().size() - 1] = 0x01;
    },"");
}

TEST_F(MemoryTrapTest, read_from_hw_backed_trap_crashes_process) {
    if (!MemoryRangeTrapper::hw_trapping_enabled()) {
        return;
    }
    ASSERT_DEATH({
        HeapMemoryTrap heap_trap(4); // Entire buffer should always be covered
        // Clobber trap just in case the compiler is clever enough to look into the trap implementation
        // and see that we memset everything to zero and `dummy` can thus be constant-promoted to 0
        // (probably won't dare to do this anyway due to opaque mprotect() that touches buffer pointer).
        do_not_optimize_away(heap_trap);
        char dummy = heap_trap.trapper().buffer()[0];
        do_not_optimize_away(dummy); // never reached
    },"");
}

GTEST_MAIN_RUN_ALL_TESTS()
