// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/ref_counted.h>
#include <vespa/vespalib/util/gate.h>
#include <vespa/vespalib/util/thread.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;

struct Base : enable_ref_counted {
    static std::atomic<int> ctor_cnt;
    static std::atomic<int> dtor_cnt;
    int val;
    Base(int val_in) : val(val_in) {
        ctor_cnt.fetch_add(1, std::memory_order_relaxed);
    }
    ~Base() {
        dtor_cnt.fetch_add(1, std::memory_order_relaxed);
    }
};
std::atomic<int> Base::ctor_cnt = 0;
std::atomic<int> Base::dtor_cnt = 0;

struct Leaf : Base {
    static std::atomic<int> ctor_cnt;
    static std::atomic<int> dtor_cnt;
    Leaf(int val_in) : Base(val_in) {
        ctor_cnt.fetch_add(1, std::memory_order_relaxed);
    }
    ~Leaf() {
        dtor_cnt.fetch_add(1, std::memory_order_relaxed);
    }
};
std::atomic<int> Leaf::ctor_cnt = 0;
std::atomic<int> Leaf::dtor_cnt = 0;

void copy_assign_ref_counted_leaf_real(ref_counted<Leaf>& lhs, const ref_counted<Leaf> &rhs)
{
    lhs = rhs;
}

void (*copy_assign_ref_counted_leaf)(ref_counted<Leaf>& lhs, const ref_counted<Leaf> &rhs) = copy_assign_ref_counted_leaf_real;

void move_assign_ref_counted_leaf_real(ref_counted<Leaf>& lhs, ref_counted<Leaf>&& rhs)
{
    lhs = std::move(rhs);
}

void (*move_assign_ref_counted_leaf)(ref_counted<Leaf>& lhs, ref_counted<Leaf>&& rhs) = move_assign_ref_counted_leaf_real;

// check that the expected number of objects have been created and
// destroyed while this object was in scope.
struct CheckObjects {
    int expect_base;
    int expect_leaf;
    int old_base_ctor;
    int old_base_dtor;
    int old_leaf_ctor;
    int old_leaf_dtor;
    CheckObjects(int expect_base_in = 0, int expect_leaf_in = 0)
      : expect_base(expect_base_in),
        expect_leaf(expect_leaf_in)
    {
        old_base_ctor = Base::ctor_cnt.load(std::memory_order_relaxed);
        old_base_dtor = Base::dtor_cnt.load(std::memory_order_relaxed);
        old_leaf_ctor = Leaf::ctor_cnt.load(std::memory_order_relaxed);
        old_leaf_dtor = Leaf::dtor_cnt.load(std::memory_order_relaxed);
    }
    ~CheckObjects() {
        int base_ctor_diff = Base::ctor_cnt.load(std::memory_order_relaxed) - old_base_ctor;
        int base_dtor_diff = Base::dtor_cnt.load(std::memory_order_relaxed) - old_base_dtor;
        int leaf_ctor_diff = Leaf::ctor_cnt.load(std::memory_order_relaxed) - old_leaf_ctor;
        int leaf_dtor_diff = Leaf::dtor_cnt.load(std::memory_order_relaxed) - old_leaf_dtor;
        EXPECT_EQ(base_ctor_diff, expect_base);
        EXPECT_EQ(base_dtor_diff, expect_base);
        EXPECT_EQ(leaf_ctor_diff, expect_leaf);
        EXPECT_EQ(leaf_dtor_diff, expect_leaf);
    }
};

TEST(RefCountedTest, create_empty_ref_counted) {
    CheckObjects check;
    ref_counted<Base> empty;
    EXPECT_FALSE(empty);
}

TEST(RefCountedTest, make_ref_counted) {
    CheckObjects check(2, 1);
    auto ref1 = make_ref_counted<Base>(10);
    static_assert(std::same_as<decltype(ref1),ref_counted<Base>>);
    EXPECT_TRUE(ref1);
    EXPECT_EQ((*ref1).val, 10);
    EXPECT_EQ(ref1->val, 10);
    auto ref2 = make_ref_counted<Leaf>(20);
    static_assert(std::same_as<decltype(ref2),ref_counted<Leaf>>);
    EXPECT_TRUE(ref2);
    EXPECT_EQ((*ref2).val, 20);
    EXPECT_EQ(ref2->val, 20);
}

TEST(RefCountedTest, ref_counted_from) {
    CheckObjects check(1, 1);
    auto ref = make_ref_counted<Leaf>(10);
    Leaf &leaf = *ref;
    Base &base = leaf;
    EXPECT_EQ(ref->count_refs(), 1);
    auto from_leaf = ref_counted_from(leaf);
    static_assert(std::same_as<decltype(from_leaf),ref_counted<Leaf>>);
    auto from_base = ref_counted_from(base);
    static_assert(std::same_as<decltype(from_base),ref_counted<Base>>);
    EXPECT_EQ(ref->count_refs(), 3);
    EXPECT_EQ(from_base->val, 10);
}

TEST(RefCountedTest, use_internal_api) {
    CheckObjects check(1);
    Base *raw = new Base(20);
    EXPECT_EQ(raw->count_refs(), 1);
    ref_counted<Base> ref = ref_counted<Base>::internal_attach(raw);
    EXPECT_EQ(ref->count_refs(), 1);
    EXPECT_EQ(ref->val, 20);
    EXPECT_EQ(ref.internal_detach(), raw);
    EXPECT_EQ(raw->count_refs(), 1);
    raw->internal_addref();
    EXPECT_EQ(raw->count_refs(), 2);
    raw->internal_subref();
    EXPECT_EQ(raw->count_refs(), 1);
    raw->internal_subref();
}

