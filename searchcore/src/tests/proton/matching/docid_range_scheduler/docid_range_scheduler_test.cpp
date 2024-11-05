// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/matching/docid_range_scheduler.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/nexus.h>
#include <vespa/vespalib/testkit/time_bomb.h>
#include <latch>

using namespace proton::matching;
using vespalib::TimeBomb;
using vespalib::test::Nexus;

void verify_range(const std::string& label, DocidRange a, DocidRange b) {
    SCOPED_TRACE(label);
    EXPECT_EQ(a.begin, b.begin);
    EXPECT_EQ(a.end, b.end);
}
//-----------------------------------------------------------------------------

TEST(DocidRangeSchedulerTest, require_that_default_docid_range_constructor_creates_and_empty_range)
{
    EXPECT_TRUE(DocidRange().empty());
    EXPECT_EQ(DocidRange().size(), 0u);
}

TEST(DocidRangeSchedulerTest, require_that_docid_range_ensures_end_is_not_less_than_begin)
{
    EXPECT_EQ(DocidRange(10, 20).size(), 10u);
    EXPECT_TRUE(!DocidRange(10, 20).empty());
    EXPECT_EQ(DocidRange(10, 20).begin, 10u);
    EXPECT_EQ(DocidRange(10, 20).end, 20u);
    EXPECT_EQ(DocidRange(20, 10).size(), 0u);
    EXPECT_TRUE(DocidRange(20, 10).empty());
    EXPECT_EQ(DocidRange(20, 10).begin, 20u);
    EXPECT_EQ(DocidRange(20, 10).end, 20u);
}

//-----------------------------------------------------------------------------

TEST(DocidRangeSchedulerTest, require_that_default_constructed_IdleObserver_is_always_zero)
{
    IdleObserver observer;
    EXPECT_TRUE(observer.is_always_zero());
    EXPECT_EQ(0u, observer.get());
}

TEST(DocidRangeSchedulerTest, require_that_IdleObserver_can_observe_an_atomic_size_t_value)
{
    std::atomic<size_t> idle(0);
    IdleObserver observer(idle);
    EXPECT_TRUE(!observer.is_always_zero());
    EXPECT_EQ(0u, observer.get());
    idle = 10;
    EXPECT_EQ(10u, observer.get());
}

//-----------------------------------------------------------------------------

TEST(DocidRangeSchedulerTest, require_that_the_docid_range_splitter_can_split_a_docid_range)
{
    DocidRangeSplitter splitter(DocidRange(1, 16), 4);
    verify_range("0", splitter.get(0), DocidRange(1, 5));
    verify_range("1", splitter.get(1), DocidRange(5, 9));
    verify_range("2", splitter.get(2), DocidRange(9, 13));
    verify_range("3", splitter.get(3), DocidRange(13, 16));
}

TEST(DocidRangeSchedulerTest, require_that_the_docid_range_splitter_can_split_an_empty_range)
{
    DocidRangeSplitter splitter(DocidRange(5, 5), 2);
    verify_range("0", splitter.get(0), DocidRange(5, 5));
    verify_range("1", splitter.get(1), DocidRange(5, 5));
}

TEST(DocidRangeSchedulerTest, require_that_the_docid_range_splitter_can_split_a_range_into_more_parts_than_values)
{
    DocidRangeSplitter splitter(DocidRange(1, 4), 4);
    verify_range("0", splitter.get(0), DocidRange(1, 2));
    verify_range("1", splitter.get(1), DocidRange(2, 3));
    verify_range("2", splitter.get(2), DocidRange(3, 4));
    verify_range("3", splitter.get(3), DocidRange(4, 4));
}

TEST(DocidRangeSchedulerTest, require_that_the_docid_range_splitter_gives_empty_ranges_if_accessed_with_too_high_index)
{
    DocidRangeSplitter splitter(DocidRange(1, 4), 3);
    verify_range("0", splitter.get(0), DocidRange(1, 2));
    verify_range("1", splitter.get(1), DocidRange(2, 3));
    verify_range("2", splitter.get(2), DocidRange(3, 4));
    verify_range("3", splitter.get(3), DocidRange(4, 4));
    verify_range("100", splitter.get(100), DocidRange(4, 4));
}

//-----------------------------------------------------------------------------

