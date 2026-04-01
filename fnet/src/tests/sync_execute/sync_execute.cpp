// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fnet/iexecutable.h>
#include <vespa/fnet/transport.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/gate.h>
#include <vespa/vespalib/util/size_literals.h>

struct DoIt : public FNET_IExecutable {
    vespalib::Gate gate;
    void           execute() override { gate.countDown(); }
};

TEST(SyncExecuteTest, sync_execute) {
    DoIt           exe1;
    DoIt           exe2;
    DoIt           exe3;
    DoIt           exe4;
    DoIt           exe5;
    DoIt           exe6;
    FNET_Transport transport;
    ASSERT_TRUE(transport.execute(&exe1));
    ASSERT_TRUE(transport.Start());
    exe1.gate.await();
    ASSERT_TRUE(transport.execute(&exe2));
    transport.sync();
    ASSERT_TRUE(exe2.gate.getCount() == 0u);
    ASSERT_TRUE(transport.execute(&exe3));
    transport.ShutDown(false);
    uint32_t expect_cnt_4 = transport.execute(&exe4) ? 0 : 1;
    transport.sync();
    transport.WaitFinished();
    ASSERT_TRUE(!transport.execute(&exe5));
    transport.sync();
    ASSERT_TRUE(exe1.gate.getCount() == 0u);
    ASSERT_TRUE(exe2.gate.getCount() == 0u);
    ASSERT_TRUE(exe3.gate.getCount() == 0u);
    ASSERT_TRUE(exe4.gate.getCount() == expect_cnt_4);
    ASSERT_TRUE(exe5.gate.getCount() == 1u);
}

GTEST_MAIN_RUN_ALL_TESTS()
