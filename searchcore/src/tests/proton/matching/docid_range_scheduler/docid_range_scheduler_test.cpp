// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/time_bomb.h>
#include <vespa/searchcore/proton/matching/docid_range_scheduler.h>

using namespace proton::matching;
using vespalib::TimeBomb;

void verify_range(DocidRange a, DocidRange b) {
    EXPECT_EQUAL(a.begin, b.begin);
    EXPECT_EQUAL(a.end, b.end);
}

//-----------------------------------------------------------------------------

TEST("require that default docid range constructor creates and empty range") {
    EXPECT_TRUE(DocidRange().empty());
    EXPECT_EQUAL(DocidRange().size(), 0u);
}

TEST("require that docid range ensures end is not less than begin") {
    EXPECT_EQUAL(DocidRange(10, 20).size(), 10u);
    EXPECT_TRUE(!DocidRange(10, 20).empty());
    EXPECT_EQUAL(DocidRange(10, 20).begin, 10u);
    EXPECT_EQUAL(DocidRange(10, 20).end, 20u);
    EXPECT_EQUAL(DocidRange(20, 10).size(), 0u);
    EXPECT_TRUE(DocidRange(20, 10).empty());
    EXPECT_EQUAL(DocidRange(20, 10).begin, 20u);
    EXPECT_EQUAL(DocidRange(20, 10).end, 20u);
}

//-----------------------------------------------------------------------------

TEST("require that default constructed IdleObserver is always zero") {
    IdleObserver observer;
    EXPECT_TRUE(observer.is_always_zero());
    EXPECT_EQUAL(0u, observer.get());
}

TEST("require that IdleObserver can observe an atomic size_t value") {
    std::atomic<size_t> idle(0);
    IdleObserver observer(idle);
    EXPECT_TRUE(!observer.is_always_zero());
    EXPECT_EQUAL(0u, observer.get());
    idle = 10;
    EXPECT_EQUAL(10u, observer.get());
}

//-----------------------------------------------------------------------------

TEST("require that the docid range splitter can split a docid range") {
    DocidRangeSplitter splitter(DocidRange(1, 16), 4);
    TEST_DO(verify_range(splitter.get(0), DocidRange(1, 5)));
    TEST_DO(verify_range(splitter.get(1), DocidRange(5, 9)));
    TEST_DO(verify_range(splitter.get(2), DocidRange(9, 13)));
    TEST_DO(verify_range(splitter.get(3), DocidRange(13, 16)));
}

TEST("require that the docid range splitter can split an empty range") {
    DocidRangeSplitter splitter(DocidRange(5, 5), 2);
    TEST_DO(verify_range(splitter.get(0), DocidRange(5, 5)));
    TEST_DO(verify_range(splitter.get(1), DocidRange(5, 5)));
}

TEST("require that the docid range splitter can split a range into more parts than values") {
    DocidRangeSplitter splitter(DocidRange(1, 4), 4);
    TEST_DO(verify_range(splitter.get(0), DocidRange(1, 2)));
    TEST_DO(verify_range(splitter.get(1), DocidRange(2, 3)));
    TEST_DO(verify_range(splitter.get(2), DocidRange(3, 4)));
    TEST_DO(verify_range(splitter.get(3), DocidRange(4, 4)));
}

TEST("require that the docid range splitter gives empty ranges if accessed with too high index") {
    DocidRangeSplitter splitter(DocidRange(1, 4), 3);
    TEST_DO(verify_range(splitter.get(0), DocidRange(1, 2)));
    TEST_DO(verify_range(splitter.get(1), DocidRange(2, 3)));
    TEST_DO(verify_range(splitter.get(2), DocidRange(3, 4)));
    TEST_DO(verify_range(splitter.get(3), DocidRange(4, 4)));
    TEST_DO(verify_range(splitter.get(100), DocidRange(4, 4)));
}

//-----------------------------------------------------------------------------

TEST("require that the partition scheduler acts as expected") {
    PartitionDocidRangeScheduler scheduler(4, 16);
    EXPECT_EQUAL(scheduler.total_size(0), 4u);
    EXPECT_EQUAL(scheduler.total_size(1), 4u);
    EXPECT_EQUAL(scheduler.total_size(2), 4u);
    EXPECT_EQUAL(scheduler.total_size(3), 3u);
    EXPECT_EQUAL(scheduler.unassigned_size(), 0u);
    TEST_DO(verify_range(scheduler.first_range(0), DocidRange(1, 5)));
    TEST_DO(verify_range(scheduler.first_range(1), DocidRange(5, 9)));
    TEST_DO(verify_range(scheduler.first_range(2), DocidRange(9, 13)));
    TEST_DO(verify_range(scheduler.first_range(3), DocidRange(13, 16)));
    TEST_DO(verify_range(scheduler.next_range(0), DocidRange()));
    TEST_DO(verify_range(scheduler.next_range(1), DocidRange()));
    TEST_DO(verify_range(scheduler.next_range(2), DocidRange()));
    TEST_DO(verify_range(scheduler.next_range(3), DocidRange()));
}

