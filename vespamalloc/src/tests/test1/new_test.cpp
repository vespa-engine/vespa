// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
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

void verify_vespamalloc_usable_size() {
    struct AllocInfo { size_t requested; size_t usable;};
    AllocInfo allocInfo[] = {{0x7, 0x20}, {0x27, 0x40}, {0x47, 0x80}, {0x87, 0x100}, {0x107, 0x200}, {0x207, 0x400},
                            {0x407, 0x800}, {0x807, 0x1000}, {0x1007, 0x2000}, {0x2007, 0x4000}, {0x4007, 0x8000},
                            {0x8007, 0x10000}, {0x10007, 0x20000}, {0x20007, 0x40000}, {0x40007, 0x80000}, {0x80007, 0x100000},
                            {0x100007, 0x200000}, {0x200007, 0x400000}, {0x400007, 0x600000}};
    for (const AllocInfo & info : allocInfo) {
        std::unique_ptr<char[]> buf = std::make_unique<char[]>(info.requested);
        size_t usable_size = malloc_usable_size(buf.get());
        EXPECT_EQUAL(info.usable, usable_size);
    }
}

TEST("verify malloc_usable_size is sane") {
    constexpr size_t SZ = 33;
    std::unique_ptr<char[]> buf = std::make_unique<char[]>(SZ);
    size_t usable_size = malloc_usable_size(buf.get());
    if (dlsym(RTLD_NEXT, "is_vespamallocd") != nullptr) {
        // Debug variants will never have more memory available as there is pre/postamble for error detection.
        EXPECT_EQUAL(SZ, usable_size);
    } else if (dlsym(RTLD_NEXT, "is_vespamalloc") != nullptr) {
        // Normal production vespamalloc will round up
        EXPECT_EQUAL(64u, usable_size);
        verify_vespamalloc_usable_size();
    } else {
        // Non vespamalloc implementations we can not say anything about
        EXPECT_GREATER_EQUAL(usable_size, SZ);
    }
}


TEST_MAIN() { TEST_RUN_ALL(); }
