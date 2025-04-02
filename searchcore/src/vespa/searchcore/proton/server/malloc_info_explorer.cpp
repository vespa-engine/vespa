// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "malloc_info_explorer.h"
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/util/size_literals.h>
#include <cassert>
#include <cstdio>
#include <ranges>
#include <string>
#include <string_view>
#ifdef __linux__
#include <malloc.h>
#endif

extern "C" {

// Weakly resolved symbols that will be nullptr if they fail to resolve. Used to
// both detect the presence of a particular malloc implementation and to do the
// info dumping for it.
void vespamalloc_dump_info(FILE* out_file) __attribute__((weak));
// TODO mimalloc and friends

}

namespace proton {

using namespace vespalib::slime;
using namespace std::string_view_literals;

namespace {

#ifdef __linux__

enum class MallocImpl {
    LibcOrUnknown,
    VespaMalloc
};

[[nodiscard]]
MallocImpl detect_malloc_impl() noexcept {
    if (vespamalloc_dump_info != nullptr) {
        return MallocImpl::VespaMalloc;
    }
    return MallocImpl::LibcOrUnknown;
}

[[nodiscard]]
std::string_view to_string(MallocImpl mi) noexcept {
    switch (mi) {
    case MallocImpl::VespaMalloc:   return "vespamalloc";
    case MallocImpl::LibcOrUnknown: return "libc_or_unknown";
    }
    abort();
}

[[maybe_unused]]
void fclose_helper(FILE* f) {
    fclose(f);
}

std::string get_vespamalloc_info_dump() {
#ifdef _POSIX_C_SOURCE // For open_memstream()
    constexpr size_t max_buf_size = 16_Ki;
    auto buf = std::make_unique<char[]>(max_buf_size);

    char* buf_loc = buf.get();
    size_t buf_size = max_buf_size;
    {
        // buf_loc and buf_size will be updated on fclose().
        // In particular, buf_size will be set to #bytes written.
        std::unique_ptr<FILE, decltype(&fclose_helper)> mem_f(open_memstream(&buf_loc, &buf_size), &fclose_helper);
        if (!mem_f) {
            return "<open_memstream failed>";
        }
        assert(vespamalloc_dump_info != nullptr);
        vespamalloc_dump_info(mem_f.get());
    }
    assert(buf_size < max_buf_size); // Excludes implicit terminating null
    return {buf_loc, buf_size};
#else
    return "<unsupported by platform>";
#endif
}

#ifdef __GLIBC__
// mallinfo() and mallinfo2() only differ in the type of the underlying
// struct fields (int vs size_t, respectively).
void dump_mallinfo_impl(Cursor& object, const auto& info) {
    // mallinfo fields are so confusingly named, it's actually sort of impressive.
    // TODO consider if we want to massage the names/output a bit instead of emitting verbatim...
    //  - for deprecated mallinfo() vespamalloc will divide by 1 Mi for all byte-counting fields to avoid overflow
    object.setLong("arena",    static_cast<int64_t>(info.arena));
    object.setLong("ordblks",  static_cast<int64_t>(info.ordblks));
    object.setLong("smblks",   static_cast<int64_t>(info.smblks));
    object.setLong("hblks",    static_cast<int64_t>(info.hblks));
    object.setLong("hblkhd",   static_cast<int64_t>(info.hblkhd));
    object.setLong("usmblks",  static_cast<int64_t>(info.usmblks));
    object.setLong("fsmblks",  static_cast<int64_t>(info.fsmblks));
    object.setLong("uordblks", static_cast<int64_t>(info.uordblks));
    object.setLong("fordblks", static_cast<int64_t>(info.fordblks));
    object.setLong("keepcost", static_cast<int64_t>(info.keepcost));
}

void dump_mallinfo(Cursor& parent) {
#if __GLIBC_PREREQ(2, 33) // Has mallinfo2()
    dump_mallinfo_impl(parent.setObject("mallinfo2"), mallinfo2());
#else // Only has old and dusty (and deprecated) mallinfo()
    dump_mallinfo_impl(parent.setObject("mallinfo"), mallinfo());
#endif
}

#endif // __GLIBC__

void emit_malloc_internal_info_dump(Cursor& parent, std::string_view info_dump) {
    // Emit as JSON array of strings with one entry per line.
    // This is a lot easier to read than a single raw, newline-escaped string.
    Cursor& lines_arr = parent.setArray("internal_info");
    for (const auto line : std::views::split(info_dump, "\n"sv)) {
        lines_arr.addString(std::string_view(line));
    }
    // Also emit the raw string to make tooling easier (no need to collapse array).
    parent.setString("raw_internal_info", info_dump);
}

#endif // __linux__

} // anon ns

void MallocInfoExplorer::get_state(const Inserter& inserter, bool full) const {
    Cursor& object = inserter.insertObject();
    if (!full) {
        return;
    }
#ifdef __linux__
    const auto malloc_impl = detect_malloc_impl();
    object.setString("malloc_impl", to_string(malloc_impl));
#ifdef __GLIBC__
    dump_mallinfo(object);
#endif
    if (malloc_impl == MallocImpl::VespaMalloc) {
        emit_malloc_internal_info_dump(object, get_vespamalloc_info_dump());
    }
#else
    (void) object;
#endif // __linux__
}

} // proton
