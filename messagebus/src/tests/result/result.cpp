// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/messagebus/result.h>
#include <vespa/messagebus/error.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/testlib/simplemessage.h>

using namespace mbus;

struct MyMessage : public SimpleMessage
{
    static int ctorCnt;
    static int dtorCnt;
    MyMessage(const string &str) : SimpleMessage(str) {
        ++ctorCnt;
    }
    virtual ~MyMessage() {
        ++dtorCnt;
    }
};
int MyMessage::ctorCnt = 0;
int MyMessage::dtorCnt = 0;

struct Test : public vespalib::TestApp
{
    Result sendOk(Message::UP msg);
    Result sendFail(Message::UP msg);
    int Main() override;
};

Result
Test::sendOk(Message::UP msg) {
    (void) msg;
    return Result();
}

Result
Test::sendFail(Message::UP msg) {
    return Result(Error(ErrorCode::FATAL_ERROR, "error"), std::move(msg));
}

int
Test::Main()
{
    TEST_INIT("result_test");
    { // test accepted
        Message::UP msg(new MyMessage("test"));
        Result res = sendOk(std::move(msg));
        EXPECT_TRUE(msg.get() == 0);
        EXPECT_TRUE(res.isAccepted());
        EXPECT_TRUE(res.getError().getCode() == ErrorCode::NONE);
        EXPECT_TRUE(res.getError().getMessage() == "");
        Message::UP back = res.getMessage();
        EXPECT_TRUE(back.get() == 0);
    }
    { // test failed
        Message::UP msg(new MyMessage("test"));
        Message *raw = msg.get();
        EXPECT_TRUE(raw != 0);
        Result res = sendFail(std::move(msg));
        EXPECT_TRUE(msg.get() == 0);
        EXPECT_TRUE(!res.isAccepted());
        EXPECT_TRUE(res.getError().getCode() == ErrorCode::FATAL_ERROR);
        EXPECT_TRUE(res.getError().getMessage() == "error");
        Message::UP back = res.getMessage();
        EXPECT_TRUE(back.get() == raw);
    }
    EXPECT_TRUE(MyMessage::ctorCnt == 2);
    EXPECT_TRUE(MyMessage::dtorCnt == 2);
    TEST_DONE();
}

TEST_APPHOOK(Test);
