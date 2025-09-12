// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vbench/test/all.h>

using namespace vbench;

struct MyHandler : public Handler<int> {
    int value;
    MyHandler() : value(-1) {}
    ~MyHandler() override;
    void handle(std::unique_ptr<int> v) override { value = v ? *v : 0; }
};

MyHandler::~MyHandler() = default;

struct Fetcher : public vespalib::Runnable {
    Provider<int> &provider;
    Handler<int> &handler;
    Fetcher(Provider<int> &p, Handler<int> &h) : provider(p), handler(h) {}
    void run() override { handler.handle(provider.provide()); }
};

VESPA_THREAD_STACK_TAG(fetcher1_thread);
VESPA_THREAD_STACK_TAG(fetcher2_thread);

TEST(DispatcherTest, dispatcher) {
    MyHandler dropped;
    MyHandler handler1;
    MyHandler handler2;
    Dispatcher<int> dispatcher(dropped);
    Fetcher fetcher1(dispatcher, handler1);
    Fetcher fetcher2(dispatcher, handler2);
    auto thread1 = vespalib::thread::start(fetcher1, fetcher1_thread);
    EXPECT_TRUE(dispatcher.waitForThreads(1, 512));
    auto thread2 = vespalib::thread::start(fetcher2, fetcher2_thread);
    EXPECT_TRUE(dispatcher.waitForThreads(2, 512));
    EXPECT_EQ(-1, dropped.value);
    EXPECT_EQ(-1, handler1.value);
    EXPECT_EQ(-1, handler2.value);
    dispatcher.handle(std::unique_ptr<int>(new int(1)));
    dispatcher.handle(std::unique_ptr<int>(new int(2)));
    dispatcher.handle(std::unique_ptr<int>(new int(3)));
    thread1.join();
    thread2.join();
    EXPECT_EQ(3, dropped.value);
    EXPECT_EQ(2, handler1.value);
    EXPECT_EQ(1, handler2.value);
    dispatcher.close();
    {
        dispatcher.handle(std::unique_ptr<int>(new int(4)));
        EXPECT_EQ(3, dropped.value);
        MyHandler handler3;
        Fetcher fetcher3(dispatcher, handler3);
        EXPECT_EQ(-1, handler3.value);
        fetcher3.run();
        EXPECT_EQ(0, handler3.value);
    }
}

TEST(DispatcherTest, dispatcher_poll_timeout) {
    MyHandler f1;
    Dispatcher<int> f2(f1);
    EXPECT_FALSE(f2.waitForThreads(1, 2));
}

GTEST_MAIN_RUN_ALL_TESTS()
