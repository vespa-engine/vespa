// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/log/log.h>
#include <malloc.h>
#include <dlfcn.h>
#include <functional>

LOG_SETUP("new_test");

void cmp(const void *a, const void *b) {
    EXPECT_EQUAL(a, b);
}
void cmp(const void *base, size_t offset, const void *p) {
    cmp((static_cast<const char *>(base) + offset), p);
}

template <typename S>
void verify_aligned(S * p) {
    EXPECT_TRUE((uintptr_t(p) % alignof(S)) == 0);
    memset(p, 0, sizeof(S));
}

TEST("verify new with normal alignment") {
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

TEST("verify new with alignment = 16") {
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

TEST("verify new with alignment = 32") {
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

TEST("verify new with alignment = 64") {
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

TEST("verify new with alignment = 64 with single element") {
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
TEST("verify reallocarray") {
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
    EXPECT_NOT_EQUAL(arr, arr2);
    EXPECT_NOT_EQUAL(nullptr, arr2);
    EXPECT_NOT_EQUAL(ENOMEM, myErrno);

    errno = 0;
    void *arr3 = call_reallocarray(arr2, 1ul << 33, 1ul << 33);
    myErrno = errno;
    EXPECT_EQUAL(nullptr, arr3);
    EXPECT_EQUAL(ENOMEM, myErrno);
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
        EXPECT_EQUAL(info.usable, usable_size);
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

TEST("verify malloc_usable_size is sane") {
    constexpr size_t SZ = 33;
    std::unique_ptr<char[]> buf = std::make_unique<char[]>(SZ);
    size_t usable_size = malloc_usable_size(buf.get());
    if (_env == MallocLibrary::VESPA_MALLOC_D) {
        // Debug variants will never have more memory available as there is pre/postamble for error detection.
        EXPECT_EQUAL(SZ, usable_size);
    } else if (_env == MallocLibrary::VESPA_MALLOC) {
        // Normal production vespamalloc will round up
        EXPECT_EQUAL(64u, usable_size);
        verify_vespamalloc_usable_size();
    } else {
        // Non vespamalloc implementations we can not say anything about
        EXPECT_GREATER_EQUAL(usable_size, SZ);
    }
}

TEST("verify mallopt") {
    if (_env == MallocLibrary::UNKNOWN) return;
    EXPECT_EQUAL(0, mallopt(M_MMAP_MAX, 0x1000000));
    EXPECT_EQUAL(1, mallopt(M_MMAP_THRESHOLD, 0x1000000));
    EXPECT_EQUAL(1, mallopt(M_MMAP_THRESHOLD, 1_Gi));
}

TEST("verify mmap_limit") {
    if (_env == MallocLibrary::UNKNOWN) return;
    EXPECT_EQUAL(1, mallopt(M_MMAP_THRESHOLD, 0x100000));
    auto small = std::make_unique<char[]>(16_Ki);
    auto large_1 = std::make_unique<char[]>(1200_Ki);
    EXPECT_GREATER(size_t(labs(small.get() - large_1.get())), 1_Ti);
    EXPECT_EQUAL(1, mallopt(M_MMAP_THRESHOLD, 1_Gi));
    auto large_2 = std::make_unique<char[]>(1200_Ki);
    EXPECT_LESS(size_t(labs(small.get() - large_2.get())), 1_Ti);
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
    EXPECT_EQUAL(0u, count_mismatches(nv, 0x5b, INITIAL_SIZE));
    memset(nv, 0xbe, SECOND_SIZE);
    v = static_cast<char *>(realloc(nv, THIRD_SIZE));
    if (expect_vespamalloc_optimization) {
        ASSERT_TRUE(v != nv);
    }
    EXPECT_EQUAL(0u, count_mismatches(v, 0xbe, SECOND_SIZE));
    free(v);
}
TEST("test realloc large buffers") {
    verifyReallocLarge(nullptr, _env != MallocLibrary::UNKNOWN);
    verifyReallocLarge(static_cast<char *>(malloc(2000)), _env != MallocLibrary::UNKNOWN);
    if (_env == MallocLibrary::UNKNOWN) return;

    EXPECT_EQUAL(1, mallopt(M_MMAP_THRESHOLD, 1_Mi));
    verifyReallocLarge(nullptr, false);
    verifyReallocLarge(static_cast<char *>(malloc(2000)), false);
    EXPECT_EQUAL(1, mallopt(M_MMAP_THRESHOLD, 1_Gi));
}

TEST_MAIN() { TEST_RUN_ALL(); }
