// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/routablequeue.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/simplereply.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace mbus;

TEST(AutoReplyTest, auto_reply_test) {
    RoutableQueue q;
    {
        Message::UP msg(new SimpleMessage("test"));
    }
    EXPECT_TRUE(q.size() == 0);
    {
        Message::UP msg(new SimpleMessage("test"));
        msg->pushHandler(q);
    }
    EXPECT_TRUE(q.size() == 1);
    {
        Reply::UP reply(new SimpleReply("test"));
    }
    EXPECT_TRUE(q.size() == 1);
    {
        Reply::UP reply(new SimpleReply("test"));
        reply->pushHandler(q);
    }
    EXPECT_TRUE(q.size() == 2);
}

GTEST_MAIN_RUN_ALL_TESTS()
