// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lid_space_jobtest.h"

using BlockedReason = IBlockableMaintenanceJob::BlockedReason;

TEST_F(JobTest, handler_name_is_used_as_part_of_job_name)
{
    EXPECT_EQ("lid_space_compaction.myhandler", _job->getName());
}

TEST_F(JobTest, no_move_operation_is_created_if_lid_bloat_factor_is_below_limit)
{
    // 20% bloat < 30% allowed bloat
    addStats(10, {1,3,4,5,6,7,9}, {{9,2}});
    EXPECT_TRUE(run());
    assertNoWorkDone();
}

TEST_F(JobTest, no_move_operation_is_created_if_lid_bloat_is_below_limit)
{
    init(3, 0.1);
    // 20% bloat >= 10% allowed bloat BUT lid bloat (2) < allowed lid bloat (3)
    addStats(10, {1,3,4,5,6,7,9}, {{9,2}});
    EXPECT_TRUE(run());
    assertNoWorkDone();
}

TEST_F(JobTest, no_move_operation_is_created_and_compaction_is_initiated)
{
    // no documents to move: lowestFreeLid(7) > highestUsedLid(6)
    addStats(10, {1,2,3,4,5,6}, {{6,7}});

    // must scan to find that no documents should be moved
    endScan().compact();
    assertJobContext(0, 0, 0, 7, 1);
}

TEST_F(JobTest, one_move_operation_is_created_and_compaction_is_initiated)
{
    setupOneDocumentToCompact();
    EXPECT_FALSE(run()); // scan
    assertOneDocumentCompacted();
}

TEST_F(JobTest, job_returns_false_when_multiple_move_operations_or_compaction_are_needed)
{
    setupThreeDocumentsToCompact();
    EXPECT_FALSE(run());
    assertJobContext(2, 9, 1, 0, 0);
    EXPECT_FALSE(run());
    assertJobContext(3, 8, 2, 0, 0);
    EXPECT_FALSE(run());
    assertJobContext(4, 7, 3, 0, 0);
    endScan().compact();
    assertJobContext(4, 7, 3, 7, 1);
}

TEST_F(JobTest, job_can_restart_documents_scan_if_lid_bloat_is_still_to_large)
{
    init(ALLOWED_LID_BLOAT, ALLOWED_LID_BLOAT_FACTOR);
    addMultiStats(10, {{1,3,4,5,6,9},{1,2,4,5,6,8}},
                  {{9,2},   // 30% bloat: move 9 -> 2
                   {8,3},   // move 8 -> 3 (this should trigger rescan as the set of used docs have changed)
                   {6,7}}); // no documents to move

    EXPECT_FALSE(run());  // move 9 -> 2
    assertJobContext(2, 9, 1, 0, 0);
    EXPECT_EQ(1u, _handler->_iteratorCnt);
    // We simulate that the set of used docs have changed between these 2 runs
    EXPECT_FALSE(run()); // move 8 -> 3
    endScan();
    assertJobContext(3, 8, 2, 0, 0);
    EXPECT_EQ(2u, _handler->_iteratorCnt);
    endScan().compact();
    assertJobContext(3, 8, 2, 7, 1);
}

TEST_F(JobTest, held_lid_is_not_considered_free_and_blocks_job)
{
    // Lid 1 on hold or pendingHold, i.e. neither free nor used.
    addMultiStats(3, {{2}}, {{2, 3}});
    EXPECT_TRUE(run());
    assertNoWorkDone();
}

TEST_F(JobTest, held_lid_is_not_considered_free_with_only_compact)
{
    // Lid 1 on hold or pendingHold, i.e. neither free nor used.
    addMultiStats(10, {{2}}, {{2, 3}});
    EXPECT_FALSE(run());
    assertNoWorkDone();
    compact();
    assertJobContext(0, 0, 0, 3, 1);
}

TEST_F(JobTest, held_lids_are_not_considered_free_with_one_move)
{
    // Lids 1,2,3 on hold or pendingHold, i.e. neither free nor used.
    addMultiStats(10, {{5}}, {{5, 4}, {4, 5}});
    EXPECT_FALSE(run());
    assertJobContext(4, 5, 1, 0, 0);
    endScan().compact();
    assertJobContext(4, 5, 1, 5, 1);
}

TEST_F(JobTest, resource_starvation_blocks_lid_space_compaction)
{
    setupOneDocumentToCompact();
    _diskMemUsageNotifier.notify({{100, 0}, {100, 101}});
    EXPECT_TRUE(run()); // scan
    assertNoWorkDone();
}

TEST_F(JobTest, ending_resource_starvation_resumes_lid_space_compaction)
{
    setupOneDocumentToCompact();
    _diskMemUsageNotifier.notify({{100, 0}, {100, 101}});
    EXPECT_TRUE(run()); // scan
    assertNoWorkDone();
    _diskMemUsageNotifier.notify({{100, 0}, {100, 0}});
    assertOneDocumentCompacted();
}

