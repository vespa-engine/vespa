// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <absl/debugging/stacktrace.h>
#include <absl/debugging/symbolize.h>
#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>

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

void terminate_on_mi_malloc_failure(int err, [[maybe_unused]] void* arg) {
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
        constexpr int skip_frames = 3; // this frame + _mi_error_message + _mi_malloc_generic
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

}

}
