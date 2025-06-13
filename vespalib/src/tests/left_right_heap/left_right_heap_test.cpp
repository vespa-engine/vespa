// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/left_right_heap.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <stdlib.h>
#include <algorithm>
#include <vector>
#include <cassert>

using namespace vespalib;

//-----------------------------------------------------------------------------

using int_up = std::unique_ptr<int>;

template <typename T> T wrap(int value);
template <> int wrap<int>(int value) { return value; }
template <> int_up wrap<int_up>(int value) { return int_up(new int(value)); }

int unwrap(const int &value) { return value; }
int unwrap(const int_up &value) { return *value; }

// verbose types needed to avoid warning

struct CmpInt {
    bool operator()(const int &a, const int &b) const {
        return (a < b);
    }
};

struct CmpIntUp {
    bool operator()(const int_up &a, const int_up &b) const {
        return (*a < *b);
    }
};

//-----------------------------------------------------------------------------

template <typename Heap> struct IsRight { enum { VALUE = 0 }; };
template <> struct IsRight<RightHeap> { enum { VALUE = 1 }; };
template <> struct IsRight<RightArrayHeap> { enum { VALUE = 1 }; };

bool operator==(const std::vector<int_up> &a,
                const std::vector<int> &b)
{
    if (a.size() != b.size()) {
        return false;
    }
    for (size_t i = 0; i < a.size(); ++i) {
        if (*a[i] != b[i]) {
            return false;
        }
    }
    return true;
}

size_t _G_InputSize = 1000;

struct Input {
    size_t n;
    std::vector<int> data;
    Input() : n(_G_InputSize), data() {
        srandom(42);
        for (size_t i = 0; i < n; ++i) {
            data.push_back(random());
        }
        assert(n == data.size());
    }
};


template <typename Heap, typename Value = int, typename Cmp = CmpInt>
struct MySetup {
    using IUP = MySetup<Heap, int_up, CmpIntUp>;
    Input &input;
    std::vector<Value> data;
    Cmp cmp;
    size_t limit;
    MySetup(Input &i) : input(i), data(), cmp(), limit(0) {}

    static void dumpData(Value *begin, Value *end) {
        int n = 10;
        while ((end - begin) > n) {
            for (int i = 0; i < n; ++i) {
                fprintf(stderr, "%d, ", unwrap(*begin++));
            }
            fprintf(stderr, "\n");
        }
        while ((end - begin) > 0) {
            fprintf(stderr, "%d, ", unwrap(*begin++));
        }
        fprintf(stderr, "\n");
    }

    static int peek_at(Value *begin, Value *end, size_t idx) {
        if (&Heap::front(begin, end) == begin) {
            return unwrap(*(begin + idx)); // normal order
        } else {
            return unwrap(*(end - 1 - idx)); // inverted order
        }
    }

    static void checkHeap(Value *begin, Value *end) {
        size_t len = (end - begin);
        for (size_t i = 0; i < len; ++i) {
            size_t child1 = (2 * i) + 1;
            size_t child2 = (2 * i) + 2;
            if (child1 < len) {
                ASSERT_LE(peek_at(begin, end, i),
                          peek_at(begin, end, child1)) << (dumpData(begin, end), "");
            }
            if (child2 < len) {
                ASSERT_LE(peek_at(begin, end, i),
                          peek_at(begin, end, child2)) << (dumpData(begin, end), "");
            }
        }
    }

