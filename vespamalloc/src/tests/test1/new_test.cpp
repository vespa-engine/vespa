// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/optimized.h>
#include <vespa/log/log.h>
#include <malloc.h>
#include <dlfcn.h>
#include <functional>
#include <cassert>

LOG_SETUP("new_test");

void *wrap_memalign_real(size_t alignment, size_t size)
{
    return memalign(alignment, size);
}

void* (*wrap_memalign)(size_t alignment, size_t size) = wrap_memalign_real;

void *wrap_aligned_alloc_real(size_t alignment, size_t size)
{
    return aligned_alloc(alignment, size);
}

void* (*wrap_aligned_alloc)(size_t alignment, size_t size) = wrap_aligned_alloc_real;

void cmp(const void *a, const void *b) {
    EXPECT_EQ(a, b);
}
void cmp(const void *base, size_t offset, const void *p) {
    cmp((static_cast<const char *>(base) + offset), p);
}

template <typename S>
void verify_aligned(S * p) {
    EXPECT_TRUE((uintptr_t(p) % alignof(S)) == 0);
    memset(p, 0, sizeof(S));
}

TEST(NewTest, verify_new_with_normal_alignment) {
    struct S {
        int a;
        long b;
        int c;
    };
    static_assert(sizeof(S) == 24);
    static_assert(alignof(S) == 8);
    auto s = std::make_unique<S>();
    verify_aligned(s.get());
    cmp(s.get(), &s->a);
    cmp(s.get(), 8, &s->b);
    cmp(s.get(), 16, &s->c);
    LOG(info, "&s=%p &s.b=%p &s.c=%p", s.get(), &s->b, &s->c);
}

TEST(NewTest, verify_new_with_alignment_16) {
    struct S {
        int a;
        alignas(16) long b;
        int c;
    };
    static_assert(sizeof(S) == 32);
    static_assert(alignof(S) == 16);
    auto s = std::make_unique<S>();
    verify_aligned(s.get());
    cmp(s.get(), &s->a);
    cmp(s.get(), 16, &s->b);
    cmp(s.get(), 24, &s->c);
    LOG(info, "&s=%p &s.b=%p &s.c=%p", s.get(), &s->b, &s->c);
}

TEST(NewTest, verify_new_with_alignment_32) {
    struct S {
        int a;
        alignas(32) long b;
        int c;
    };
    static_assert(sizeof(S) == 64);
    static_assert(alignof(S) == 32);
    auto s = std::make_unique<S>();
    verify_aligned(s.get());
    cmp(s.get(), &s->a);
    cmp(s.get(), 32, &s->b);
    cmp(s.get(), 40, &s->c);
    LOG(info, "&s=%p &s.b=%p &s.c=%p", s.get(), &s->b, &s->c);
}

TEST(NewTest, verify_new_with_alignment_64) {
    struct S {
        int a;
        alignas(64) long b;
        int c;
    };
    static_assert(sizeof(S) == 128);
    static_assert(alignof(S) == 64);
    auto s = std::make_unique<S>();
    verify_aligned(s.get());
    cmp(s.get(), &s->a);
    cmp(s.get(), 64, &s->b);
    cmp(s.get(), 72, &s->c);
    LOG(info, "&s=%p &s.b=%p &s.c=%p", s.get(), &s->b, &s->c);
}

TEST(NewTest, verify_new_with_alignment_64_with_single_element) {
    struct S {
        alignas(64) long a;
    };
    static_assert(sizeof(S) == 64);
    static_assert(alignof(S) == 64);
    auto s = std::make_unique<S>();
    verify_aligned(s.get());
    cmp(s.get(), &s->a);
    LOG(info, "&s=%p", s.get());
}

#if __GLIBC_PREREQ(2, 26)
TEST(NewTest, verify_reallocarray) {
    std::function<void*(void*,size_t,size_t)> call_reallocarray = [](void *ptr, size_t nmemb, size_t size) noexcept { return reallocarray(ptr, nmemb, size); };
    void *arr = calloc(5,5);
    //Used to ensure that 'arr' can not resized in place.
    std::vector<std::unique_ptr<char[]>> dummies;
    for (size_t i(0); i < 1000; i++) {
        dummies.push_back(std::make_unique<char[]>(5*5));
    }
    errno = 0;
    void *arr2 = call_reallocarray(arr, 800, 5);
    int myErrno = errno;
    EXPECT_NE(arr, arr2);
    EXPECT_NE(nullptr, arr2);
    EXPECT_NE(ENOMEM, myErrno);

    errno = 0;
    void *arr3 = call_reallocarray(arr2, 1ul << 33, 1ul << 33);
    myErrno = errno;
    EXPECT_EQ(nullptr, arr3);
    EXPECT_EQ(ENOMEM, myErrno);
    free(arr2);
}
#endif

