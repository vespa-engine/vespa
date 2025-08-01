// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/arrayqueue.hpp>

using namespace vespalib;

// 'instrumented' int
struct Int
{
    static int ctorCnt;
    static int aliveCnt;
    static int dtorCnt;
    static int ddCnt;

    bool alive;
    int  value;

    Int(int val) : alive(true), value(val)
    {
        ++ctorCnt;
        ++aliveCnt;
    }
    Int(const Int &val) : alive(true), value(val.value)
    {
        ++ctorCnt;
        ++aliveCnt;
    }
    Int &operator=(const Int &rhs) = default;
    operator int() const { return value; }
    ~Int() {
        ++dtorCnt;
        if (alive) {
            --aliveCnt;
            alive = false;
        } else {
            ++ddCnt;
        }
    }
};
int Int::ctorCnt  = 0;
int Int::aliveCnt = 0;
int Int::dtorCnt  = 0;
int Int::ddCnt    = 0;

struct FunkyItem {
    int extra;
    std::unique_ptr<Int> mine;
    FunkyItem(const FunkyItem &) = delete;
    FunkyItem &operator=(const FunkyItem &) = delete;
    FunkyItem(int e, int m) : extra(e), mine(new Int(m)) {}
    FunkyItem(FunkyItem &&rhs) : extra(rhs.extra), mine(std::move(rhs.mine)) {}
};

struct Copy {
    using Q = ArrayQueue<Int>;
    static void push(Q &q, int v) { Int value(v); q.push(value); }
    static void pushFront(Q &q, int v) { Int value(v); q.pushFront(value); }
    static void set(Q &q, int idx, int val) { q.access(idx) = val; }
};

struct Move {
    using Q = ArrayQueue<std::unique_ptr<Int> >;
    static void push(Q &q, int v) { q.push(std::unique_ptr<Int>(new Int(v))); }
    static void pushFront(Q &q, int v) { q.pushFront(std::unique_ptr<Int>(new Int(v))); }
    static void set(Q &q, int idx, int val) { *q.access(idx) = val; }
};

struct Emplace {
    using Q = ArrayQueue<FunkyItem>;
    static void push(Q &q, int v) { q.emplace(v, v); }
    static void pushFront(Q &q, int v) { q.emplaceFront(v, v); }
    static void set(Q &q, int idx, int val) {
        q.access(idx).extra = val;
        *q.access(idx).mine = val;
    }
};

void checkStatics(int alive) {
    EXPECT_EQ(Int::ctorCnt, alive + Int::dtorCnt);
    EXPECT_EQ(Int::aliveCnt, alive);
    EXPECT_EQ(Int::ddCnt, 0);
    Int::ctorCnt  = alive;
    Int::aliveCnt = alive;
    Int::dtorCnt  = 0;
    Int::ddCnt    = 0;
}

template<typename T> int unwrap(const T &);
template<> int unwrap<Int>(const Int &x) { return x; }
template<> int unwrap<std::unique_ptr<Int> >(const std::unique_ptr<Int> &x) { return *x; }
template<> int unwrap<FunkyItem>(const FunkyItem &x) {
    EXPECT_EQ(x.extra, *(x.mine));
    return unwrap(x.mine);
}

template <typename T> void checkInts(T &q, std::initializer_list<int> il) {
    size_t idx = 0;
    EXPECT_EQ(il.size() == 0, q.empty());
    for (auto itr = il.begin(); itr != il.end(); ++itr, ++idx) {
        int val = *itr;
        EXPECT_EQ(val, unwrap(q.peek(idx)));
        EXPECT_EQ(val, unwrap(q.access(idx)));
        if (idx == 0) {
            EXPECT_EQ(val, unwrap(q.front()));
        }
        if (idx == (il.size() - 1)) {
            EXPECT_EQ(val, unwrap(q.back()));
        }
    }
}

template <typename T> void testBasic() {
    typename T::Q q;
    GTEST_DO(checkStatics(0));
    GTEST_DO(checkInts(q, {}));
    T::push(q, 1);
    GTEST_DO(checkStatics(1));
    GTEST_DO(checkInts(q, { 1 }));
    T::push(q, 2);
    GTEST_DO(checkStatics(2));
    GTEST_DO(checkInts(q, { 1, 2 }));
    T::push(q, 3);
    GTEST_DO(checkStatics(3));
    GTEST_DO(checkInts(q, { 1, 2, 3 }));
    q.clear();
    GTEST_DO(checkStatics(0));
    GTEST_DO(checkInts(q, {}));
}