TEST_F(JobTest, resource_limit_factor_adjusts_limit)
{
    init(ALLOWED_LID_BLOAT, ALLOWED_LID_BLOAT_FACTOR, 1.05);
    setupOneDocumentToCompact();
    _diskMemUsageNotifier.notify({{100, 0}, {100, 101}});
    EXPECT_FALSE(run()); // scan
    assertOneDocumentCompacted();
}

TEST_F(JobTest, delay_is_set_based_on_interval_and_is_max_300_secs)
{
    init_with_interval(301s);
    EXPECT_EQ(300s, _job->getDelay());
    EXPECT_EQ(301s, _job->getInterval());
}

TEST_F(JobTest, delay_is_set_based_on_interval_and_can_be_less_than_300_secs)
{
    init_with_interval(299s);
    EXPECT_EQ(299s, _job->getDelay());
    EXPECT_EQ(299s, _job->getInterval());
}

TEST_F(JobTest, job_is_disabled_when_node_is_retired)
{
    init_with_node_retired(true);
    setupOneDocumentToCompact();
    EXPECT_TRUE(run()); // not runnable, no work to do
    assertNoWorkDone();
}

TEST_F(JobTest, job_is_disabled_when_node_becomes_retired)
{
    init_with_node_retired(false);
    setupOneDocumentToCompact();
    notifyNodeRetired(true);
    EXPECT_TRUE(run()); // not runnable, no work to do
    assertNoWorkDone();
}

TEST_F(JobTest, job_is_re_enabled_when_node_is_no_longer_retired)
{
    init_with_node_retired(true);
    setupOneDocumentToCompact();
    EXPECT_TRUE(run()); // not runnable, no work to do
    assertNoWorkDone();
    notifyNodeRetired(false); // triggers running of job
    assertOneDocumentCompacted();
}

TEST_F(JobDisabledByRemoveOpsTest, config_is_propagated_to_remove_operations_rate_tracker)
{
    auto& remove_batch_tracker = _handler->_rm_listener->get_remove_batch_tracker();
    EXPECT_EQ(vespalib::from_s(21.0), remove_batch_tracker.get_time_budget_per_op());
    EXPECT_EQ(vespalib::from_s(21.0), remove_batch_tracker.get_time_budget_window());

    auto& remove_tracker = _handler->_rm_listener->get_remove_tracker();
    EXPECT_EQ(vespalib::from_s(20.0), remove_tracker.get_time_budget_per_op());
    EXPECT_EQ(vespalib::from_s(20.0), remove_tracker.get_time_budget_window());
}

TEST_F(JobDisabledByRemoveOpsTest, job_is_disabled_while_remove_batch_is_ongoing)
{
    job_is_disabled_while_remove_ops_are_ongoing(true);
}

TEST_F(JobDisabledByRemoveOpsTest, job_becomes_disabled_if_remove_batch_starts)
{
    job_becomes_disabled_if_remove_ops_starts(true);
}

TEST_F(JobDisabledByRemoveOpsTest, job_is_re_enabled_when_remove_batch_is_no_longer_ongoing)
{
    job_is_re_enabled_when_remove_ops_are_no_longer_ongoing(true);
}

TEST_F(JobDisabledByRemoveOpsTest, job_is_disabled_while_removes_are_ongoing)
{
    job_is_disabled_while_remove_ops_are_ongoing(false);
}

TEST_F(JobDisabledByRemoveOpsTest, job_becomes_disabled_if_removes_start)
{
    job_becomes_disabled_if_remove_ops_starts(false);
}

TEST_F(JobDisabledByRemoveOpsTest, job_is_re_enabled_when_removes_are_no_longer_ongoing)
{
    job_is_re_enabled_when_remove_ops_are_no_longer_ongoing(false);
}


TEST_F(MaxOutstandingJobTest, job_is_blocked_if_it_has_too_many_outstanding_move_operations_with_max_1)
{
    init(1);
    setupThreeDocumentsToCompact();

    assertRunToBlocked();
    assertJobContext(2, 9, 1, 0, 0);
    assertRunToBlocked();
    assertJobContext(2, 9, 1, 0, 0);

    unblockJob(1);
    assertRunToBlocked();
    assertJobContext(3, 8, 2, 0, 0);

    unblockJob(2);
    assertRunToBlocked();
    assertJobContext(4, 7, 3, 0, 0);

    unblockJob(3);
    endScan().compact();
    assertJobContext(4, 7, 3, 7, 1);
}

TEST_F(MaxOutstandingJobTest, job_is_blocked_if_it_has_too_many_outstanding_move_operations_with_max_2)
{
    init(2);
    setupThreeDocumentsToCompact();

    assertRunToNotBlocked();
    assertJobContext(2, 9, 1, 0, 0);
    assertRunToBlocked();
    assertJobContext(3, 8, 2, 0, 0);

    unblockJob(1);
    assertRunToNotBlocked();
    assertJobContext(4, 7, 3, 0, 0);
    unblockJob(1);
    endScan().compact();
    assertJobContext(4, 7, 3, 7, 1);
    sync();
}

GTEST_MAIN_RUN_ALL_TESTS()
