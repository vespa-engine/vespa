// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
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
    EXPECT_EQUAL(Int::ctorCnt, alive + Int::dtorCnt);
    EXPECT_EQUAL(Int::aliveCnt, alive);
    EXPECT_EQUAL(Int::ddCnt, 0);
    Int::ctorCnt  = alive;
    Int::aliveCnt = alive;
    Int::dtorCnt  = 0;
    Int::ddCnt    = 0;
}

template<typename T> int unwrap(const T &);
template<> int unwrap<Int>(const Int &x) { return x; }
template<> int unwrap<std::unique_ptr<Int> >(const std::unique_ptr<Int> &x) { return *x; }
template<> int unwrap<FunkyItem>(const FunkyItem &x) {
    EXPECT_EQUAL(x.extra, *(x.mine));
    return unwrap(x.mine);
}

template <typename T> void checkInts(T &q, std::initializer_list<int> il) {
    size_t idx = 0;
    EXPECT_EQUAL(il.size() == 0, q.empty());
    for (auto itr = il.begin(); itr != il.end(); ++itr, ++idx) {
        int val = *itr;
        EXPECT_EQUAL(val, unwrap(q.peek(idx)));
        EXPECT_EQUAL(val, unwrap(q.access(idx)));
        if (idx == 0) {
            EXPECT_EQUAL(val, unwrap(q.front()));
        }
        if (idx == (il.size() - 1)) {
            EXPECT_EQUAL(val, unwrap(q.back()));
        }
    }
}

template <typename T> void testBasic() {
    typename T::Q q;
    TEST_DO(checkStatics(0));
    TEST_DO(checkInts(q, {}));
    T::push(q, 1);
    TEST_DO(checkStatics(1));
    TEST_DO(checkInts(q, { 1 }));
    T::push(q, 2);
    TEST_DO(checkStatics(2));
    TEST_DO(checkInts(q, { 1, 2 }));
    T::push(q, 3);
    TEST_DO(checkStatics(3));
    TEST_DO(checkInts(q, { 1, 2, 3 }));
    q.clear();
    TEST_DO(checkStatics(0));
    TEST_DO(checkInts(q, {}));
}

template <typename T> void testNormal() {
    typename T::Q q;
    for (uint32_t i = 0; i < 100; ++i) {
        TEST_DO(checkStatics(0));
        TEST_DO(checkInts(q, {}));
        T::push(q, 1);
        TEST_DO(checkStatics(1));
        TEST_DO(checkInts(q, { 1 }));
        T::push(q, 2);
        TEST_DO(checkStatics(2));
        TEST_DO(checkInts(q, { 1, 2 }));
        T::push(q, 3);
        TEST_DO(checkStatics(3));
        TEST_DO(checkInts(q, { 1, 2, 3 }));
        q.pop();
        TEST_DO(checkStatics(2));
        TEST_DO(checkInts(q, { 2, 3 }));
        q.pop();
        TEST_DO(checkStatics(1));
        TEST_DO(checkInts(q, { 3 }));
        q.pop();
        TEST_DO(checkStatics(0));
        TEST_DO(checkInts(q, {}));
    }
    T::push(q, 1);
    T::push(q, 2);
    T::push(q, 3);
    TEST_DO(checkStatics(3));
    TEST_DO(checkInts(q, { 1, 2, 3 }));
    q.clear();
    TEST_DO(checkStatics(0));
    TEST_DO(checkInts(q, {}));
}

template <typename T> void testReverse() {
    typename T::Q q;
    for (uint32_t i = 0; i < 100; ++i) {
        TEST_DO(checkStatics(0));
        TEST_DO(checkInts(q, {}));
        T::pushFront(q, 1);
        TEST_DO(checkStatics(1));
        TEST_DO(checkInts(q, { 1 }));
        T::pushFront(q, 2);
        TEST_DO(checkStatics(2));
        TEST_DO(checkInts(q, { 2, 1 }));
        T::pushFront(q, 3);
        TEST_DO(checkStatics(3));
        TEST_DO(checkInts(q, { 3, 2, 1 }));
        q.popBack();
        TEST_DO(checkStatics(2));
        TEST_DO(checkInts(q, { 3, 2 }));
        q.popBack();
        TEST_DO(checkStatics(1));
        TEST_DO(checkInts(q, { 3 }));
        q.popBack();
        TEST_DO(checkStatics(0));
        TEST_DO(checkInts(q, {}));
    }
    T::pushFront(q, 1);
    T::pushFront(q, 2);
    T::pushFront(q, 3);
    TEST_DO(checkStatics(3));
    TEST_DO(checkInts(q, { 3, 2, 1 }));
    q.clear();
    TEST_DO(checkStatics(0));
    TEST_DO(checkInts(q, {}));
}

template <typename T> void subTestCopy() { TEST_ERROR("undefined"); }
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
        TEST_DO(checkStatics(6));
        TEST_DO(checkInts(q1, { 1, 2, 3 }));
        TEST_DO(checkInts(q2, { 1, 2, 3 }));
        T::push(q1, 4);
        T::push(q1, 5);
        T::push(q2, 40);
        T::push(q2, 50);
        TEST_DO(checkStatics(10));
        TEST_DO(checkInts(q1, { 1, 2, 3, 4, 5 }));
        TEST_DO(checkInts(q2, { 1, 2, 3, 40, 50 }));
    }
    { // copy assign queue
        T::Q q1;
        T::Q q2;
        T::push(q1, 1);
        T::push(q1, 2);
        T::push(q1, 3);
        TEST_DO(checkStatics(3));
        TEST_DO(checkInts(q1, { 1, 2, 3 }));
        TEST_DO(checkInts(q2, {}));
        q2 = q1;
        TEST_DO(checkStatics(6));
        TEST_DO(checkInts(q1, { 1, 2, 3 }));
        TEST_DO(checkInts(q2, { 1, 2, 3 }));
        T::push(q1, 4);
        T::push(q1, 5);
        T::push(q2, 40);
        T::push(q2, 50);
        TEST_DO(checkStatics(10));
        TEST_DO(checkInts(q1, { 1, 2, 3, 4, 5 }));
        TEST_DO(checkInts(q2, { 1, 2, 3, 40, 50 }));
    }
}