TEST(DocidRangeSchedulerTest, require_that_the_partition_scheduler_acts_as_expected)
{
    PartitionDocidRangeScheduler scheduler(4, 16);
    EXPECT_EQ(scheduler.total_size(0), 4u);
    EXPECT_EQ(scheduler.total_size(1), 4u);
    EXPECT_EQ(scheduler.total_size(2), 4u);
    EXPECT_EQ(scheduler.total_size(3), 3u);
    EXPECT_EQ(scheduler.unassigned_size(), 0u);
    verify_range("first0", scheduler.first_range(0), DocidRange(1, 5));
    verify_range("first1", scheduler.first_range(1), DocidRange(5, 9));
    verify_range("first2", scheduler.first_range(2), DocidRange(9, 13));
    verify_range("first3", scheduler.first_range(3), DocidRange(13, 16));
    verify_range("next0", scheduler.next_range(0), DocidRange());
    verify_range("next1", scheduler.next_range(1), DocidRange());
    verify_range("next2", scheduler.next_range(2), DocidRange());
    verify_range("next3",scheduler.next_range(3), DocidRange());
}

TEST(DocidRangeSchedulerTest, require_that_the_partition_scheduler_protects_against_documents_underflow)
{
    PartitionDocidRangeScheduler scheduler(2, 0);
    EXPECT_EQ(scheduler.total_size(0), 0u);
    EXPECT_EQ(scheduler.total_size(1), 0u);
    EXPECT_EQ(scheduler.unassigned_size(), 0u);
    verify_range("first0", scheduler.first_range(0), DocidRange(1,1));
    verify_range("first1", scheduler.first_range(1), DocidRange(1,1));
    verify_range("next0", scheduler.next_range(0), DocidRange());
    verify_range("next1", scheduler.next_range(1), DocidRange());
}

//-----------------------------------------------------------------------------

TEST(DocidRangeSchedulerTest, require_that_the_task_scheduler_acts_as_expected)
{
    TaskDocidRangeScheduler scheduler(2, 5, 20);
    EXPECT_EQ(scheduler.unassigned_size(), 19u);
    EXPECT_EQ(scheduler.total_size(0), 0u);
    EXPECT_EQ(scheduler.total_size(1), 0u);
    verify_range("first1", scheduler.first_range(1), DocidRange(1, 5));
    verify_range("first0", scheduler.first_range(0), DocidRange(5, 9));
    verify_range("next0", scheduler.next_range(0), DocidRange(9, 13));
    EXPECT_EQ(scheduler.unassigned_size(), 7u);
    verify_range("next1", scheduler.next_range(1), DocidRange(13, 17));
    verify_range("2nd next0", scheduler.next_range(0), DocidRange(17, 20));
    verify_range("3rd next0", scheduler.next_range(0), DocidRange(20, 20));
    verify_range("2nd next1", scheduler.next_range(1), DocidRange(20, 20));
    EXPECT_EQ(scheduler.total_size(0), 11u);
    EXPECT_EQ(scheduler.total_size(1), 8u);
    EXPECT_EQ(scheduler.unassigned_size(), 0u);
}

TEST(DocidRangeSchedulerTest, require_that_the_task_scheduler_protects_against_documents_underflow)
{
    TaskDocidRangeScheduler scheduler(2, 4, 0);
    EXPECT_EQ(scheduler.total_size(0), 0u);
    EXPECT_EQ(scheduler.total_size(1), 0u);
    EXPECT_EQ(scheduler.unassigned_size(), 0u);
    verify_range("first0", scheduler.first_range(0), DocidRange(1,1));
    verify_range("first", scheduler.first_range(1), DocidRange(1,1));
    verify_range("next0", scheduler.next_range(0), DocidRange(1,1));
    verify_range("next1", scheduler.next_range(1), DocidRange(1,1));
}

//-----------------------------------------------------------------------------