template <typename T> void testNormal() {
    typename T::Q q;
    for (uint32_t i = 0; i < 100; ++i) {
        GTEST_DO(checkStatics(0));
        GTEST_DO(checkInts(q, {}));
        T::push(q, 1);
        GTEST_DO(checkStatics(1));
        GTEST_DO(checkInts(q, { 1 }));
        T::push(q, 2);
        GTEST_DO(checkStatics(2));
        GTEST_DO(checkInts(q, { 1, 2 }));
        T::push(q, 3);
        GTEST_DO(checkStatics(3));
        GTEST_DO(checkInts(q, { 1, 2, 3 }));
        q.pop();
        GTEST_DO(checkStatics(2));
        GTEST_DO(checkInts(q, { 2, 3 }));
        q.pop();
        GTEST_DO(checkStatics(1));
        GTEST_DO(checkInts(q, { 3 }));
        q.pop();
        GTEST_DO(checkStatics(0));
        GTEST_DO(checkInts(q, {}));
    }
    T::push(q, 1);
    T::push(q, 2);
    T::push(q, 3);
    GTEST_DO(checkStatics(3));
    GTEST_DO(checkInts(q, { 1, 2, 3 }));
    q.clear();
    GTEST_DO(checkStatics(0));
    GTEST_DO(checkInts(q, {}));
}

template <typename T> void testReverse() {
    typename T::Q q;
    for (uint32_t i = 0; i < 100; ++i) {
        GTEST_DO(checkStatics(0));
        GTEST_DO(checkInts(q, {}));
        T::pushFront(q, 1);
        GTEST_DO(checkStatics(1));
        GTEST_DO(checkInts(q, { 1 }));
        T::pushFront(q, 2);
        GTEST_DO(checkStatics(2));
        GTEST_DO(checkInts(q, { 2, 1 }));
        T::pushFront(q, 3);
        GTEST_DO(checkStatics(3));
        GTEST_DO(checkInts(q, { 3, 2, 1 }));
        q.popBack();
        GTEST_DO(checkStatics(2));
        GTEST_DO(checkInts(q, { 3, 2 }));
        q.popBack();
        GTEST_DO(checkStatics(1));
        GTEST_DO(checkInts(q, { 3 }));
        q.popBack();
        GTEST_DO(checkStatics(0));
        GTEST_DO(checkInts(q, {}));
    }
    T::pushFront(q, 1);
    T::pushFront(q, 2);
    T::pushFront(q, 3);
    GTEST_DO(checkStatics(3));
    GTEST_DO(checkInts(q, { 3, 2, 1 }));
    q.clear();
    GTEST_DO(checkStatics(0));
    GTEST_DO(checkInts(q, {}));
}

template <typename T> void subTestCopy() { FAIL() << "undefined"; }
template <> void subTestCopy<Emplace>() {}
template <> void subTestCopy<Move>() {}
template <> void subTestCopy<Copy>() {
    using T = Copy;
    { // copy construct queue
        T::Q q1;
        T::push(q1, 1);
        T::push(q1, 2);
        T::push(q1, 3);
        T::Q q2(q1);
        GTEST_DO(checkStatics(6));
        GTEST_DO(checkInts(q1, { 1, 2, 3 }));
        GTEST_DO(checkInts(q2, { 1, 2, 3 }));
        T::push(q1, 4);
        T::push(q1, 5);
        T::push(q2, 40);
        T::push(q2, 50);
        GTEST_DO(checkStatics(10));
        GTEST_DO(checkInts(q1, { 1, 2, 3, 4, 5 }));
        GTEST_DO(checkInts(q2, { 1, 2, 3, 40, 50 }));
    }
    { // copy assign queue
        T::Q q1;
        T::Q q2;
        T::push(q1, 1);
        T::push(q1, 2);
        T::push(q1, 3);
        GTEST_DO(checkStatics(3));
        GTEST_DO(checkInts(q1, { 1, 2, 3 }));
        GTEST_DO(checkInts(q2, {}));
        q2 = q1;
        GTEST_DO(checkStatics(6));
        GTEST_DO(checkInts(q1, { 1, 2, 3 }));
        GTEST_DO(checkInts(q2, { 1, 2, 3 }));
        T::push(q1, 4);
        T::push(q1, 5);
        T::push(q2, 40);
        T::push(q2, 50);
        GTEST_DO(checkStatics(10));
        GTEST_DO(checkInts(q1, { 1, 2, 3, 4, 5 }));
        GTEST_DO(checkInts(q2, { 1, 2, 3, 40, 50 }));
    }
}

