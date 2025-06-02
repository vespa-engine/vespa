// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/server/i_blockable_maintenance_job.h>
#include <vespa/searchcore/proton/server/move_operation_limiter.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <ostream>
#include <queue>

using namespace proton;

namespace proton {

void PrintTo(IBlockableMaintenanceJob::BlockedReason reason, std::ostream* os) {
    using BlockedReason = IBlockableMaintenanceJob::BlockedReason;
    switch  (reason) {
        case BlockedReason::RESOURCE_LIMITS:
            *os << "RESOURCE_LIMITS";
            break;
        case BlockedReason::FROZEN_BUCKET:
            *os << "FROZEN_BUCKET";
            break;
        case BlockedReason::CLUSTER_STATE:
            *os << "CLUSTER_STATE";
            break;
        case BlockedReason::OUTSTANDING_OPS:
            *os << "OUTSTANDING_OPS";
            break;
        case BlockedReason::DRAIN_OUTSTANDING_OPS:
            *os << "DRAIN_OUTSTANDING_OPS";
            break;
        default:
            ;
    }
}

}

namespace move_operation_limiter_test {

struct MyBlockableMaintenanceJob : public IBlockableMaintenanceJob {
    bool blocked;
    BlockedReason expected_blocked_reason;
    MyBlockableMaintenanceJob()
        : IBlockableMaintenanceJob("my_job", 1s, 1s),
          blocked(false),
          expected_blocked_reason(BlockedReason::OUTSTANDING_OPS)
    {}
    void setBlocked(BlockedReason reason) override {
        ASSERT_EQ(expected_blocked_reason, reason);
        EXPECT_FALSE(blocked);
        blocked = true;
    }
    void unBlock(BlockedReason reason) override {
        ASSERT_EQ(expected_blocked_reason, reason);
        EXPECT_TRUE(blocked);
        blocked = false;
    }
    void got_token(std::shared_ptr<MaintenanceJobToken>, bool) override { }
    bool run() override { return true; }
    void onStop() override { }
    void set_expected_blocked_reason(BlockedReason reason) noexcept {
        expected_blocked_reason = reason;
    }
};

struct MoveOperationLimiterTest : public ::testing::Test {
    using OpsQueue = std::queue<std::shared_ptr<vespalib::IDestructorCallback>>;
    using MoveOperationLimiterSP = std::shared_ptr<MoveOperationLimiter>;
    static constexpr uint32_t max_outstanding_ops = 2;

    MyBlockableMaintenanceJob job;
    MoveOperationLimiterSP limiter;
    OpsQueue ops;
    MoveOperationLimiterTest();
    ~MoveOperationLimiterTest() override;
    void beginOp() { ops.push(limiter->beginOperation()); }
    void endOp() { ops.pop(); }
    void clearJob() { limiter->clearJob(); }
    void clearLimiter() { limiter = MoveOperationLimiterSP(); }
    void assertAboveLimit(const std::string& label) const {
        SCOPED_TRACE(label);
        EXPECT_TRUE(limiter->isAboveLimit());
        EXPECT_TRUE(job.blocked);
    }
    void assertBelowLimit(const std::string& label) const {
        SCOPED_TRACE(label);
        EXPECT_FALSE(limiter->isAboveLimit());
        EXPECT_FALSE(job.blocked);
    }
};

MoveOperationLimiterTest::MoveOperationLimiterTest()
    : job(),
      limiter(std::make_shared<MoveOperationLimiter>(&job, max_outstanding_ops)),
      ops()
{}

MoveOperationLimiterTest::~MoveOperationLimiterTest() = default;

TEST_F(MoveOperationLimiterTest, require_that_hasPending_reflects_if_any_jobs_are_outstanding)
{
    EXPECT_FALSE(limiter->hasPending());
    beginOp();
    EXPECT_TRUE(limiter->hasPending());
    endOp();
    EXPECT_FALSE(limiter->hasPending());
}

TEST_F(MoveOperationLimiterTest, require_that_job_is_blocked_and_unblocked_when_crossing_max_outstanding_ops_boundaries)
{
    beginOp();
    assertBelowLimit("1");
    beginOp();
    assertAboveLimit("2");
    beginOp();
    assertAboveLimit("3");
    endOp();
    assertAboveLimit("4");
    endOp();
    assertBelowLimit("5");
    endOp();
    assertBelowLimit("6");
}

TEST_F(MoveOperationLimiterTest, require_that_cleared_job_is_not_blocked_when_crossing_max_ops_boundary)
{
    beginOp();
    clearJob();
    beginOp();
    EXPECT_FALSE(job.blocked);
    EXPECT_TRUE(limiter->isAboveLimit());
}

TEST_F(MoveOperationLimiterTest, require_that_cleared_job_is_not_unblocked_when_crossing_max_ops_boundary)
{
    beginOp();
    beginOp();
    assertAboveLimit("1");
    clearJob();
    endOp();
    EXPECT_TRUE(job.blocked);
    EXPECT_FALSE(limiter->isAboveLimit());
}

TEST_F(MoveOperationLimiterTest, require_that_destructor_callback_has_reference_to_limiter_via_shared_ptr)
{
    beginOp();
    beginOp();
    assertAboveLimit("1");
    clearLimiter();
    endOp();
    EXPECT_FALSE(job.blocked);
}

TEST_F(MoveOperationLimiterTest, require_that_drain_works)
{
    job.set_expected_blocked_reason(IBlockableMaintenanceJob::BlockedReason::DRAIN_OUTSTANDING_OPS);
    beginOp();
    ASSERT_FALSE(limiter->drain());
    EXPECT_TRUE(job.blocked);
    endOp();
    EXPECT_FALSE(job.blocked);
    ASSERT_TRUE(limiter->drain());
}

}
