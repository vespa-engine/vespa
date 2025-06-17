// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/nexus.h>
#include <vespa/vespalib/testkit/time_bomb.h>
#include <vespa/vespalib/net/socket_handle.h>
#include <vespa/vespalib/net/socket_utils.h>
#include <vespa/vespalib/portal/reactor.h>
#include <vespa/vespalib/util/gate.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <fcntl.h>
#include <cassert>

using namespace vespalib;
using namespace vespalib::portal;
using vespalib::test::Nexus;

struct SocketPair {
    SocketHandle main;
    SocketHandle other;
    SocketPair() : main(), other() {
        int sockets[2];
        socketutils::nonblocking_socketpair(AF_UNIX, SOCK_STREAM, 0, sockets);
        main.reset(sockets[0]);
        other.reset(sockets[1]);
        // make main socket both readable and writable
        assert(other.write("x", 1) == 1);
    }
    ~SocketPair();
};
SocketPair::~SocketPair() = default;

std::atomic<size_t> tick_cnt = 0;
int tick() {
    ++tick_cnt;
    std::this_thread::sleep_for(std::chrono::milliseconds(1));
    return 0;
}

void wait_tick() {
    size_t sample = tick_cnt;
    while (sample == tick_cnt) {
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }
}

struct HandlerBase : Reactor::EventHandler {
    SocketPair          sockets;
    std::atomic<size_t> read_cnt;
    std::atomic<size_t> write_cnt;
    HandlerBase()
        : sockets(), read_cnt(0), write_cnt(0)
    {
    }
    void handle_event(bool read, bool write) override {
        if (read) {
            ++read_cnt;
        }
        if (write) {
            ++write_cnt;
        }
    }
    void verify(bool read, bool write) {
        size_t read_sample = read_cnt;
        size_t write_sample = write_cnt;
        wait_tick();
        wait_tick();
        EXPECT_EQ((read_sample != read_cnt), read);
        EXPECT_EQ((write_sample != write_cnt), write);
    }
    ~HandlerBase();
};
HandlerBase::~HandlerBase() = default;

struct SimpleHandler : HandlerBase {
    Reactor::Token::UP token;
    SimpleHandler(Reactor &reactor, bool read, bool write)
      : HandlerBase(), token()
    {
        token = reactor.attach(*this, sockets.main.get(), read, write);
    }
    ~SimpleHandler();
};
SimpleHandler::~SimpleHandler() = default;

struct DeletingHandler : HandlerBase {
    Gate allow_delete;
    Gate token_deleted;
    Reactor::Token::UP token;
    DeletingHandler(Reactor &reactor)
      : HandlerBase(), allow_delete(), token_deleted(), token()
    {
        token = reactor.attach(*this, sockets.main.get(), true, true);
    }
    void handle_event(bool read, bool write) override {
        HandlerBase::handle_event(read, write);
        allow_delete.await();
        token.reset();
        token_deleted.countDown();
    }
    ~DeletingHandler();
};
DeletingHandler::~DeletingHandler() = default;

struct WaitingHandler : HandlerBase {
    Gate enter_callback;
    Gate exit_callback;
    Reactor::Token::UP token;
    WaitingHandler(Reactor &reactor)
      : HandlerBase(), enter_callback(), exit_callback(), token()
    {
        token = reactor.attach(*this, sockets.main.get(), true, true);
    }
    void handle_event(bool read, bool write) override {
        enter_callback.countDown();
        HandlerBase::handle_event(read, write);
        exit_callback.await();
    }
    ~WaitingHandler();
};
WaitingHandler::~WaitingHandler() = default;

//-----------------------------------------------------------------------------

TEST(ReactorTest, require_that_reactor_can_produce_async_io_events) {
    Reactor f1(tick);
    TimeBomb f2(60);
    for (bool read: {true, false}) {
        for (bool write: {true, false}) {
            {
                SimpleHandler handler(f1, read, write);
                GTEST_DO(handler.verify(read, write));
            }
        }
    }
}

TEST(ReactorTest, require_that_reactor_token_can_be_used_to_change_active_io_events) {
    Reactor f1(tick);
    TimeBomb f2(60);
    SimpleHandler handler(f1, false, false);
    GTEST_DO(handler.verify(false, false));
    for (int i = 0; i < 2; ++i) {
        for (bool read: {true, false}) {
            for (bool write: {true, false}) {
                handler.token->update(read, write);
                wait_tick(); // avoid stale events
                GTEST_DO(handler.verify(read, write));
            }
        }
    }
}

TEST(ReactorTest, require_that_deleting_reactor_token_disables_io_events) {
    Reactor f1(tick);
    TimeBomb f2(60);
    SimpleHandler handler(f1, true, true);
    GTEST_DO(handler.verify(true, true));
    handler.token.reset();
    GTEST_DO(handler.verify(false, false));
}

TEST(ReactorTest, require_that_reactor_token_can_be_destroyed_during_io_event_handling) {
    Reactor f1(tick);
    TimeBomb f2(60);
    DeletingHandler handler(f1);
    handler.allow_delete.countDown();
    handler.token_deleted.await();
    GTEST_DO(handler.verify(false, false));
    EXPECT_EQ(handler.read_cnt, 1u);
    EXPECT_EQ(handler.write_cnt, 1u);
}

TEST(ReactorTest, require_that_reactor_token_destruction_waits_for_io_event_handling) {
    size_t num_threads = 2;
    Reactor f1;
    WaitingHandler f2(f1);
    Gate f3;
    TimeBomb f4(60);
    auto task = [&](Nexus &ctx){
                    if (ctx.thread_id() == 0) {
                        f2.enter_callback.await();
                        ctx.barrier(); // #1
                        EXPECT_TRUE(!f3.await(20ms));
                        f2.exit_callback.countDown();
                        EXPECT_TRUE(f3.await(60s));
                    } else {
                        ctx.barrier(); // #1
                        f2.token.reset();
                        f3.countDown();
                    }
                };
    Nexus::run(num_threads, task);
}

GTEST_MAIN_RUN_ALL_TESTS()