TEST(RefCountedTest, use_multi_ref_internal_api) {
    CheckObjects check(1);
    Base *raw = new Base(20);
    EXPECT_EQ(raw->count_refs(), 1);
    raw->internal_addref(9);
    EXPECT_EQ(raw->count_refs(), 10);
    EXPECT_EQ(raw->val, 20);
    raw->internal_subref(6, 4);
    EXPECT_EQ(raw->count_refs(), 4);
    raw->internal_subref(4, 0);
}

TEST(RefCountedTest, move_ref_counted) {
    for (bool has_src: {true, false}) {
        for (bool has_dst: {true, false}) {
            for (bool same: {true, false}) {
                if (same) {
                    CheckObjects check(has_src + has_dst);
                    ref_counted<Base> src = has_src ? make_ref_counted<Base>(10) : ref_counted<Base>();
                    ref_counted<Base> dst = has_dst ? make_ref_counted<Base>(20) : ref_counted<Base>();
                    dst = std::move(src);
                    EXPECT_EQ(dst, has_src);
                    EXPECT_FALSE(src);
                    if (has_src) {
                        EXPECT_EQ(dst->val, 10);
                        EXPECT_EQ(dst->count_refs(), 1);
                    }
                } else {
                    CheckObjects check(has_src + has_dst, has_src + has_dst);
                    ref_counted<Leaf> src = has_src ? make_ref_counted<Leaf>(10) : ref_counted<Leaf>();
                    ref_counted<Base> dst = has_dst ? make_ref_counted<Leaf>(20) : ref_counted<Leaf>();
                    dst = std::move(src);
                    EXPECT_EQ(dst, has_src);
                    EXPECT_FALSE(src);
                    if (has_src) {
                        EXPECT_EQ(dst->val, 10);
                        EXPECT_EQ(dst->count_refs(), 1);
                    }
                }
            }
        }
    }
}

TEST(RefCountedTest, copy_ref_counted) {
    for (bool has_src: {true, false}) {
        for (bool has_dst: {true, false}) {
            for (bool same: {true, false}) {
                if (same) {
                    CheckObjects check(2);
                    ref_counted<Base> empty;
                    ref_counted<Base> obj1 = make_ref_counted<Base>(10);
                    ref_counted<Base> obj2 = make_ref_counted<Base>(20);
                    ref_counted<Base> src = has_src ? obj1 : empty;
                    ref_counted<Base> dst = has_dst ? obj2 : empty;
                    dst = src;
                    EXPECT_EQ(dst, has_src);
                    EXPECT_EQ(src, has_src);
                    if (has_src) {
                        EXPECT_EQ(dst->val, 10);
                        EXPECT_EQ(dst->count_refs(), 3);
                    }
                } else {
                    CheckObjects check(2, 2);
                    ref_counted<Leaf> empty;
                    ref_counted<Leaf> obj1 = make_ref_counted<Leaf>(10);
                    ref_counted<Leaf> obj2 = make_ref_counted<Leaf>(20);
                    ref_counted<Leaf> src = has_src ? obj1 : empty;
                    ref_counted<Base> dst = has_dst ? obj2 : empty;
                    dst = src;
                    EXPECT_EQ(dst, has_src);
                    EXPECT_EQ(src, has_src);
                    if (has_src) {
                        EXPECT_EQ(dst->val, 10);
                        EXPECT_EQ(dst->count_refs(), 3);
                    }
                }
            }
        }
    }
}

struct Other : enable_ref_counted {};

TEST(RefCountedTest, compile_errors_when_uncommented) {
    struct Foo {};
    [[maybe_unused]] Foo foo;
    [[maybe_unused]] ref_counted<Other> other = make_ref_counted<Other>();
    // ref_counted<Foo> empty;
    // auto ref1 = make_ref_counted<Foo>();
    // auto ref2 = ref_counted_from(foo);
    // ref_counted<Base> base = other;
}

TEST(RefCountedTest, self_assign) {
    ref_counted<Leaf> ref = make_ref_counted<Leaf>(10);
    copy_assign_ref_counted_leaf(ref, ref);
    move_assign_ref_counted_leaf(ref, std::move(ref));
    EXPECT_EQ(ref->count_refs(), 1);
    EXPECT_EQ(ref->val, 10);
}

TEST(RefCountedTest, reset) {
    CheckObjects check(1);
    auto ref = make_ref_counted<Base>(10);
    auto pre_cnt = Base::dtor_cnt.load(std::memory_order_relaxed);
    EXPECT_TRUE(ref);
    ref.reset();
    EXPECT_FALSE(ref);
    auto post_cnt = Base::dtor_cnt.load(std::memory_order_relaxed);
    EXPECT_EQ(post_cnt, pre_cnt + 1);
}

TEST(RefCountedTest, with_threads) {
    CheckObjects check(2,1);
    ThreadPool pool;
    Gate gate;
    {
        auto a = make_ref_counted<Base>(10);
        auto b = make_ref_counted<Leaf>(20);
        for (int i = 0; i < 8; ++i) {
            pool.start([&gate,a,b]()
                       {
                           gate.await();
                           for (int j = 0; j < 100000; ++j) {
                               auto c = a;
                               auto d = b;
                               EXPECT_EQ(c->val, 10);
                               EXPECT_EQ(d->val, 20);
                           }
                       });
        }
    }
    gate.countDown();
    pool.join();
}

GTEST_MAIN_RUN_ALL_TESTS()
