// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <unistd.h>

#include <vespa/log/log.h>
LOG_SETUP("signalhandler_test");

using namespace vespalib;

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("signalhandler_test");
    EXPECT_TRUE(!SignalHandler::INT.check());
    EXPECT_TRUE(!SignalHandler::TERM.check());
    SignalHandler::INT.ignore();
    EXPECT_TRUE(!SignalHandler::INT.check());
    EXPECT_TRUE(!SignalHandler::TERM.check());
    SignalHandler::TERM.hook();
    EXPECT_TRUE(!SignalHandler::INT.check());
    EXPECT_TRUE(!SignalHandler::TERM.check());
    kill(getpid(), SIGINT);
    EXPECT_TRUE(!SignalHandler::INT.check());
    EXPECT_TRUE(!SignalHandler::TERM.check());
    kill(getpid(), SIGTERM);
    EXPECT_TRUE(!SignalHandler::INT.check());
    EXPECT_TRUE(SignalHandler::TERM.check());
    SignalHandler::TERM.clear();
    EXPECT_TRUE(!SignalHandler::INT.check());
    EXPECT_TRUE(!SignalHandler::TERM.check());
    EXPECT_EQUAL(0, system("res=`./vespalib_victim_app`; test \"$res\" = \"GOT TERM\""));
    TEST_DONE();
}