TEST("require that the partition scheduler protects against documents underflow") {
    PartitionDocidRangeScheduler scheduler(2, 0);
    EXPECT_EQUAL(scheduler.total_size(0), 0u);
    EXPECT_EQUAL(scheduler.total_size(1), 0u);
    EXPECT_EQUAL(scheduler.unassigned_size(), 0u);
    TEST_DO(verify_range(scheduler.first_range(0), DocidRange(1,1)));
    TEST_DO(verify_range(scheduler.first_range(1), DocidRange(1,1)));
    TEST_DO(verify_range(scheduler.next_range(0), DocidRange()));
    TEST_DO(verify_range(scheduler.next_range(1), DocidRange()));
}

//-----------------------------------------------------------------------------

TEST("require that the task scheduler acts as expected") {
    TaskDocidRangeScheduler scheduler(2, 5, 20);
    EXPECT_EQUAL(scheduler.unassigned_size(), 19u);
    EXPECT_EQUAL(scheduler.total_size(0), 0u);
    EXPECT_EQUAL(scheduler.total_size(1), 0u);
    TEST_DO(verify_range(scheduler.first_range(1), DocidRange(1, 5)));
    TEST_DO(verify_range(scheduler.first_range(0), DocidRange(5, 9)));
    TEST_DO(verify_range(scheduler.next_range(0), DocidRange(9, 13)));
    EXPECT_EQUAL(scheduler.unassigned_size(), 7u);
    TEST_DO(verify_range(scheduler.next_range(1), DocidRange(13, 17)));
    TEST_DO(verify_range(scheduler.next_range(0), DocidRange(17, 20)));
    TEST_DO(verify_range(scheduler.next_range(0), DocidRange(20, 20)));
    TEST_DO(verify_range(scheduler.next_range(1), DocidRange(20, 20)));
    EXPECT_EQUAL(scheduler.total_size(0), 11u);
    EXPECT_EQUAL(scheduler.total_size(1), 8u);
    EXPECT_EQUAL(scheduler.unassigned_size(), 0u);
}

TEST("require that the task scheduler protects against documents underflow") {
    TaskDocidRangeScheduler scheduler(2, 4, 0);
    EXPECT_EQUAL(scheduler.total_size(0), 0u);
    EXPECT_EQUAL(scheduler.total_size(1), 0u);
    EXPECT_EQUAL(scheduler.unassigned_size(), 0u);
    TEST_DO(verify_range(scheduler.first_range(0), DocidRange(1,1)));
    TEST_DO(verify_range(scheduler.first_range(1), DocidRange(1,1)));
    TEST_DO(verify_range(scheduler.next_range(0), DocidRange(1,1)));
    TEST_DO(verify_range(scheduler.next_range(1), DocidRange(1,1)));
}

//-----------------------------------------------------------------------------

TEST("require that the adaptive scheduler starts by dividing the docid space equally") {
    AdaptiveDocidRangeScheduler scheduler(4, 1, 16);
    EXPECT_EQUAL(scheduler.total_size(0), 4u);
    EXPECT_EQUAL(scheduler.total_size(1), 4u);
    EXPECT_EQUAL(scheduler.total_size(2), 4u);
    EXPECT_EQUAL(scheduler.total_size(3), 3u);
    EXPECT_EQUAL(scheduler.unassigned_size(), 0u);    
    TEST_DO(verify_range(scheduler.first_range(0), DocidRange(1, 5)));
    TEST_DO(verify_range(scheduler.first_range(1), DocidRange(5, 9)));
    TEST_DO(verify_range(scheduler.first_range(2), DocidRange(9, 13)));
    TEST_DO(verify_range(scheduler.first_range(3), DocidRange(13, 16)));
}

TEST_MT_FF("require that the adaptive scheduler terminates when all workers request more work",
           4, AdaptiveDocidRangeScheduler(num_threads, 1, 16), TimeBomb(60))
{
    (void) f1.first_range(thread_id);
    DocidRange range = f1.next_range(thread_id);
    EXPECT_TRUE(range.empty());
}

void wait_idle(const DocidRangeScheduler &scheduler, size_t wanted) {
    IdleObserver observer = scheduler.make_idle_observer();
    while (observer.get() != wanted) {
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }
}

