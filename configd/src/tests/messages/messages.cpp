// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>

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
