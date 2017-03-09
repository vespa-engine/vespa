// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/net/socket_address.h>
#include <vespa/vespalib/net/selector.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <thread>
#include <functional>
#include <chrono>

using namespace vespalib;

struct SocketPair {
    SocketHandle a;
    SocketHandle b;
    SocketPair(int a_fd, int b_fd) : a(a_fd), b(b_fd) {}
    SocketPair(SocketPair &&) = default;
    SocketPair &operator=(SocketPair &&) = default;
    static SocketPair create() {
        int sockets[2];
        ASSERT_EQUAL(0, socketpair(AF_UNIX, SOCK_STREAM | O_NONBLOCK, 0, sockets));
        return SocketPair(sockets[0], sockets[1]);
    }
    ~SocketPair() {}
};

struct Context {
    int fd;
    bool can_read;
    bool can_write;
    Context(int fd_in) : fd(fd_in), can_read(false), can_write(false) {}
    void reset() {
        can_read = false;
        can_write = false;
    }
};

struct Handler {
    bool wakeup;
    using context_type = Context;
    int get_fd(Context &ctx) const { return ctx.fd; }
    void handle_wakeup() { wakeup = true; }
    void handle_event(Context &ctx, bool read, bool write) {
        ctx.can_read = read;
        ctx.can_write = write;
    }
    void reset() {
        wakeup = false;
    }
};

struct Fixture {
    Handler handler;
    Selector<Handler> selector;
    std::vector<SocketPair> sockets;
    std::vector<Context> contexts;
    Fixture(size_t size) : handler(), selector(handler, 1024), sockets(), contexts() {
        for (size_t i = 0; i < size; ++i) {
            sockets.push_back(SocketPair::create());
            contexts.push_back(Context(sockets.back().a.get()));
        }
        for (auto &ctx: contexts) {
            selector.add(ctx);
        }
    }
    Fixture &reset() {
        handler.reset();
        for (auto &ctx: contexts) {
            ctx.reset();
        }
        return *this;
    }
    Fixture &poll(int timeout_ms = 250000) {
        selector.poll(timeout_ms);
        selector.dispatch();
        return *this;
    }
    void verify(bool expect_wakeup, std::vector<std::pair<bool,bool> > expect_events) {
        EXPECT_EQUAL(expect_wakeup, handler.wakeup);
        ASSERT_EQUAL(expect_events.size(), contexts.size());
        for (size_t i = 0; i < expect_events.size(); ++i) {
            EXPECT_EQUAL(expect_events[i].first, contexts[i].can_read);
            EXPECT_EQUAL(expect_events[i].second, contexts[i].can_write);
        }
    }
};

constexpr std::pair<bool,bool> none = std::make_pair(false, false);
constexpr std::pair<bool,bool> in   = std::make_pair(true,  false);
constexpr std::pair<bool,bool> out  = std::make_pair(false, true);
constexpr std::pair<bool,bool> both = std::make_pair(true,  true);

TEST_F("require that basic events trigger correctly", Fixture(1)) {
    TEST_DO(f1.reset().poll().verify(false, {out}));
    EXPECT_EQUAL(write(f1.sockets[0].b.get(), "test", 4), 4);
    TEST_DO(f1.reset().poll().verify(false, {both}));
    f1.selector.disable_write(f1.contexts[0]);
    TEST_DO(f1.reset().poll().verify(false, {in}));
    f1.selector.enable_write(f1.contexts[0]);
    TEST_DO(f1.reset().poll().verify(false, {both}));
    f1.selector.wakeup();
    TEST_DO(f1.reset().poll().verify(true, {both}));
    TEST_DO(f1.reset().poll().verify(false, {both}));
}

TEST_F("require that multiple sources can be selected on", Fixture(5)) {
    char buf[128];
    for (auto &ctx: f1.contexts) {
        f1.selector.disable_write(ctx);
    }
    TEST_DO(f1.reset().poll(10).verify(false, {none, none, none, none, none}));
    EXPECT_EQUAL(write(f1.sockets[1].b.get(), "test", 4), 4);
    EXPECT_EQUAL(write(f1.sockets[3].b.get(), "test", 4), 4);
    TEST_DO(f1.reset().poll().verify(false, {none, in, none, in, none}));
    EXPECT_EQUAL(read(f1.sockets[1].a.get(), buf, sizeof(buf)), 4);
    EXPECT_EQUAL(read(f1.sockets[3].a.get(), buf, 2), 2);
    TEST_DO(f1.reset().poll().verify(false, {none, none, none, in, none}));
    EXPECT_EQUAL(read(f1.sockets[3].a.get(), buf, sizeof(buf)), 2);
    TEST_DO(f1.reset().poll(10).verify(false, {none, none, none, none, none}));
}

TEST_F("require that removed sources no longer produce events", Fixture(2)) {
    TEST_DO(f1.reset().poll().verify(false, {out, out}));
    EXPECT_EQUAL(write(f1.sockets[0].b.get(), "test", 4), 4);
    EXPECT_EQUAL(write(f1.sockets[1].b.get(), "test", 4), 4);
    TEST_DO(f1.reset().poll().verify(false, {both, both}));
    f1.selector.remove(f1.contexts[0]);
    TEST_DO(f1.reset().poll().verify(false, {none, both}));
}

TEST_F("require that filling the output buffer disables write events", Fixture(1)) {
    EXPECT_EQUAL(write(f1.sockets[0].b.get(), "test", 4), 4);
    TEST_DO(f1.reset().poll().verify(false, {both}));
    size_t buffer_size = 0;
    while (write(f1.sockets[0].a.get(), "x", 1) == 1) {
        ++buffer_size;
    }
    EXPECT_EQUAL(errno, EWOULDBLOCK);
    fprintf(stderr, "buffer size: %zu\n", buffer_size);
    TEST_DO(f1.reset().poll().verify(false, {in}));
}

TEST_MT_FF("require that selector can be woken while waiting for events", 2, Fixture(0), TimeBomb(60)) {
    if (thread_id == 0) {
        TEST_DO(f1.reset().poll().verify(true, {}));
    } else {
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
        f1.selector.wakeup();
    }
}

TEST_MT_FF("require that selection criteria can be changed while waiting for events", 2, Fixture(1), TimeBomb(60)) {
    if (thread_id == 0) {
        f1.selector.disable_write(f1.contexts[0]);
        TEST_BARRIER();
        TEST_DO(f1.reset().poll().verify(false, {out}));
    } else {
        TEST_BARRIER();
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
        f1.selector.enable_write(f1.contexts[0]);        
    }
}

TEST_MT_FF("require that selection sources can be added while waiting for events", 2, Fixture(0), TimeBomb(60)) {
    if (thread_id == 0) {
        TEST_DO(f1.reset().poll().verify(false, {}));
        TEST_BARRIER();
    } else {
        SocketPair pair = SocketPair::create();
        Context ctx(pair.a.get());
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
        f1.selector.add(ctx);
        TEST_BARRIER();
        EXPECT_TRUE(ctx.can_write);
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
