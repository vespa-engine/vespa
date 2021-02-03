// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/time_bomb.h>
#include <vespa/vespalib/net/socket_handle.h>
#include <vespa/vespalib/net/socket_utils.h>
#include <vespa/vespalib/portal/reactor.h>
#include <vespa/vespalib/util/gate.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <fcntl.h>

using namespace vespalib;
using namespace vespalib::portal;

struct SocketPair {
    SocketHandle main;
    SocketHandle other;
    SocketPair() : main(), other() {
        int sockets[2];
        socketutils::nonblocking_socketpair(AF_UNIX, SOCK_STREAM, 0, sockets);
        main.reset(sockets[0]);
        other.reset(sockets[1]);
        // make main socket both readable and writable
        ASSERT_EQUAL(other.write("x", 1), 1);
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

struct SimpleHandler : Reactor::EventHandler {
    SocketPair          sockets;
    std::atomic<size_t> read_cnt;
    std::atomic<size_t> write_cnt;
    Reactor::Token::UP  token;
    SimpleHandler(Reactor &reactor, bool read, bool write)
        : sockets(), read_cnt(0), write_cnt(0), token()
    {
        token = reactor.attach(*this, sockets.main.get(), read, write);
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
        EXPECT_EQUAL((read_sample != read_cnt), read);
        EXPECT_EQUAL((write_sample != write_cnt), write);
    }
    ~SimpleHandler();
};
SimpleHandler::~SimpleHandler() = default;

struct DeletingHandler : SimpleHandler {
    Gate token_deleted;
    DeletingHandler(Reactor &reactor) : SimpleHandler(reactor, true, true),
                                        token_deleted() {}
    void handle_event(bool read, bool write) override {
        SimpleHandler::handle_event(read, write);
        token.reset();
        token_deleted.countDown();
    }
    ~DeletingHandler();
};
DeletingHandler::~DeletingHandler() = default;

struct WaitingHandler : SimpleHandler {
    Gate enter_callback;
    Gate exit_callback;
    WaitingHandler(Reactor &reactor) : SimpleHandler(reactor, true, true),
                                       enter_callback(), exit_callback() {}
    void handle_event(bool read, bool write) override {
        enter_callback.countDown();
        SimpleHandler::handle_event(read, write);
        exit_callback.await();
    }
    ~WaitingHandler();
};
WaitingHandler::~WaitingHandler() = default;

//-----------------------------------------------------------------------------

TEST_FF("require that reactor can produce async io events", Reactor(tick), TimeBomb(60)) {
    for (bool read: {true, false}) {
        for (bool write: {true, false}) {
            {
                SimpleHandler handler(f1, read, write);
                TEST_DO(handler.verify(read, write));
            }
        }
    }
}

TEST_FF("require that reactor token can be used to change active io events", Reactor(tick), TimeBomb(60)) {
    SimpleHandler handler(f1, false, false);
    TEST_DO(handler.verify(false, false));
    for (int i = 0; i < 2; ++i) {
        for (bool read: {true, false}) {
            for (bool write: {true, false}) {
                handler.token->update(read, write);
                wait_tick(); // avoid stale events
                TEST_DO(handler.verify(read, write));
            }
        }
    }
}

TEST_FF("require that deleting reactor token disables io events", Reactor(tick), TimeBomb(60)) {
    SimpleHandler handler(f1, true, true);
    TEST_DO(handler.verify(true, true));
    handler.token.reset();
    TEST_DO(handler.verify(false, false));
}

TEST_FF("require that reactor token can be destroyed during io event handling", Reactor(tick), TimeBomb(60)) {
    DeletingHandler handler(f1);
    handler.token_deleted.await();
    TEST_DO(handler.verify(false, false));
    EXPECT_EQUAL(handler.read_cnt, 1u);
    EXPECT_EQUAL(handler.write_cnt, 1u);
}

TEST_MT_FFFF("require that reactor token destruction waits for io event handling", 2,
             Reactor(), WaitingHandler(f1), Gate(), TimeBomb(60))
{
    if (thread_id == 0) {
        f2.enter_callback.await();
        TEST_BARRIER(); // #1
        EXPECT_TRUE(!f3.await(20ms));
        f2.exit_callback.countDown();
        EXPECT_TRUE(f3.await(60s));
    } else {
        TEST_BARRIER(); // #1
        f2.token.reset();
        f3.countDown();
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
