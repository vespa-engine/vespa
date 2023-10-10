// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/left_right_heap.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <stdlib.h>
#include <algorithm>
#include <vector>

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
        ASSERT_EQUAL(n, data.size());
    }
};


template <typename Heap, typename Value = int, typename Cmp = CmpInt>
struct Setup {
    using IUP = Setup<Heap, int_up, CmpIntUp>;
    Input &input;
    std::vector<Value> data;
    Cmp cmp;
    size_t limit;
    Setup(Input &i) : input(i), data(), cmp(), limit(0) {}

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
        if (&Heap::template front(begin, end) == begin) {
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
                if(!EXPECT_LESS_EQUAL(peek_at(begin, end, i),
                                      peek_at(begin, end, child1)))
                {
                    dumpData(begin, end);
                    TEST_FATAL("forced unwind (see previous failure)");
                }
            }
            if (child2 < len) {
                if (!EXPECT_LESS_EQUAL(peek_at(begin, end, i),
                                       peek_at(begin, end, child2)))
                {
                    dumpData(begin, end);
                    TEST_FATAL("forced unwind (see previous failure)");
                }
            }
        }
    }

    void push() {
        if (IsRight<Heap>::VALUE) {
            ASSERT_GREATER(limit, 0u);
            Heap::push(&data[--limit], &data[data.size()], cmp);
        } else {
            ASSERT_LESS(limit, data.size());
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
            ASSERT_LESS(limit, data.size());
            Heap::pop(&data[limit++], &data[data.size()], cmp);
            return unwrap(data[limit - 1]);
        } else {
            ASSERT_GREATER(limit, 0u);
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
        EXPECT_EQUAL(100, unwrap(front()));
        adjust();
        EXPECT_EQUAL(100, unwrap(front()));
        push(50);
        EXPECT_EQUAL(50, unwrap(front()));
        adjust();
        EXPECT_EQUAL(50, unwrap(front()));
        push(200);
        push(175);
        EXPECT_EQUAL(50, unwrap(front()));
        front() = wrap<Value>(150);
        adjust();
        EXPECT_EQUAL(100, unwrap(front()));
        EXPECT_EQUAL(100, pop());
        EXPECT_EQUAL(150, pop());
        EXPECT_EQUAL(175, pop());
        EXPECT_EQUAL(200, pop());
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
        if (!EXPECT_TRUE(data == ref)) {
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
            TEST_FATAL("forced unwind (see previous failure)");
        }
    }
    void test() {
        testBasic();
        testSort();
    }
};

TEST("require correct heap tags") {
    LeftHeap::require_left_heap();
    RightHeap::require_right_heap();
    LeftArrayHeap::require_left_heap();
    RightArrayHeap::require_right_heap();
    LeftStdHeap::require_left_heap();
}

TEST_FF("verify left heap invariants and sorting", Input, Setup<LeftHeap>(f1)) { f2.test(); }
TEST_FF("verify right heap invariants and sorting", Input, Setup<RightHeap>(f1)) { f2.test(); }
TEST_FF("verify left array heap invariants and sorting", Input, Setup<LeftArrayHeap>(f1)) { f2.test(); }
TEST_FF("verify right array heap invariants and sorting", Input, Setup<RightArrayHeap>(f1)) { f2.test(); }
TEST_FF("verify left std heap invariants and sorting", Input, Setup<LeftStdHeap>(f1)) { f2.test(); }

TEST_FF("verify [move only] left heap invariants and sorting", Input, Setup<LeftHeap>::IUP(f1)) { f2.test(); }
TEST_FF("verify [move only] right heap invariants and sorting", Input, Setup<RightHeap>::IUP(f1)) { f2.test(); }
TEST_FF("verify [move only] left array heap invariants and sorting", Input, Setup<LeftArrayHeap>::IUP(f1)) { f2.test(); }
TEST_FF("verify [move only] right array heap invariants and sorting", Input, Setup<RightArrayHeap>::IUP(f1)) { f2.test(); }
TEST_FF("verify [move only] left std heap invariants and sorting", Input, Setup<LeftStdHeap>::IUP(f1)) { f2.test(); }

TEST_MAIN() {
    // Would be nice to have access to arguments.....
    _G_InputSize = 1000; // strtoul(_argv[1], NULL, 0);
    TEST_RUN_ALL();
}