    void push() {
        if (IsRight<Heap>::VALUE) {
            ASSERT_GT(limit, 0u);
            Heap::push(&data[--limit], &data[data.size()], cmp);
        } else {
            ASSERT_LT(limit, data.size());
            Heap::push(&data[0], &data[++limit], cmp);
        }
    }
    void push(int value) {
        if (IsRight<Heap>::VALUE) {
            data[limit - 1] = wrap<Value>(value);
        } else {
            data[limit] = wrap<Value>(value);
        }
        push();
    }
    Value &front() {
        if (IsRight<Heap>::VALUE) {
            return Heap::front(&data[limit], &data[data.size()]);
        } else {
            return Heap::front(&data[0], &data[limit]);
        }
    }
    void adjust() {
        if (IsRight<Heap>::VALUE) {
            Heap::adjust(&data[limit], &data[data.size()], cmp);
        } else {
            Heap::adjust(&data[0], &data[limit], cmp);
        }
    }
    int pop() {
        if (IsRight<Heap>::VALUE) {
            assert(limit < data.size());
            Heap::pop(&data[limit++], &data[data.size()], cmp);
            return unwrap(data[limit - 1]);
        } else {
            assert(limit > 0u);
            Heap::pop(&data[0], &data[limit--], cmp);
            return unwrap(data[limit]);
        }
    }
    void check() {
        if (IsRight<Heap>::VALUE) {
            checkHeap(&data[limit], &data[data.size()]);
        } else {
            checkHeap(&data[0], &data[limit]);
        }
    }
    void init() {
        data.clear();
        for (size_t i = 0; i < input.data.size(); ++i) {
            data.push_back(wrap<Value>(input.data[i]));
        }
        if (IsRight<Heap>::VALUE) {
            limit = data.size();
        } else {
            limit = 0;
        }
    }
    void testBasic() {
        init();
        push(100);
        EXPECT_EQ(100, unwrap(front()));
        adjust();
        EXPECT_EQ(100, unwrap(front()));
        push(50);
        EXPECT_EQ(50, unwrap(front()));
        adjust();
        EXPECT_EQ(50, unwrap(front()));
        push(200);
        push(175);
        EXPECT_EQ(50, unwrap(front()));
        front() = wrap<Value>(150);
        adjust();
        EXPECT_EQ(100, unwrap(front()));
        EXPECT_EQ(100, pop());
        EXPECT_EQ(150, pop());
        EXPECT_EQ(175, pop());
        EXPECT_EQ(200, pop());
    }
    void testSort() {
        init();
        for (size_t i = 0; i < input.n; ++i) {
            push();
            adjust(); // has no effect here
            check();
        }
        for (size_t i = 0; i < input.n; ++i) {
            adjust(); // has no effect here
            pop();
            check();
        }
        std::vector<int> ref = input.data;
        EXPECT_FALSE(data == ref);
        if (IsRight<Heap>::VALUE) {
            std::sort(ref.begin(), ref.end(), std::less<int>());
        } else {
            std::sort(ref.begin(), ref.end(), std::greater<int>());
        }
        EXPECT_TRUE(data == ref);
        if (!(data == ref)) {
            if (data.size() == ref.size()) {
                for (size_t i = 0; i < ref.size(); ++i) {
                    if (unwrap(data[i]) != ref[i]) {
                        fprintf(stderr, "data[%zu] != %d, ref[%zu] = %d\n",
                                i, unwrap(data[i]), i, ref[i]);
                    }
                }
            } else {
                fprintf(stderr, "sizes differ: %zu, %zu\n", data.size(), ref.size());
            }
            FAIL() << "forced unwind (see previous failure)";
        }
    }
    void test() {
        testBasic();
        testSort();
    }
};

TEST(LeftRightHeapTest, require_correct_heap_tags) {
    LeftHeap::require_left_heap();
    RightHeap::require_right_heap();
    LeftArrayHeap::require_left_heap();
    RightArrayHeap::require_right_heap();
    LeftStdHeap::require_left_heap();
}

TEST(LeftRightHeapTest, verify_left_heap_invariants_and_sorting) {
    Input f1;
    MySetup<LeftHeap> f2(f1);
    f2.test();
}
TEST(LeftRightHeapTest, verify_right_heap_invariants_and_sorting) {
    Input f1;
    MySetup<RightHeap> f2(f1);
    f2.test();
}
TEST(LeftRightHeapTest, verify_left_array_heap_invariants_and_sorting) {
    Input f1;
    MySetup<LeftArrayHeap> f2(f1);
    f2.test();
}
TEST(LeftRightHeapTest, verify_right_array_heap_invariants_and_sorting) {
    Input f1;
    MySetup<RightArrayHeap> f2(f1);
    f2.test();
}
TEST(LeftRightHeapTest, verify_left_std_heap_invariants_and_sorting) {
    Input f1;
    MySetup<LeftStdHeap> f2(f1);
    f2.test();
}

TEST(LeftRightHeapTest, verify_move_only_left_heap_invariants_and_sorting) {
    Input f1;
    MySetup<LeftHeap>::IUP f2(f1);
    f2.test();
}
TEST(LeftRightHeapTest, verify_move_only_right_heap_invariants_and_sorting) {
    Input f1;
    MySetup<RightHeap>::IUP f2(f1);
    f2.test();
}
TEST(LeftRightHeapTest, verify_move_only_left_array_heap_invariants_and_sorting) {
    Input f1;
    MySetup<LeftArrayHeap>::IUP f2(f1);
    f2.test();
}
TEST(LeftRightHeapTest, verify_move_only_right_array_heap_invariants_and_sorting) {
    Input f1;
    MySetup<RightArrayHeap>::IUP f2(f1);
    f2.test();
}
TEST(LeftRightHeapTest, verify_move_only_left_std_heap_invariants_and_sorting) {
    Input f1;
    MySetup<LeftStdHeap>::IUP f2(f1);
    f2.test();
}

GTEST_MAIN_RUN_ALL_TESTS()