template <typename T> void testEdit() {
    { // modify value in queue
        typename T::Q q;
        T::push(q, 5);
        TEST_DO(checkStatics(1));
        TEST_DO(checkInts(q, { 5 }));
        T::set(q, 0, 10);
        TEST_DO(checkStatics(1));
        TEST_DO(checkInts(q, { 10 }));
    }
    subTestCopy<T>(); // only test copy if elements of T::Q are copyable
    { // move construct queue
        typename T::Q q1;
        T::push(q1, 1);
        T::push(q1, 2);
        T::push(q1, 3);
        typename T::Q q2(std::move(q1));
        TEST_DO(checkStatics(3));
        TEST_DO(checkInts(q1, {}));
        TEST_DO(checkInts(q2, { 1, 2, 3 }));
        T::push(q1, 4);
        T::push(q1, 5);
        T::push(q2, 40);
        T::push(q2, 50);
        TEST_DO(checkStatics(7));
        TEST_DO(checkInts(q1, { 4, 5 }));
        TEST_DO(checkInts(q2, { 1, 2, 3, 40, 50 }));
    }
    { // move assign queue
        typename T::Q q1;
        typename T::Q q2;
        T::push(q1, 1);
        T::push(q1, 2);
        T::push(q1, 3);
        TEST_DO(checkStatics(3));
        TEST_DO(checkInts(q1, { 1, 2, 3 }));
        TEST_DO(checkInts(q2, {}));
        q2 = std::move(q1);
        TEST_DO(checkStatics(3));
        TEST_DO(checkInts(q1, {}));
        TEST_DO(checkInts(q2, { 1, 2, 3 }));
        T::push(q1, 4);
        T::push(q1, 5);
        T::push(q2, 40);
        T::push(q2, 50);
        TEST_DO(checkStatics(7));
        TEST_DO(checkInts(q1, { 4, 5 }));
        TEST_DO(checkInts(q2, { 1, 2, 3, 40, 50 }));
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
        TEST_DO(checkStatics(6));
        TEST_DO(checkInts(q1, { 1, 2, 3 }));
        TEST_DO(checkInts(q2, { 10, 20, 30 }));
        q1.swap(q2);
        TEST_DO(checkStatics(6));
        TEST_DO(checkInts(q1, { 10, 20, 30 }));
        TEST_DO(checkInts(q2, { 1, 2, 3 }));
    }
}

template <typename T> void testCapacity() {
    { // start with zero capacity
        typename T::Q q;
        EXPECT_EQUAL(q.capacity(), 0u);
        q.reserve(1);
        EXPECT_EQUAL(q.capacity(), 16u);
        q.reserve(16);
        EXPECT_EQUAL(q.capacity(), 16u);
        q.reserve(17);
        EXPECT_EQUAL(q.capacity(), 32u);
        q.reserve(33);
        EXPECT_EQUAL(q.capacity(), 64u);
        q.reserve(500);
        EXPECT_EQUAL(q.capacity(), 512u);
    }
    { // start with given capacity < 16
        typename T::Q q(10);
        EXPECT_EQUAL(q.capacity(), 10u);
        q.reserve(10);
        EXPECT_EQUAL(q.capacity(), 10u);
        q.reserve(11);
        EXPECT_EQUAL(q.capacity(), 16u);
        q.reserve(17);
        EXPECT_EQUAL(q.capacity(), 32u);
        q.reserve(33);
        EXPECT_EQUAL(q.capacity(), 64u);
        q.reserve(500);
        EXPECT_EQUAL(q.capacity(), 512u);
    }
    { // start with given capacity > 16
        typename T::Q q(20);
        EXPECT_EQUAL(q.capacity(), 20u);
        q.reserve(20);
        EXPECT_EQUAL(q.capacity(), 20u);
        q.reserve(21);
        EXPECT_EQUAL(q.capacity(), 40u);
        q.reserve(41);
        EXPECT_EQUAL(q.capacity(), 80u);
        q.reserve(81);
        EXPECT_EQUAL(q.capacity(), 160u);
        q.reserve(500);
        EXPECT_EQUAL(q.capacity(), 640u);
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
        EXPECT_EQUAL(unwrap(q.peek(i)), i);
    }
}

template <typename T> void dispatchTypedTests() {
    TEST_DO(testBasic<T>());
    TEST_DO(testNormal<T>());
    TEST_DO(testReverse<T>());
    TEST_DO(testEdit<T>());
    TEST_DO(testCapacity<T>());
    TEST_DO(testExpansion<T>());
}

TEST("test with copyable items") { dispatchTypedTests<Copy>(); }
TEST("test with movable items") { dispatchTypedTests<Move>(); }
TEST("test with emplaced items") { dispatchTypedTests<Emplace>(); }

TEST_MAIN() { TEST_RUN_ALL(); }
