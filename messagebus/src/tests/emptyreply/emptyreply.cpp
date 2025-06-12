// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/messagebus/emptyreply.h>

using namespace mbus;

TEST(EmptyResultTest, emptyreply_test) {
    Reply::UP empty(new EmptyReply());
    EXPECT_TRUE(empty->isReply());
    EXPECT_TRUE(empty->getProtocol() == "");
    EXPECT_TRUE(empty->getType() == 0);
}

GTEST_MAIN_RUN_ALL_TESTS()
