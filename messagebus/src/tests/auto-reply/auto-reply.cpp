// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/routablequeue.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/simplereply.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace mbus;

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("auto-reply_test");
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
    TEST_DONE();
}
