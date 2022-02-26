// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>

using namespace vbench;

struct MyHandler : public Handler<int> {
    int value;
    MyHandler() : value(-1) {}
    ~MyHandler() override;
    void handle(std::unique_ptr<int> v) override { value = (v.get() != 0) ? *v : 0; }
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

TEST("dispatcher") {
    MyHandler dropped;
    MyHandler handler1;
    MyHandler handler2;
    Dispatcher<int> dispatcher(dropped);
    Fetcher fetcher1(dispatcher, handler1);
    Fetcher fetcher2(dispatcher, handler2);
    vespalib::Thread thread1(fetcher1, fetcher1_thread);
    vespalib::Thread thread2(fetcher2, fetcher2_thread);
    thread1.start();
    EXPECT_TRUE(dispatcher.waitForThreads(1, 512));
    thread2.start();
    EXPECT_TRUE(dispatcher.waitForThreads(2, 512));
    EXPECT_EQUAL(-1, dropped.value);
    EXPECT_EQUAL(-1, handler1.value);
    EXPECT_EQUAL(-1, handler2.value);
    dispatcher.handle(std::unique_ptr<int>(new int(1)));
    dispatcher.handle(std::unique_ptr<int>(new int(2)));
    dispatcher.handle(std::unique_ptr<int>(new int(3)));
    thread1.join();
    thread2.join();
    EXPECT_EQUAL(3, dropped.value);
    EXPECT_EQUAL(2, handler1.value);
    EXPECT_EQUAL(1, handler2.value);
    dispatcher.close();
    {
        dispatcher.handle(std::unique_ptr<int>(new int(4)));
        EXPECT_EQUAL(3, dropped.value);
        MyHandler handler3;
        Fetcher fetcher3(dispatcher, handler3);
        EXPECT_EQUAL(-1, handler3.value);
        fetcher3.run();
        EXPECT_EQUAL(0, handler3.value);
    }
}

TEST_FF("dispatcher poll timeout", MyHandler(), Dispatcher<int>(f1)) {
    EXPECT_FALSE(f2.waitForThreads(1, 2));
}

TEST_MAIN() { TEST_RUN_ALL(); }
