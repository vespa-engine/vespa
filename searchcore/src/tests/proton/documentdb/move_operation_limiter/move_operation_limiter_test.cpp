// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("move_operation_limiter_test");

#include <vespa/searchcore/proton/server/i_blockable_maintenance_job.h>
#include <vespa/searchcore/proton/server/move_operation_limiter.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <queue>

using namespace proton;

struct MyBlockableMaintenanceJob : public IBlockableMaintenanceJob {
    bool blocked;
    MyBlockableMaintenanceJob()
        : IBlockableMaintenanceJob("my_job", 1s, 1s),
          blocked(false)
    {}
    void setBlocked(BlockedReason reason) override {
        ASSERT_TRUE(reason == BlockedReason::OUTSTANDING_OPS);
        EXPECT_FALSE(blocked);
        blocked = true;
    }
    void unBlock(BlockedReason reason) override {
        ASSERT_TRUE(reason == BlockedReason::OUTSTANDING_OPS);
        EXPECT_TRUE(blocked);
        blocked = false;
    }
    bool run() override { return true; }
};

struct Fixture {
    using OpsQueue = std::queue<std::shared_ptr<vespalib::IDestructorCallback>>;
    using MoveOperationLimiterSP = std::shared_ptr<MoveOperationLimiter>;

    MyBlockableMaintenanceJob job;
    MoveOperationLimiterSP limiter;
    OpsQueue ops;
    Fixture(uint32_t maxOutstandingOps = 2)
        : job(),
          limiter(std::make_shared<MoveOperationLimiter>(&job, maxOutstandingOps)),
          ops()
    {}
    void beginOp() { ops.push(limiter->beginOperation()); }
    void endOp() { ops.pop(); }
    void clearJob() { limiter->clearJob(); }
    void clearLimiter() { limiter = MoveOperationLimiterSP(); }
    void assertAboveLimit() const {
        EXPECT_TRUE(limiter->isAboveLimit());
        EXPECT_TRUE(job.blocked);
    }
    void assertBelowLimit() const {
        EXPECT_FALSE(limiter->isAboveLimit());
        EXPECT_FALSE(job.blocked);
    }
};

TEST_F("require that hasPending reflects if any jobs are outstanding", Fixture)
{
    EXPECT_FALSE(f.limiter->hasPending());
    f.beginOp();
    EXPECT_TRUE(f.limiter->hasPending());
    f.endOp();
    EXPECT_FALSE(f.limiter->hasPending());
}

TEST_F("require that job is blocked / unblocked when crossing max outstanding ops boundaries", Fixture)
{
    f.beginOp();
    TEST_DO(f.assertBelowLimit());
    f.beginOp();
    TEST_DO(f.assertAboveLimit());
    f.beginOp();
    TEST_DO(f.assertAboveLimit());
    f.endOp();
    TEST_DO(f.assertAboveLimit());
    f.endOp();
    TEST_DO(f.assertBelowLimit());
    f.endOp();
    TEST_DO(f.assertBelowLimit());
}

TEST_F("require that cleared job is not blocked when crossing max ops boundary", Fixture)
{
    f.beginOp();
    f.clearJob();
    f.beginOp();
    EXPECT_FALSE(f.job.blocked);
    EXPECT_TRUE(f.limiter->isAboveLimit());
}

TEST_F("require that cleared job is not unblocked when crossing max ops boundary", Fixture)
{
    f.beginOp();
    f.beginOp();
    TEST_DO(f.assertAboveLimit());
    f.clearJob();
    f.endOp();
    EXPECT_TRUE(f.job.blocked);
    EXPECT_FALSE(f.limiter->isAboveLimit());
}

TEST_F("require that destructor callback has reference to limiter via shared ptr", Fixture)
{
    f.beginOp();
    f.beginOp();
    TEST_DO(f.assertAboveLimit());
    f.clearLimiter();
    f.endOp();
    EXPECT_FALSE(f.job.blocked);
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
