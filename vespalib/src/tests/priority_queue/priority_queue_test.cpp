// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("priority_queue_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/priority_queue.h>

using namespace vespalib;

TEST("require that default priority order works") {
    PriorityQueue<int> queue;
    EXPECT_EQUAL(true, queue.empty());
    EXPECT_EQUAL(0u, queue.size());
    queue.push(5);
    queue.push(3);
    queue.push(7);
    queue.push(10);
    queue.push(2);
    EXPECT_EQUAL(false, queue.empty());
    EXPECT_EQUAL(5u, queue.size());
    EXPECT_EQUAL(2, queue.front());
    queue.front() = 6;
    queue.adjust();
    EXPECT_EQUAL(3, queue.front());
    queue.pop_front();
    EXPECT_EQUAL(5, queue.front());
    queue.pop_front();
    EXPECT_EQUAL(6, queue.front());
    queue.pop_front();
    EXPECT_EQUAL(7, queue.front());
    queue.pop_front();
    EXPECT_EQUAL(10, queue.front());
    queue.pop_front();
    EXPECT_EQUAL(true, queue.empty());
    EXPECT_EQUAL(0u, queue.size());
}

TEST("require that priority order can be specified") {
    PriorityQueue<int, std::greater<int> > queue;
    EXPECT_EQUAL(true, queue.empty());
    EXPECT_EQUAL(0u, queue.size());
    queue.push(5);
    queue.push(3);
    queue.push(7);
    queue.push(10);
    queue.push(2);
    EXPECT_EQUAL(false, queue.empty());
    EXPECT_EQUAL(5u, queue.size());
    EXPECT_EQUAL(10, queue.front());
    queue.front() = 6;
    queue.adjust();
    EXPECT_EQUAL(7, queue.front());
    queue.pop_front();
    EXPECT_EQUAL(6, queue.front());
    queue.pop_front();
    EXPECT_EQUAL(5, queue.front());
    queue.pop_front();
    EXPECT_EQUAL(3, queue.front());
    queue.pop_front();
    EXPECT_EQUAL(2, queue.front());
    queue.pop_front();
    EXPECT_EQUAL(true, queue.empty());
    EXPECT_EQUAL(0u, queue.size());
}

TEST("require that a random item can be accessed and removed") {
    size_t n = 100;
    PriorityQueue<int> queue;
    std::vector<int>   seen(100, 0);
    for (size_t i = 0; i < n; ++i) {
        queue.push(i);
    }
    EXPECT_EQUAL(n, queue.size());
    for (size_t i = 0; i < n; ++i) {
        ++seen[queue.any()];
        queue.pop_any();
    }
    EXPECT_TRUE(queue.empty());
    for (size_t i = 0; i < n; ++i) {
        EXPECT_EQUAL(1, seen[i]);
    }
}

struct MyCmp {
    int *ref;
    MyCmp(int *r) : ref(r) {}
    bool operator()(const int &a, const int &b) {
        return (ref[a] < ref[b]);
    }
};

TEST("require that the comparator can have state") {
    std::vector<int> ref(5);
    PriorityQueue<int, MyCmp> queue(MyCmp(&ref.front()));
    ref[3] = 1;
    ref[2] = 2;
    ref[0] = 3;
    ref[4] = 4;
    ref[1] = 5;
    queue.push(0);
    queue.push(1);
    queue.push(2);
    queue.push(3);
    queue.push(4);
    ASSERT_EQUAL(5u, queue.size());
    EXPECT_EQUAL(3, queue.front()); queue.pop_front();
    EXPECT_EQUAL(2, queue.front()); queue.pop_front();
    EXPECT_EQUAL(0, queue.front()); queue.pop_front();
    EXPECT_EQUAL(4, queue.front()); queue.pop_front();
    EXPECT_EQUAL(1, queue.front()); queue.pop_front();
}

TEST("require that the heap algorithm can be changed") {
    PriorityQueue<int, std::less<int>, LeftArrayHeap> queue;
    for (int i = 99; i >= 0; --i) {
        queue.push(i);
    }
    EXPECT_EQUAL(0, queue.front());
    ASSERT_EQUAL(100u, queue.size());
    for (int i = 0; i < 100; ++i) {
        EXPECT_EQUAL(queue.front(), queue.any());
        EXPECT_EQUAL(i, queue.front()); queue.pop_front();
    }
}

using int_up = std::unique_ptr<int>;
int_up wrap(int value) { return int_up(new int(value)); }
struct CmpIntUp {
    bool operator()(const int_up &a, const int_up &b) const {
        return (*a < *b);
    }
};

TEST("require that priority queue works with move-only objects") {
    PriorityQueue<int_up, CmpIntUp> queue;
    queue.push(wrap(5));
    queue.push(wrap(3));
    queue.push(wrap(7));
    queue.push(wrap(10));
    queue.push(wrap(2));
    std::vector<int_up> stash;
    stash.push_back(std::move(queue.front())); queue.pop_front();
    stash.push_back(std::move(queue.front())); queue.pop_front();
    stash.push_back(std::move(queue.front())); queue.pop_front();
    stash.push_back(std::move(queue.front())); queue.pop_front();
    stash.push_back(std::move(queue.front())); queue.pop_front();
    ASSERT_EQUAL(5u, stash.size());
    EXPECT_EQUAL(2, *stash[0]);
    EXPECT_EQUAL(3, *stash[1]);
    EXPECT_EQUAL(5, *stash[2]);
    EXPECT_EQUAL(7, *stash[3]);
    EXPECT_EQUAL(10, *stash[4]);
}

struct MyItem {
    int value;
    int *ref;
    MyItem(int v, int &r) noexcept : value(v), ref(&r) {}
    MyItem(const MyItem &) noexcept = delete;
    MyItem &operator=(const MyItem &) noexcept = delete;
    MyItem(MyItem &&rhs) noexcept : value(rhs.value), ref(rhs.ref) { rhs.ref = 0; }
    MyItem &operator=(MyItem &&rhs) noexcept {
        value = rhs.value;
        ref = rhs.ref;
        rhs.ref = 0;
        return *this;
    }
    ~MyItem() {
        if (ref != 0) {
            ++(*ref);
        }
    }
    bool operator<(const MyItem &rhs) const { return (value < rhs.value); }
};

TEST("require that pop'ed elements are destructed") {
    PriorityQueue<MyItem> queue;
    int cnt = 0;
    queue.push(MyItem(5, cnt));
    queue.push(MyItem(7, cnt));
    queue.push(MyItem(3, cnt));
    EXPECT_EQUAL(0, cnt);
    queue.pop_front();
    EXPECT_EQUAL(1, cnt);
    queue.pop_any();
    EXPECT_EQUAL(2, cnt);
    queue.pop_front();
    EXPECT_EQUAL(3, cnt);
}

TEST_MAIN() { TEST_RUN_ALL(); }