template <typename T> void testEdit() {
    { // modify value in queue
        typename T::Q q;
        T::push(q, 5);
        GTEST_DO(checkStatics(1));
        GTEST_DO(checkInts(q, { 5 }));
        T::set(q, 0, 10);
        GTEST_DO(checkStatics(1));
        GTEST_DO(checkInts(q, { 10 }));
    }
    subTestCopy<T>(); // only test copy if elements of T::Q are copyable
    { // move construct queue
        typename T::Q q1;
        T::push(q1, 1);
        T::push(q1, 2);
        T::push(q1, 3);
        typename T::Q q2(std::move(q1));
        GTEST_DO(checkStatics(3));
        GTEST_DO(checkInts(q1, {}));
        GTEST_DO(checkInts(q2, { 1, 2, 3 }));
        T::push(q1, 4);
        T::push(q1, 5);
        T::push(q2, 40);
        T::push(q2, 50);
        GTEST_DO(checkStatics(7));
        GTEST_DO(checkInts(q1, { 4, 5 }));
        GTEST_DO(checkInts(q2, { 1, 2, 3, 40, 50 }));
    }
    { // move assign queue
        typename T::Q q1;
        typename T::Q q2;
        T::push(q1, 1);
        T::push(q1, 2);
        T::push(q1, 3);
        GTEST_DO(checkStatics(3));
        GTEST_DO(checkInts(q1, { 1, 2, 3 }));
        GTEST_DO(checkInts(q2, {}));
        q2 = std::move(q1);
        GTEST_DO(checkStatics(3));
        GTEST_DO(checkInts(q1, {}));
        GTEST_DO(checkInts(q2, { 1, 2, 3 }));
        T::push(q1, 4);
        T::push(q1, 5);
        T::push(q2, 40);
        T::push(q2, 50);
        GTEST_DO(checkStatics(7));
        GTEST_DO(checkInts(q1, { 4, 5 }));
        GTEST_DO(checkInts(q2, { 1, 2, 3, 40, 50 }));
    }
    { // swap queues
        typename T::Q q1;
        typename T::Q q2;
        T::push(q1, 1);
        T::push(q1, 2);
        T::push(q1, 3);
        T::push(q2, 10);
        T::push(q2, 20);
        T::push(q2, 30);
        GTEST_DO(checkStatics(6));
        GTEST_DO(checkInts(q1, { 1, 2, 3 }));
        GTEST_DO(checkInts(q2, { 10, 20, 30 }));
        q1.swap(q2);
        GTEST_DO(checkStatics(6));
        GTEST_DO(checkInts(q1, { 10, 20, 30 }));
        GTEST_DO(checkInts(q2, { 1, 2, 3 }));
    }
}

template <typename T> void testCapacity() {
    { // start with zero capacity
        typename T::Q q;
        EXPECT_EQ(q.capacity(), 0u);
        q.reserve(1);
        EXPECT_EQ(q.capacity(), 16u);
        q.reserve(16);
        EXPECT_EQ(q.capacity(), 16u);
        q.reserve(17);
        EXPECT_EQ(q.capacity(), 32u);
        q.reserve(33);
        EXPECT_EQ(q.capacity(), 64u);
        q.reserve(500);
        EXPECT_EQ(q.capacity(), 512u);
    }
    { // start with given capacity < 16
        typename T::Q q(10);
        EXPECT_EQ(q.capacity(), 10u);
        q.reserve(10);
        EXPECT_EQ(q.capacity(), 10u);
        q.reserve(11);
        EXPECT_EQ(q.capacity(), 16u);
        q.reserve(17);
        EXPECT_EQ(q.capacity(), 32u);
        q.reserve(33);
        EXPECT_EQ(q.capacity(), 64u);
        q.reserve(500);
        EXPECT_EQ(q.capacity(), 512u);
    }
    { // start with given capacity > 16
        typename T::Q q(20);
        EXPECT_EQ(q.capacity(), 20u);
        q.reserve(20);
        EXPECT_EQ(q.capacity(), 20u);
        q.reserve(21);
        EXPECT_EQ(q.capacity(), 40u);
        q.reserve(41);
        EXPECT_EQ(q.capacity(), 80u);
        q.reserve(81);
        EXPECT_EQ(q.capacity(), 160u);
        q.reserve(500);
        EXPECT_EQ(q.capacity(), 640u);
    }
}

template <typename T> void testExpansion() {
    typename T::Q q(32);
    T::push(q, 111);
    T::push(q, 222);
    T::push(q, 333);
    q.pop();
    q.pop();
    q.pop();
    for (int i = 0; i < 200; ++i) {
        T::push(q, i);
    }
    for (int i = 0; i < 200; ++i) {
        EXPECT_EQ(unwrap(q.peek(i)), i);
    }
}

template <typename T> void dispatchTypedTests() {
    GTEST_DO(testBasic<T>());
    GTEST_DO(testNormal<T>());
    GTEST_DO(testReverse<T>());
    GTEST_DO(testEdit<T>());
    GTEST_DO(testCapacity<T>());
    GTEST_DO(testExpansion<T>());
}

TEST(ArrayQueueTest, test_with_copyable_items) { dispatchTypedTests<Copy>(); }
TEST(ArrayQueueTest, test_with_movable_items) { dispatchTypedTests<Move>(); }
TEST(ArrayQueueTest, test_with_emplaced_items) { dispatchTypedTests<Emplace>(); }

GTEST_MAIN_RUN_ALL_TESTS()
