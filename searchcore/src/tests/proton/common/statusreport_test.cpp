// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchcore/proton/common/statusreport.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace proton {

TEST(StatusReportTest, require_that_default_status_report_works)
{
    StatusReport sr(StatusReport::Params("foo"));

    EXPECT_EQ("foo", sr.getComponent());
    EXPECT_EQ(StatusReport::DOWN, sr.getState());
    EXPECT_EQ("", sr.getInternalState());
    EXPECT_EQ("", sr.getInternalConfigState());
    EXPECT_FALSE(sr.hasProgress());
    EXPECT_EQ("", sr.getMessage());
    EXPECT_EQ("state=", sr.getInternalStatesStr());
}

TEST(StatusReportTest, require_that_custom_status_report_works)
{
    StatusReport sr(StatusReport::Params("foo").
            state(StatusReport::UPOK).
            internalState("mystate").
            internalConfigState("myconfigstate").
            progress(65).
            message("mymessage"));

    EXPECT_EQ("foo", sr.getComponent());
    EXPECT_EQ(StatusReport::UPOK, sr.getState());
    EXPECT_EQ("mystate", sr.getInternalState());
    EXPECT_EQ("myconfigstate", sr.getInternalConfigState());
    EXPECT_TRUE(sr.hasProgress());
    EXPECT_EQ(65, sr.getProgress());
    EXPECT_EQ("mymessage", sr.getMessage());
    EXPECT_EQ("state=mystate configstate=myconfigstate", sr.getInternalStatesStr());
}

}  // namespace proton
