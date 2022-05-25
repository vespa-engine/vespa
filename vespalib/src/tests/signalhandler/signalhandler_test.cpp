// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <gmock/gmock.h>
#include <latch>
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

void my_cool_function(std::latch&, std::latch&) __attribute__((noinline));

// Could have used a single std::barrier<no op functor> here, but when using explicit
// phase latches it sort of feels like the semantics are more immediately obvious.
void my_cool_function(std::latch& arrival_latch, std::latch& departure_latch) {
    arrival_latch.arrive_and_wait();
    // Twiddle thumbs in departure latch until main test thread has dumped our stack
    departure_latch.arrive_and_wait();
}

TEST(SignalHandlerTest, can_dump_stack_of_another_thread)
{
    SignalHandler::enable_cross_thread_stack_tracing();

    std::latch arrival_latch(2);
    std::latch departure_latch(2);

    std::thread t([&]{
        my_cool_function(arrival_latch, departure_latch);
    });
    arrival_latch.arrive_and_wait();

    auto trace = SignalHandler::get_cross_thread_stack_trace(t.native_handle());
    EXPECT_THAT(trace, HasSubstr("my_cool_function"));

    departure_latch.arrive_and_wait();
    t.join();
}

TEST(SignalHandlerTest, dumping_stack_of_an_ex_thread_does_not_crash)
{
    SignalHandler::enable_cross_thread_stack_tracing();
    std::thread t([]() noexcept {
        // Do a lot of nothing at all.
    });
    auto tid = t.native_handle();
    t.join();
    auto trace = SignalHandler::get_cross_thread_stack_trace(tid);
    EXPECT_EQ(trace, "(pthread_kill() failed; could not get backtrace)");
}

string my_totally_tubular_and_groovy_function() __attribute__((noinline));
string my_totally_tubular_and_groovy_function() {
    return SignalHandler::get_cross_thread_stack_trace(pthread_self());
}

TEST(SignalHandlerTest, can_get_stack_trace_of_own_thread)
{
    SignalHandler::enable_cross_thread_stack_tracing(); // Technically not cross-thread in this case...!
    auto trace = my_totally_tubular_and_groovy_function();
    EXPECT_THAT(trace, HasSubstr("my_totally_tubular_and_groovy_function"));
}

GTEST_MAIN_RUN_ALL_TESTS()
