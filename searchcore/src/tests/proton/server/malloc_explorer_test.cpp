// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/server/malloc_info_explorer.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <atomic>
#include <cassert>
#include <string>
#include <dlfcn.h>

namespace {
std::atomic<bool> override_stats = false;
}

extern "C" {

using mi_output_fun = void(const char* msg, void* aux_arg);
using mi_stats_fun_ptr = void(*)(mi_output_fun*, void*);

// Override the symbol exported by mimalloc shared library with our own (which is in
// the test executable and thus has precedence), causing the state explorer to get mocked
// string data emitted. To be a good citizen we forward to the real backing call when
// the test code is _not_ executing... just in case!
void mi_stats_print_out(mi_output_fun* out_fn, void* arg) {
#ifdef RTLD_NEXT // "reserved for future use" under POSIX, but implemented functionally under glibc
    static const auto maybe_real_stats = reinterpret_cast<mi_stats_fun_ptr>(dlsym(RTLD_NEXT, "mi_stats_print_out"));
    assert(maybe_real_stats != mi_stats_print_out); // No infinite recursion please
#else
    const mi_stats_fun_ptr maybe_real_stats = nullptr;
#endif
    if (override_stats) {
        // Ensure we handle multiple output calls as part of one stats call
        out_fn("here ", arg);
        out_fn("be ", arg);
        out_fn("dragons!", arg);
    } else if (maybe_real_stats) {
        // Test is running _with_ mimalloc o_o, delegate to it...
        maybe_real_stats(out_fn, arg);
    }
}

} // extern "C"

TEST(MallocExplorerTest, mimalloc_internal_stats_are_emitted) {
    proton::MallocInfoExplorer explorer;
    vespalib::Slime result;
    vespalib::slime::SlimeInserter inserter(result);
    override_stats = true;
    explorer.get_state(inserter, true);
    override_stats = false;
#if !defined(__APPLE__)
    EXPECT_EQ(result["raw_internal_info"].asString(), "here be dragons!");
#endif
}
