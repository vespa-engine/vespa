// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
#include <vespa/vespalib/testkit/testapp.h>

LOG_SETUP("messages_test");

class MessagesTest : public vespalib::TestApp
{
public:
    MessagesTest() { }
    int Main() override;
};

int MessagesTest::Main()
{
    TEST_INIT("messages_test");
    TEST_DONE();
}

TEST_APPHOOK(MessagesTest);
