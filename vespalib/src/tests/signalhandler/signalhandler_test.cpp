// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "my_shared_library.h"
#include <vespa/vespalib/util/count_down_latch.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <gmock/gmock.h>
#include <thread>
#include <unistd.h>

#include <vespa/log/log.h>
LOG_SETUP("signalhandler_test");

using namespace vespalib;
using namespace ::testing;

TEST(SignalHandlerTest, signal_handler_can_intercept_hooked_signals)
{
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
    EXPECT_EQ(0, system("res=`./vespalib_victim_app`; test \"$res\" = \"GOT TERM\""));
}

TEST(SignalHandlerTest, can_dump_stack_of_another_thread)
{
    vespalib::CountDownLatch arrival_latch(2);
    vespalib::CountDownLatch departure_latch(2);

    std::thread t([&]{
        my_cool_function(arrival_latch, departure_latch);
    });
    arrival_latch.countDown();
    arrival_latch.await();

    auto trace = SignalHandler::get_cross_thread_stack_trace(t.native_handle());
    EXPECT_THAT(trace, HasSubstr("my_cool_function"));

    departure_latch.countDown();
    departure_latch.await();
    t.join();
}

TEST(SignalHandlerTest, can_get_stack_trace_of_own_thread)
{
    auto trace = my_totally_tubular_and_groovy_function();
    EXPECT_THAT(trace, HasSubstr("my_totally_tubular_and_groovy_function"));
}

int main(int argc, char* argv[]) {
    ::testing::InitGoogleTest(&argc, argv);
    SignalHandler::enable_cross_thread_stack_tracing();
    return RUN_ALL_TESTS();
}
