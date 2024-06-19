// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/messagebus/emptyreply.h>

using namespace mbus;

TEST("emptyreply_test") {
    Reply::UP empty(new EmptyReply());
    EXPECT_TRUE(empty->isReply());
    EXPECT_TRUE(empty->getProtocol() == "");
    EXPECT_TRUE(empty->getType() == 0);
}

TEST_MAIN() { TEST_RUN_ALL(); }