TEST_MT_FF("require that the adaptive scheduler enables threads to share work",
           3, AdaptiveDocidRangeScheduler(num_threads, 1, 28), TimeBomb(60))
{
    DocidRange range = f1.first_range(thread_id);
    if (thread_id == 0) {
        TEST_DO(verify_range(range, DocidRange(1,10)));
    } else if (thread_id == 1) {
        TEST_DO(verify_range(range, DocidRange(10,19)));
    } else {
        TEST_DO(verify_range(range, DocidRange(19,28)));
    }
    EXPECT_EQUAL(f1.total_size(thread_id), 9u);
    TEST_DO(verify_range(f1.share_range(thread_id, range), range));
    TEST_BARRIER();
    if (thread_id == 0) {
        TEST_DO(verify_range(f1.next_range(thread_id), DocidRange(25,28)));
    } else if (thread_id == 1) {
        wait_idle(f1, 1);
        TEST_DO(verify_range(f1.next_range(thread_id), DocidRange(22,25)));
    } else {
        wait_idle(f1, 2);
        verify_range(f1.share_range(thread_id, range), DocidRange(19,22));
    }
    TEST_DO(verify_range(f1.next_range(thread_id), DocidRange()));
    EXPECT_EQUAL(f1.total_size(0), 12u);
    EXPECT_EQUAL(f1.total_size(1), 12u);
    EXPECT_EQUAL(f1.total_size(2), 3u);
}

TEST_MT_FF("require that the adaptive scheduler protects against documents underflow",
           2, AdaptiveDocidRangeScheduler(num_threads, 1, 0), TimeBomb(60))
{
    TEST_DO(verify_range(f1.first_range(thread_id), DocidRange()));
    EXPECT_EQUAL(f1.total_size(thread_id), 0u);
    EXPECT_EQUAL(f1.unassigned_size(), 0u);
}

TEST_MT_FF("require that the adaptive scheduler respects the minimal task size",
           2, AdaptiveDocidRangeScheduler(num_threads, 3, 21), TimeBomb(60))
{
    EXPECT_EQUAL(f1.first_range(thread_id).size(), 10u);
    if (thread_id == 0) {
        TEST_DO(verify_range(f1.next_range(thread_id), DocidRange(18,21)));
        TEST_DO(verify_range(f1.next_range(thread_id), DocidRange()));
    } else {
        wait_idle(f1, 1);
        // a range with size 5 will not be split
        TEST_DO(verify_range(f1.share_range(thread_id, DocidRange(16,21)), DocidRange(16,21)));
        // a range with size 6 will be split
        TEST_DO(verify_range(f1.share_range(thread_id, DocidRange(15,21)), DocidRange(15,18)));
        TEST_DO(verify_range(f1.next_range(thread_id), DocidRange()));
    }
}

TEST_MT_FF("require that the adaptive scheduler will never split a task with size 1",
           2, AdaptiveDocidRangeScheduler(num_threads, 0, 21), TimeBomb(60))
{
    EXPECT_EQUAL(f1.first_range(thread_id).size(), 10u);
    if (thread_id == 0) {
        TEST_DO(verify_range(f1.next_range(thread_id), DocidRange()));
    } else {
        IdleObserver observer = f1.make_idle_observer();
        while (observer.get() == 0) {
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
        }
        DocidRange small_range = DocidRange(20,21);
        verify_range(f1.share_range(thread_id, small_range), small_range);
        TEST_DO(verify_range(f1.next_range(thread_id), DocidRange()));
    }
}

TEST_MT_FF("require that the adaptive scheduler can leave idle workers alone due to minimal task size",
           3, AdaptiveDocidRangeScheduler(num_threads, 3, 28), TimeBomb(60))
{
    EXPECT_EQUAL(f1.first_range(thread_id).size(), 9u);
    if (thread_id == 0) {
        TEST_DO(verify_range(f1.next_range(thread_id), DocidRange()));
    } else if (thread_id == 1) {
        wait_idle(f1, 1);
        TEST_DO(verify_range(f1.next_range(thread_id), DocidRange(24,28)));
        TEST_DO(verify_range(f1.next_range(thread_id), DocidRange()));
    } else {
        wait_idle(f1, 2);
        verify_range(f1.share_range(thread_id, DocidRange(20,28)), DocidRange(20,24));
        TEST_DO(verify_range(f1.next_range(thread_id), DocidRange()));
    }
    EXPECT_EQUAL(f1.total_size(0), 9u);
    EXPECT_EQUAL(f1.total_size(1), 13u);
    EXPECT_EQUAL(f1.total_size(2), 5u);
}

TEST_MT_FF("require that the adaptive scheduler handles no documents",
           4, AdaptiveDocidRangeScheduler(num_threads, 1, 1), TimeBomb(60))
{
    for (DocidRange docid_range = f1.first_range(thread_id);
         !docid_range.empty();
         docid_range = f1.next_range(thread_id))
    {
        TEST_ERROR("no threads should get any work");
    }
}

TEST_MT_FF("require that the adaptive scheduler handles fewer documents than threads",
           4, AdaptiveDocidRangeScheduler(num_threads, 1, 3), TimeBomb(60))
{
    for (DocidRange docid_range = f1.first_range(thread_id);
         !docid_range.empty();
         docid_range = f1.next_range(thread_id))
    {
        EXPECT_TRUE(docid_range.size() == 1);
        EXPECT_TRUE(thread_id < 2);
    }
}

//-----------------------------------------------------------------------------

TEST_MAIN() { TEST_RUN_ALL(); }
