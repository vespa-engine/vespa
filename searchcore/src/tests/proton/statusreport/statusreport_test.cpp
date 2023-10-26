// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchcore/proton/common/statusreport.h>

namespace proton {

TEST("require that default status report works")
{
    StatusReport sr(StatusReport::Params("foo"));

    EXPECT_EQUAL("foo", sr.getComponent());
    EXPECT_EQUAL(StatusReport::DOWN, sr.getState());
    EXPECT_EQUAL("", sr.getInternalState());
    EXPECT_EQUAL("", sr.getInternalConfigState());
    EXPECT_FALSE(sr.hasProgress());
    EXPECT_EQUAL("", sr.getMessage());
    EXPECT_EQUAL("state=", sr.getInternalStatesStr());
}

TEST("require that custom status report works")
{
    StatusReport sr(StatusReport::Params("foo").
            state(StatusReport::UPOK).
            internalState("mystate").
            internalConfigState("myconfigstate").
            progress(65).
            message("mymessage"));

    EXPECT_EQUAL("foo", sr.getComponent());
    EXPECT_EQUAL(StatusReport::UPOK, sr.getState());
    EXPECT_EQUAL("mystate", sr.getInternalState());
    EXPECT_EQUAL("myconfigstate", sr.getInternalConfigState());
    EXPECT_TRUE(sr.hasProgress());
    EXPECT_EQUAL(65, sr.getProgress());
    EXPECT_EQUAL("mymessage", sr.getMessage());
    EXPECT_EQUAL("state=mystate configstate=myconfigstate", sr.getInternalStatesStr());
}

}  // namespace proton

TEST_MAIN() { TEST_RUN_ALL(); }
