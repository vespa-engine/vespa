// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("priority_queue_test");
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/priority_queue.h>

using namespace vespalib;

TEST(PriorityQueueTest, require_that_default_priority_order_works) {
    PriorityQueue<int> queue;
    EXPECT_EQ(true, queue.empty());
    EXPECT_EQ(0u, queue.size());
    queue.push(5);
    queue.push(3);
    queue.push(7);
    queue.push(10);
    queue.push(2);
    EXPECT_EQ(false, queue.empty());
    EXPECT_EQ(5u, queue.size());
    EXPECT_EQ(2, queue.front());
    queue.front() = 6;
    queue.adjust();
    EXPECT_EQ(3, queue.front());
    queue.pop_front();
    EXPECT_EQ(5, queue.front());
    queue.pop_front();
    EXPECT_EQ(6, queue.front());
    queue.pop_front();
    EXPECT_EQ(7, queue.front());
    queue.pop_front();
    EXPECT_EQ(10, queue.front());
    queue.pop_front();
    EXPECT_EQ(true, queue.empty());
    EXPECT_EQ(0u, queue.size());
}

TEST(PriorityQueueTest, require_that_priority_order_can_be_specified) {
    PriorityQueue<int, std::greater<int> > queue;
    EXPECT_EQ(true, queue.empty());
    EXPECT_EQ(0u, queue.size());
    queue.push(5);
    queue.push(3);
    queue.push(7);
    queue.push(10);
    queue.push(2);
    EXPECT_EQ(false, queue.empty());
    EXPECT_EQ(5u, queue.size());
    EXPECT_EQ(10, queue.front());
    queue.front() = 6;
    queue.adjust();
    EXPECT_EQ(7, queue.front());
    queue.pop_front();
    EXPECT_EQ(6, queue.front());
    queue.pop_front();
    EXPECT_EQ(5, queue.front());
    queue.pop_front();
    EXPECT_EQ(3, queue.front());
    queue.pop_front();
    EXPECT_EQ(2, queue.front());
    queue.pop_front();
    EXPECT_EQ(true, queue.empty());
    EXPECT_EQ(0u, queue.size());
}

TEST(PriorityQueueTest, require_that_a_random_item_can_be_accessed_and_removed) {
    size_t n = 100;
    PriorityQueue<int> queue;
    std::vector<int>   seen(100, 0);
    for (size_t i = 0; i < n; ++i) {
        queue.push(i);
    }
    EXPECT_EQ(n, queue.size());
    for (size_t i = 0; i < n; ++i) {
        ++seen[queue.any()];
        queue.pop_any();
    }
    EXPECT_TRUE(queue.empty());
    for (size_t i = 0; i < n; ++i) {
        EXPECT_EQ(1, seen[i]);
    }
}

struct MyCmp {
    int *ref;
    MyCmp(int *r) : ref(r) {}
    bool operator()(const int &a, const int &b) {
        return (ref[a] < ref[b]);
    }
};

TEST(PriorityQueueTest, require_that_the_comparator_can_have_state) {
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
    ASSERT_EQ(5u, queue.size());
    EXPECT_EQ(3, queue.front()); queue.pop_front();
    EXPECT_EQ(2, queue.front()); queue.pop_front();
    EXPECT_EQ(0, queue.front()); queue.pop_front();
    EXPECT_EQ(4, queue.front()); queue.pop_front();
    EXPECT_EQ(1, queue.front()); queue.pop_front();
}

TEST(PriorityQueueTest, require_that_the_heap_algorithm_can_be_changed) {
    PriorityQueue<int, std::less<int>, LeftArrayHeap> queue;
    for (int i = 99; i >= 0; --i) {
        queue.push(i);
    }
    EXPECT_EQ(0, queue.front());
    ASSERT_EQ(100u, queue.size());
    for (int i = 0; i < 100; ++i) {
        EXPECT_EQ(queue.front(), queue.any());
        EXPECT_EQ(i, queue.front()); queue.pop_front();
    }
}

using int_up = std::unique_ptr<int>;
int_up wrap(int value) { return int_up(new int(value)); }
struct CmpIntUp {
    bool operator()(const int_up &a, const int_up &b) const {
        return (*a < *b);
    }
};

TEST(PriorityQueueTest, require_that_priority_queue_works_with_move_only_objects) {
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
    ASSERT_EQ(5u, stash.size());
    EXPECT_EQ(2, *stash[0]);
    EXPECT_EQ(3, *stash[1]);
    EXPECT_EQ(5, *stash[2]);
    EXPECT_EQ(7, *stash[3]);
    EXPECT_EQ(10, *stash[4]);
}

struct MyItem {
    int value;
    int *ref;
    MyItem(int v, int &r) noexcept : value(v), ref(&r) {}
    MyItem(const MyItem &) noexcept = delete;
    MyItem &operator=(const MyItem &) noexcept = delete;
    MyItem(MyItem &&rhs) noexcept : value(rhs.value), ref(rhs.ref) { rhs.ref = nullptr; }
    MyItem &operator=(MyItem &&rhs) noexcept {
        value = rhs.value;
        ref = rhs.ref;
        rhs.ref = nullptr;
        return *this;
    }
    ~MyItem() {
        if (ref != nullptr) {
            ++(*ref);
        }
    }
    bool operator<(const MyItem &rhs) const { return (value < rhs.value); }
};

TEST(PriorityQueueTest, require_that_popped_elements_are_destructed) {
    PriorityQueue<MyItem> queue;
    int cnt = 0;
    queue.push(MyItem(5, cnt));
    queue.push(MyItem(7, cnt));
    queue.push(MyItem(3, cnt));
    EXPECT_EQ(0, cnt);
    queue.pop_front();
    EXPECT_EQ(1, cnt);
    queue.pop_any();
    EXPECT_EQ(2, cnt);
    queue.pop_front();
    EXPECT_EQ(3, cnt);
}

GTEST_MAIN_RUN_ALL_TESTS()
