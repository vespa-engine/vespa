// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/testlib/receptor.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/simplereply.h>
#include <vespa/messagebus/message.h>
#include <vespa/messagebus/reply.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/error.h>

#include <vespa/vespalib/testkit/testapp.h>

using namespace mbus;
using namespace std::chrono_literals;

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("routable_test");

    {
        // Test message swap state.
        SimpleMessage foo("foo");
        Route fooRoute = Route::parse("foo");
        foo.setRoute(fooRoute);
        foo.setRetry(1);
        foo.setTimeReceivedNow();
        foo.setTimeRemaining(2ms);

        SimpleMessage bar("bar");
        Route barRoute = Route::parse("bar");
        bar.setRoute(barRoute);
        bar.setRetry(3);
        bar.setTimeReceivedNow();
        bar.setTimeRemaining(4ms);

        foo.swapState(bar);
        EXPECT_EQUAL(barRoute.toString(), foo.getRoute().toString());
        EXPECT_EQUAL(fooRoute.toString(), bar.getRoute().toString());
        EXPECT_EQUAL(3u, foo.getRetry());
        EXPECT_EQUAL(1u, bar.getRetry());
        EXPECT_TRUE(foo.getTimeReceived() >= bar.getTimeReceived());
        EXPECT_EQUAL(4ms, foo.getTimeRemaining());
        EXPECT_EQUAL(2ms, bar.getTimeRemaining());
    }
    {
        // Test reply swap state.
        SimpleReply foo("foo");
        foo.setMessage(Message::UP(new SimpleMessage("foo")));
        foo.setRetryDelay(1);
        foo.addError(Error(ErrorCode::APP_FATAL_ERROR, "fatal"));
        foo.addError(Error(ErrorCode::APP_TRANSIENT_ERROR, "transient"));

        SimpleReply bar("bar");
        bar.setMessage(Message::UP(new SimpleMessage("bar")));
        bar.setRetryDelay(2);
        bar.addError(Error(ErrorCode::ERROR_LIMIT, "err"));

        foo.swapState(bar);
        EXPECT_EQUAL("bar", static_cast<SimpleMessage&>(*foo.getMessage()).getValue());
        EXPECT_EQUAL("foo", static_cast<SimpleMessage&>(*bar.getMessage()).getValue());
        EXPECT_EQUAL(2.0, foo.getRetryDelay());
        EXPECT_EQUAL(1.0, bar.getRetryDelay());
        EXPECT_EQUAL(1u, foo.getNumErrors());
        EXPECT_EQUAL(2u, bar.getNumErrors());
    }
    {
        // Test message discard logic.
        Receptor handler;
        SimpleMessage msg("foo");
        msg.pushHandler(handler);
        msg.discard();

        Reply::UP reply = handler.getReply(0);
        ASSERT_FALSE(reply);
    }
    {
        // Test reply discard logic.
        Receptor handler;
        SimpleMessage msg("foo");
        msg.pushHandler(handler);

        SimpleReply reply("bar");
        reply.swapState(msg);
        reply.discard();

        Reply::UP ap = handler.getReply(0);
        ASSERT_FALSE(ap);
    }

    TEST_DONE();
}