TEST(DocidRangeSchedulerTest, require_that_the_adaptive_scheduler_starts_by_dividing_the_docid_space_equally)
{
    AdaptiveDocidRangeScheduler scheduler(4, 1, 16);
    EXPECT_EQ(scheduler.total_size(0), 4u);
    EXPECT_EQ(scheduler.total_size(1), 4u);
    EXPECT_EQ(scheduler.total_size(2), 4u);
    EXPECT_EQ(scheduler.total_size(3), 3u);
    EXPECT_EQ(scheduler.unassigned_size(), 0u);
    verify_range("first0", scheduler.first_range(0), DocidRange(1, 5));
    verify_range("first1", scheduler.first_range(1), DocidRange(5, 9));
    verify_range("first2", scheduler.first_range(2), DocidRange(9, 13));
    verify_range("first3", scheduler.first_range(3), DocidRange(13, 16));
}

TEST(DocidRangeSchedulerTest, require_that_the_adaptive_scheduler_terminates_when_all_workers_request_more_work)
{
    constexpr size_t num_threads = 4;
    AdaptiveDocidRangeScheduler f1(num_threads, 1, 16);
    TimeBomb f2(60);
    auto task = [&f1](Nexus& ctx) {
        auto thread_id = ctx.thread_id();
        (void) f1.first_range(thread_id);
        DocidRange range = f1.next_range(thread_id);
        EXPECT_TRUE(range.empty());
    };
    Nexus::run(num_threads, task);
}

namespace {

void wait_idle(const DocidRangeScheduler &scheduler, size_t wanted) {
    IdleObserver observer = scheduler.make_idle_observer();
    while (observer.get() != wanted) {
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }
}

}

TEST(DocidRangeSchedulerTest, require_that_the_adaptive_scheduler_enables_threads_to_share_work)
{
    constexpr size_t num_threads = 3;
    AdaptiveDocidRangeScheduler f1(num_threads, 1, 28);
    std::latch latch(num_threads);
    TimeBomb f2(60);
    auto task = [&f1,&latch](Nexus& ctx) {
        auto thread_id = ctx.thread_id();
        DocidRange range = f1.first_range(thread_id);
        if (thread_id == 0) {
            verify_range("thread0", range, DocidRange(1, 10));
        } else if (thread_id == 1) {
            verify_range("thread1", range, DocidRange(10, 19));
        } else {
            verify_range("thread" + std::to_string(thread_id), range, DocidRange(19, 28));
        }
        EXPECT_EQ(f1.total_size(thread_id), 9u);
        verify_range("before barrier", f1.share_range(thread_id, range), range);
        latch.arrive_and_wait();
        if (thread_id == 0) {
            verify_range("after barrier0", f1.next_range(thread_id), DocidRange(25,28));
        } else if (thread_id == 1) {
            wait_idle(f1, 1);
            verify_range("after barrier1", f1.next_range(thread_id), DocidRange(22,25));
        } else {
            wait_idle(f1, 2);
            verify_range("after barrier" + std::to_string(thread_id), f1.share_range(thread_id, range), DocidRange(19,22));
        }
        verify_range("exp empty" + std::to_string(thread_id), f1.next_range(thread_id), DocidRange());
        EXPECT_EQ(f1.total_size(0), 12u);
        EXPECT_EQ(f1.total_size(1), 12u);
        EXPECT_EQ(f1.total_size(2), 3u);
    };
    Nexus::run(num_threads, task);
}

TEST(DocidRangeSchedulerTest, require_that_the_adaptive_scheduler_protects_against_documents_underflow)
{
    constexpr size_t num_threads = 2;
    AdaptiveDocidRangeScheduler f1(num_threads, 1, 0);
    TimeBomb f2(60);
    auto task = [&f1](Nexus& ctx) {
        auto thread_id = ctx.thread_id();
        verify_range(std::to_string(thread_id), f1.first_range(thread_id), DocidRange());
        EXPECT_EQ(f1.total_size(thread_id), 0u);
        EXPECT_EQ(f1.unassigned_size(), 0u);
    };
    Nexus::run(num_threads, task);
}

