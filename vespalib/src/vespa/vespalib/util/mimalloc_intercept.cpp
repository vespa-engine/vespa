// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mimalloc_intercept.h"
#include <absl/debugging/stacktrace.h>
#include <absl/debugging/symbolize.h>
#include <atomic>
#include <cerrno>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <thread>

using namespace std::chrono_literals;

// Global init-time utility for detecting the presence of mimalloc and installing
// a custom error handler that ensures malloc() never returns nullptr on allocation
// failure, but instead immediately exits the process after dumping a stacktrace to stderr.
// It also explicitly aborts the process if any other invariant failures are reported
// by the mimalloc runtime.

extern "C" {

// From https://microsoft.github.io/mimalloc/group__extended.html
typedef void mi_error_fun(int err, void* arg);
void mi_register_error(mi_error_fun* err_fn, void* arg) __attribute__((weak));

}

namespace vespalib {

namespace {

__attribute__((noreturn))
void terminate_on_mi_malloc_failure_once(int err, [[maybe_unused]] void* arg) {
    // From https://microsoft.github.io/mimalloc/group__extended.html:
    // "The possible error codes are:
    //   EAGAIN:    Double free was detected (only in debug and secure mode).
    //   EFAULT:    Corrupted free list or meta-data was detected (only in
    //              debug and secure mode).
    //   ENOMEM:    Not enough memory available to satisfy the request.
    //   EOVERFLOW: Too large a request, for example in mi_calloc(), the
    //              count and size parameters are too large.
    //   EINVAL:    Trying to free or re-allocate an invalid pointer."
    //
    // We abort on everything except ENOMEM, in which case we quick-exit
    // with the same exit code as vespamalloc.
    if (err == ENOMEM) {
        fprintf(stderr, "mimalloc has reported an OOM condition; exiting process. "
                        "Allocation site stack trace (may dump core if symbolizer has not been initialized):\n");
        // Use Abseil stacktrace functionality, as it is async signal safe.
        // Note that this is predicated on absl::InitializeSymbolizer(argv[0]) having been
        // called via main() some point prior to this happening. This is done explicitly by our
        // "large" C++ binaries, but may not always be the case for any other random binary.
        // This is then likely to trigger a transitive abort() by the Abseil allocation code
        // if there's no memory available.
        // Since we trigger _Exit() rather than abort() to avoid creating massive core
        // dumps for processes with very large footprints, this is mostly relevant for the
        // binaries where we already init the symbolizer, so triggering a core for other
        // (presumably less beefy) processes is not necessarily a problem.
        constexpr int max_frames = 32;
        // Don't make any assumptions on which frames can be omitted from the stack trace.
        // This is both dependent on compiler optimizations and the underlying mimalloc code.
        constexpr int skip_frames = 0;
        void* frames[max_frames];
        int depth = absl::GetStackTrace(frames, max_frames, skip_frames);
        for (int i = 0; i < depth; ++i) {
            const char* sym = "(unknown)";
            char tmp[1024];
            if (absl::Symbolize(frames[i], tmp, sizeof(tmp))) {
                sym = tmp;
            }
            fprintf(stderr, "%p  %s\n", frames[i], sym);
        }
        std::_Exit(66);
    } else {
        const char* msg;
        switch (err) {
        case EAGAIN:    msg = "double-free"; break;
        case EFAULT:    msg = "corrupted free-list or metadata"; break;
        case EOVERFLOW: msg = "too large allocation request"; break;
        case EINVAL:    msg = "trying to free or reallocate an invalid pointer"; break;
        default:        msg = "(unknown error)";
        }
        fprintf(stderr, "mimalloc has reported an invariant violation: %s (errno %d). Terminating.\n", msg, err);
        abort();
    }
}

// It's possible for an unspecified number of threads to concurrently enter our fatal
// mimalloc error interception handler, but we only want to trigger a stack trace and
// a process exit/abort from the first one that reports that the end is near. The
// alternative is a jumbled mess of interleaved error messages and stacks on stderr.
// This flag must always be initialized before `init_mi_malloc_error_handler`, so
// the two should always be kept in the same translation unit and in this order.
std::atomic_flag error_handler_entered{};

} // anon ns

void terminate_on_mi_malloc_failure(int err, void* fwd_arg) {
    // Only allow the first failing thread to enter the error handler. The error handler
    // will always terminate the process in one way or another, so for the other threads
    // there is nothing else to do than to wait for the inevitable.
    // Reminder: `test_and_set()` returns the _previously_ held value.
    if (!error_handler_entered.test_and_set()) {
        terminate_on_mi_malloc_failure_once(err, fwd_arg);
    } else {
        // This ship is already sinking, so play the violin until the process is terminated.
        // It should be noted that until P2809R1 is in place, infinite _trivial_ (side effect
        // less) loops are technically undefined, but we assume that `sleep_for` explicitly
        // models a side effect.
        while (true) {
            std::this_thread::sleep_for(1s);
        }
    }
}

namespace {

class MiMallocAutoRegisterErrorHandler {
public:
    MiMallocAutoRegisterErrorHandler() {
        // If there's no `libmimalloc.so` preloaded, `mi_register_error` will be nullptr
        // since it's a weakly resolved symbol. In that case, do nothing.
        if (mi_register_error) {
            mi_register_error(terminate_on_mi_malloc_failure, nullptr);
        }
    }
};

[[maybe_unused]] MiMallocAutoRegisterErrorHandler init_mi_malloc_error_handler;

} // anon ns

}