namespace {

void
verify_vespamalloc_usable_size() {
    struct AllocInfo {
        size_t requested;
        size_t usable;
    };
    AllocInfo allocInfo[] = {{0x7,      0x20},
                             {0x27,     0x40},
                             {0x47,     0x80},
                             {0x87,     0x100},
                             {0x107,    0x200},
                             {0x207,    0x400},
                             {0x407,    0x800},
                             {0x807,    0x1000},
                             {0x1007,   0x2000},
                             {0x2007,   0x4000},
                             {0x4007,   0x8000},
                             {0x8007,   0x10000},
                             {0x10007,  0x20000},
                             {0x20007,  0x40000},
                             {0x40007,  0x80000},
                             {0x80007,  0x100000},
                             {0x100007, 0x200000},
                             {0x200007, 0x400000},
                             {0x400007, 0x600000}};
    for (const AllocInfo &info: allocInfo) {
        std::unique_ptr<char[]> buf = std::make_unique<char[]>(info.requested);
        size_t usable_size = malloc_usable_size(buf.get());
        EXPECT_EQ(info.usable, usable_size);
    }
}

enum class MallocLibrary {
    UNKNOWN, VESPA_MALLOC, VESPA_MALLOC_D
};

MallocLibrary
detectLibrary() {
    if (dlsym(RTLD_NEXT, "is_vespamallocd") != nullptr) {
        // Debug variants will never have more memory available as there is pre/postamble for error detection.
        return MallocLibrary::VESPA_MALLOC_D;
    } else if (dlsym(RTLD_NEXT, "is_vespamalloc") != nullptr) {
        return MallocLibrary::VESPA_MALLOC;
    }
    return MallocLibrary::UNKNOWN;
}

MallocLibrary _env = detectLibrary();

size_t
count_mismatches(const char * v, char c, size_t count) {
    size_t errors = 0;
    for (size_t i(0); i < count; i++) {
        if (v[i] != c) errors++;
    }
    return errors;
}

}

TEST(NewTest, verify_malloc_usable_size_is_sane) {
    constexpr size_t SZ = 33;
    std::unique_ptr<char[]> buf = std::make_unique<char[]>(SZ);
    size_t usable_size = malloc_usable_size(buf.get());
    if (_env == MallocLibrary::VESPA_MALLOC_D) {
        // Debug variants will never have more memory available as there is pre/postamble for error detection.
        EXPECT_EQ(SZ, usable_size);
    } else if (_env == MallocLibrary::VESPA_MALLOC) {
        // Normal production vespamalloc will round up
        EXPECT_EQ(64u, usable_size);
        verify_vespamalloc_usable_size();
    } else {
        // Non vespamalloc implementations we can not say anything about
        EXPECT_GE(usable_size, SZ);
    }
}

TEST(NewTest, verify_mallopt) {
    if (_env == MallocLibrary::UNKNOWN) return;
    EXPECT_EQ(0, mallopt(M_MMAP_MAX, 0x1000000));
    EXPECT_EQ(1, mallopt(M_MMAP_THRESHOLD, 0x1000000));
    EXPECT_EQ(1, mallopt(M_MMAP_THRESHOLD, 1_Gi));
}

TEST(NewTest, verify_mmap_limit) {
    if (_env == MallocLibrary::UNKNOWN) return;
    EXPECT_EQ(1, mallopt(M_MMAP_THRESHOLD, 0x100000));
    auto small = std::make_unique<char[]>(16_Ki);
    auto large_1 = std::make_unique<char[]>(1200_Ki);
    EXPECT_GT(size_t(labs(small.get() - large_1.get())), 1_Ti);
    EXPECT_EQ(1, mallopt(M_MMAP_THRESHOLD, 1_Gi));
    auto large_2 = std::make_unique<char[]>(1200_Ki);
    EXPECT_LT(size_t(labs(small.get() - large_2.get())), 1_Ti);
}