TEST(DocidRangeSchedulerTest, require_that_the_adaptive_scheduler_respects_the_minimal_task_size)
{
    constexpr size_t num_threads = 2;
    AdaptiveDocidRangeScheduler f1(num_threads, 3, 21);
    TimeBomb f2(60);
    auto task = [&f1](Nexus& ctx) {
        auto thread_id = ctx.thread_id();
        EXPECT_EQ(f1.first_range(thread_id).size(), 10u);
        if (thread_id == 0) {
            verify_range("1st next0", f1.next_range(thread_id), DocidRange(18,21));
            verify_range("2nd next0", f1.next_range(thread_id), DocidRange());
        } else {
            wait_idle(f1, 1);
            // a range with size 5 will not be split
            verify_range("1st share1", f1.share_range(thread_id, DocidRange(16,21)), DocidRange(16,21));
            // a range with size 6 will be split
            verify_range("2nd share1", f1.share_range(thread_id, DocidRange(15,21)), DocidRange(15,18));
            verify_range("next1", f1.next_range(thread_id), DocidRange());
        }
    };
    Nexus::run(num_threads, task);
}

TEST(DocidRangeSchedulerTest, require_that_the_adaptive_scheduler_will_never_split_a_task_with_size_1)
{
    constexpr size_t num_threads = 2;
    AdaptiveDocidRangeScheduler f1(num_threads, 0, 21);
    TimeBomb f2(60);
    auto task = [&f1](Nexus& ctx) {
        auto thread_id = ctx.thread_id();
        EXPECT_EQ(f1.first_range(thread_id).size(), 10u);
        if (thread_id == 0) {
            verify_range("next0", f1.next_range(thread_id), DocidRange());
        } else {
            IdleObserver observer = f1.make_idle_observer();
            while (observer.get() == 0) {
                std::this_thread::sleep_for(std::chrono::milliseconds(1));
            }
            DocidRange small_range = DocidRange(20,21);
            verify_range("1st next1", f1.share_range(thread_id, small_range), small_range);
            verify_range("2nd next1", f1.next_range(thread_id), DocidRange());
        }
    };
    Nexus::run(num_threads, task);
}

TEST(DocidRangeSchedulerTest, require_that_the_adaptive_scheduler_can_leave_idle_workers_alone_due_to_minimal_task_size)
{
    constexpr size_t num_threads = 3;
    AdaptiveDocidRangeScheduler f1(num_threads, 3, 28);
    TimeBomb f2(60);
    auto task = [&f1](Nexus& ctx) {
        auto thread_id = ctx.thread_id();
        EXPECT_EQ(f1.first_range(thread_id).size(), 9u);
        if (thread_id == 0) {
            verify_range("next0", f1.next_range(thread_id), DocidRange());
        } else if (thread_id == 1) {
            wait_idle(f1, 1);
            verify_range("1st next1", f1.next_range(thread_id), DocidRange(24,28));
            verify_range("2nd next1", f1.next_range(thread_id), DocidRange());
        } else {
            wait_idle(f1, 2);
            verify_range("1st next2", f1.share_range(thread_id, DocidRange(20,28)), DocidRange(20,24));
            verify_range("2nd next2", f1.next_range(thread_id), DocidRange());
        }
        EXPECT_EQ(f1.total_size(0), 9u);
        EXPECT_EQ(f1.total_size(1), 13u);
        EXPECT_EQ(f1.total_size(2), 5u);
    };
    Nexus::run(num_threads, task);
}

TEST(DocidRangeSchedulerTest, require_that_the_adaptive_scheduler_handles_no_documents)
{
    constexpr size_t num_threads = 4;
    AdaptiveDocidRangeScheduler f1(num_threads, 1, 1);
    TimeBomb f2(60);
    auto task = [&f1](Nexus& ctx) {
        auto thread_id = ctx.thread_id();
        for (DocidRange docid_range = f1.first_range(thread_id);
             !docid_range.empty();
             docid_range = f1.next_range(thread_id))
        {
            FAIL() << "no threads should get any work";
        }
    };
    Nexus::run(num_threads, task);
}

TEST(DocidRangeSchedulerTest, require_that_the_adaptive_scheduler_handles_fewer_documents_than_threads)
{
    constexpr size_t num_threads = 4;
    AdaptiveDocidRangeScheduler f1(num_threads, 1, 3);
    TimeBomb f2(60);
    auto task = [&f1](Nexus& ctx) {
        auto thread_id = ctx.thread_id();
        for (DocidRange docid_range = f1.first_range(thread_id);
             !docid_range.empty();
             docid_range = f1.next_range(thread_id))
        {
            EXPECT_EQ(1, docid_range.size());
            EXPECT_GT(2, thread_id);
        }
    };
    Nexus::run(num_threads, task);
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
