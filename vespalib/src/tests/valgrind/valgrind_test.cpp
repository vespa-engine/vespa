// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/valgrind.h>

using namespace vespalib;

class Test : public TestApp
{
    int Main() override;
    void testUninitializedUser();
    void testUninitializedSystemCall();
    void testInitializedUser();
    void testInitializedSystemCall();
};

void Test::testUninitializedUser()
{
    char buf[7];
    buf[0] = 7;
    buf[5] = 7;
    Valgrind::testUninitialized(buf, sizeof(buf));
}

void Test::testUninitializedSystemCall()
{
    char buf[7];
    buf[0] = 7;
    buf[5] = 7;
    Valgrind::testSystemCall(buf, sizeof(buf));
}
void Test::testInitializedUser()
{
    char buf[7];
    memset(buf, 0, sizeof(buf));
    Valgrind::testUninitialized(buf, sizeof(buf));
}

void Test::testInitializedSystemCall()
{
    char buf[7];
    memset(buf, 0, sizeof(buf));
    Valgrind::testSystemCall(buf, sizeof(buf));
}

int
Test::Main()
{
    TEST_INIT("valgrind_test");

    if (strcmp(_argv[1], "testInitializedUser") == 0) {
        testInitializedUser();
    } else if (strcmp(_argv[1], "testInitializedSystemCall") == 0) {
        testInitializedSystemCall();
    } else if (strcmp(_argv[1], "testUninitializedUser") == 0) {
        testUninitializedUser();
    } else if (strcmp(_argv[1], "testUninitializedSystemCall") == 0) {
        testUninitializedSystemCall();
    } else {
        testInitializedUser();
    }

    TEST_DONE();
}

TEST_APPHOOK(Test)
