// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/log/log.h>

LOG_SETUP("new_test");

void cmp(const void *a, const void *b) {
    EXPECT_EQUAL(a, b);
}
void cmp(const void *base, size_t offset, const void *p) {
    cmp((static_cast<const char *>(base) + offset), p);
}

template <typename S>
void veryfy_aligned(const S * p) {
    EXPECT_TRUE((uintptr_t(p) % alignof(S)) == 0);
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
    veryfy_aligned(s.get());
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
    veryfy_aligned(s.get());
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
    veryfy_aligned(s.get());
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
    veryfy_aligned(s.get());
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
    veryfy_aligned(s.get());
    cmp(s.get(), &s->a);
    LOG(info, "&s=%p", s.get());
}

TEST("verify new with alignment = 64 with single element") {
    struct alignas(64) S {
        long a;
    };
    static_assert(sizeof(S) == 64);
    static_assert(alignof(S) == 64);
    auto s = std::make_unique<S>();
    veryfy_aligned(s.get());
    cmp(s.get(), &s->a);
    LOG(info, "&s=%p", s.get());
}


TEST_MAIN() { TEST_RUN_ALL(); }