void
verifyReallocLarge(char * initial, bool expect_vespamalloc_optimization) {
    const size_t INITIAL_SIZE = 0x400001;
    const size_t SECOND_SIZE = 0x500001;
    const size_t THIRD_SIZE = 0x600001;
    char *v = static_cast<char *>(realloc(initial, INITIAL_SIZE));
    memset(v, 0x5b, INITIAL_SIZE);
    char *nv = static_cast<char *>(realloc(v, SECOND_SIZE));
    if (expect_vespamalloc_optimization) {
        ASSERT_TRUE(v == nv);
    }
    EXPECT_EQ(0u, count_mismatches(nv, 0x5b, INITIAL_SIZE));
    memset(nv, 0xbe, SECOND_SIZE);
    v = static_cast<char *>(realloc(nv, THIRD_SIZE));
    if (expect_vespamalloc_optimization) {
        ASSERT_TRUE(v != nv);
    }
    EXPECT_EQ(0u, count_mismatches(v, 0xbe, SECOND_SIZE));
    free(v);
}
TEST(NewTest, test_realloc_large_buffers) {
    verifyReallocLarge(nullptr, _env != MallocLibrary::UNKNOWN);
    verifyReallocLarge(static_cast<char *>(malloc(2000)), _env != MallocLibrary::UNKNOWN);
    if (_env == MallocLibrary::UNKNOWN) return;

    EXPECT_EQ(1, mallopt(M_MMAP_THRESHOLD, 1_Mi));
    verifyReallocLarge(nullptr, false);
    verifyReallocLarge(static_cast<char *>(malloc(2000)), false);
    EXPECT_EQ(1, mallopt(M_MMAP_THRESHOLD, 1_Gi));
}

// Edge case: reallocation of block large enough to get its own distinct memory mapped
// region down to an allocation _smaller_ than the original allocation. The amount of
// memory copied from the old allocation to the new one must be the _minimum_ of the
// two allocation sizes.
TEST(NewTest, realloc_from_large_to_small_constrains_copied_memory_extent) {
    if (_env == MallocLibrary::UNKNOWN) {
        return;
    }
    constexpr size_t old_sz = 8_Mi;
    constexpr size_t new_sz = 1_Ki;
    ASSERT_EQ(1, mallopt(M_MMAP_THRESHOLD, 1_Mi));
    char* buf = static_cast<char*>(malloc(old_sz));
    assert(buf);
    memset(buf, 0x5b, old_sz);
    // This will SIGSEGV (with a very high likelihood) if we don't constrain the copied
    // memory size since it will attempt to write outside a valid mapped region.
    char* realloc_buf = static_cast<char*>(realloc(buf, new_sz));
    EXPECT_NE(realloc_buf, buf); // Should not have reused buffer

    EXPECT_EQ(0u, count_mismatches(realloc_buf, 0x5b, new_sz));
    free(realloc_buf);
}

void verify_alignment(void * ptr, size_t align, size_t min_sz) {
    EXPECT_NE(ptr, nullptr);
    EXPECT_EQ(0u, size_t(ptr) & (align-1));
    assert(0ul == (size_t(ptr) & (align-1)));
    EXPECT_GE(malloc_usable_size(ptr), min_sz);
    free(ptr);
}

TEST(NewTest, test_memalign) {
    verify_alignment(wrap_memalign(0, 0), 1, 1);
    verify_alignment(wrap_memalign(0, 1), 1, 1);
    verify_alignment(wrap_memalign(1, 0), 1, 1);

    for (size_t align : {3,7,19}) {
        // According to man pages these should fail, but it seems it rounds up and does best effort
        verify_alignment(wrap_memalign(align, 73), 1ul << vespalib::Optimized::msbIdx(align), 73);
    }
    for (size_t align : {1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536}) {
        verify_alignment(wrap_memalign(align, 1), align, 1);
    }
}

TEST(NewTest, test_aligned_alloc) {
    verify_alignment(wrap_aligned_alloc(1, 0), 1, 1);
    for (size_t align : {1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536}) {
        verify_alignment(wrap_aligned_alloc(align, align*7), align, align*7);
    }
    for (size_t sz : {31,33,63}) {
        // According to man pages these should fail, but it seems it rounds up and does best effort
        verify_alignment(wrap_aligned_alloc(32, sz), 32, sz);
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
