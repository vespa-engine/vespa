// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/mimalloc_intercept.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <absl/debugging/symbolize.h>
#include <cerrno>

// Utility to force the compiler to assume functions have side effects. Otherwise,
// it simply cannot resist the temptation to optimize/inline across even functions
// declared as __attribute__((noipa)).
// See https://gcc.gnu.org/onlinedocs/gcc/Common-Function-Attributes.html
#define VESPA_ENFORCE_FN_SIDE_EFFECTS asm("")

using namespace ::testing;

namespace vespalib {

// Disable all inter-process optimizations for these very simple forwarding functions.
__attribute__((noipa)) void my_failing_function(int err);
__attribute__((noipa)) void my_fake_mi_error_message(int err);
__attribute__((noipa)) void my_fake_mi_malloc_generic(int err);

// The error handler skips a few frames of the stack top due to assumptions on the internal
// mimalloc call-path, so to get `my_failing_function` in the stack trace, emulate this here.

void my_fake_mi_error_message(int err) {
    terminate_on_mi_malloc_failure(err, nullptr);
    VESPA_ENFORCE_FN_SIDE_EFFECTS;
}

void my_fake_mi_malloc_generic(int err) {
    my_fake_mi_error_message(err);
    VESPA_ENFORCE_FN_SIDE_EFFECTS;
}

void my_failing_function(int err) {
    my_fake_mi_malloc_generic(err);
    VESPA_ENFORCE_FN_SIDE_EFFECTS;
}

TEST(MiMallocErrorHandlerDeathTest, oom_condition_quick_exits_with_stack_trace) {
    EXPECT_EXIT({ my_failing_function(ENOMEM); }, ExitedWithCode(66),
                "mimalloc has reported an OOM condition; exiting process.*my_failing_function");
}

TEST(MiMallocErrorHandlerDeathTest, mimalloc_eagain_aborts_with_double_free_message) {
    EXPECT_EXIT({ my_failing_function(EAGAIN); }, KilledBySignal(SIGABRT),
                "mimalloc has reported an invariant violation: double-free");
}

TEST(MiMallocErrorHandlerDeathTest, mimalloc_efault_aborts_with_corruption_message) {
    EXPECT_EXIT({ my_failing_function(EFAULT); }, KilledBySignal(SIGABRT),
                "mimalloc has reported an invariant violation: corrupted free-list or metadata");
}

TEST(MiMallocErrorHandlerDeathTest, mimalloc_eoverflow_aborts_with_too_large_allocation_message) {
    EXPECT_EXIT({ my_failing_function(EOVERFLOW); }, KilledBySignal(SIGABRT),
                "mimalloc has reported an invariant violation: too large allocation request");
}

TEST(MiMallocErrorHandlerDeathTest, mimalloc_einval_aborts_with_invalid_ptr_message) {
    EXPECT_EXIT({ my_failing_function(EINVAL); }, KilledBySignal(SIGABRT),
                "mimalloc has reported an invariant violation: trying to free or reallocate an invalid pointer");
}

TEST(MiMallocErrorHandlerDeathTest, mimalloc_unknown_errno_aborts_with_unknown_error_and_errno) {
    EXPECT_EXIT({ my_failing_function(EPERM); }, KilledBySignal(SIGABRT),
                // \d seems to be broken for GTest regexes (see https://github.com/google/googletest/issues/3084)...
                R"(mimalloc has reported an invariant violation: \(unknown error\) \(errno .+\))");
}

} // ns vespalib

int main(int argc, char* argv[]) {
    absl::InitializeSymbolizer(argv[0]);
    InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
