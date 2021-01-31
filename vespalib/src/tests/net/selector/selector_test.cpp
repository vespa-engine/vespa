// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/time_bomb.h>
#include <vespa/vespalib/net/socket_address.h>
#include <vespa/vespalib/net/selector.h>
#include <vespa/vespalib/net/socket_utils.h>
#include <thread>
#include <functional>
#include <chrono>
#include <fcntl.h>

using namespace vespalib;

struct SocketPair {
    SocketHandle a;
    SocketHandle b;
    SocketPair(int a_fd, int b_fd) : a(a_fd), b(b_fd) {}
    SocketPair(SocketPair &&) = default;
    SocketPair &operator=(SocketPair &&) = default;
    static SocketPair create() {
        int sockets[2];
        socketutils::nonblocking_socketpair(AF_UNIX, SOCK_STREAM, 0, sockets);
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

struct Fixture {
    bool wakeup;
    Selector<Context> selector;
    std::vector<SocketPair> sockets;
    std::vector<Context> contexts;
    Fixture(size_t size, bool read_enabled, bool write_enabled) : wakeup(false), selector(), sockets(), contexts() {
        for (size_t i = 0; i < size; ++i) {
            sockets.push_back(SocketPair::create());
            contexts.push_back(Context(sockets.back().a.get()));
        }
        for (auto &ctx: contexts) {
            selector.add(ctx.fd, ctx, read_enabled, write_enabled);
        }
    }
    void update(size_t idx, bool read, bool write) {
        Context &ctx = contexts[idx];
        selector.update(ctx.fd, ctx, read, write);
    }
    bool write(size_t idx, const char *str) {
        size_t len = strlen(str);
        ssize_t res = ::write(sockets[idx].b.get(), str, len);
        return (res == ssize_t(len));
    }
    bool write_self(size_t idx, const char *str) {
        size_t len = strlen(str);
        ssize_t res = ::write(sockets[idx].a.get(), str, len);
        return (res == ssize_t(len));
    }
    bool read(size_t idx, size_t len) {
        char buf[128];
        ssize_t res = ::read(sockets[idx].a.get(), buf, len);
        return (res == ssize_t(len));
    }
    Fixture &reset() {
        wakeup = false;
        for (auto &ctx: contexts) {
            ctx.reset();
        }
        return *this;
    }
    Fixture &poll(int timeout_ms = 60000) {
        selector.poll(timeout_ms);
        auto dispatchResult = selector.dispatch(*this);
        if (wakeup) {
            EXPECT_TRUE(dispatchResult == SelectorDispatchResult::WAKEUP_CALLED);
        } else {
            EXPECT_TRUE(dispatchResult == SelectorDispatchResult::NO_WAKEUP);
        }
        return *this;
    }
    void verify(bool expect_wakeup, std::vector<std::pair<bool,bool> > expect_events) {
        EXPECT_EQUAL(expect_wakeup, wakeup);
        ASSERT_EQUAL(expect_events.size(), contexts.size());
        for (size_t i = 0; i < expect_events.size(); ++i) {
            EXPECT_EQUAL(expect_events[i].first, contexts[i].can_read);
            EXPECT_EQUAL(expect_events[i].second, contexts[i].can_write);
        }
    }
    // selector callbacks
    void handle_wakeup() { wakeup = true; }
    void handle_event(Context &ctx, bool read, bool write) {
        ctx.can_read = read;
        ctx.can_write = write;
    }
};

constexpr std::pair<bool,bool> none = std::make_pair(false, false);
constexpr std::pair<bool,bool> in   = std::make_pair(true,  false);
constexpr std::pair<bool,bool> out  = std::make_pair(false, true);
constexpr std::pair<bool,bool> both = std::make_pair(true,  true);

TEST_F("require that basic events trigger correctly", Fixture(1, true, true)) {
    TEST_DO(f1.reset().poll().verify(false, {out}));
    EXPECT_TRUE(f1.write(0, "test"));
    TEST_DO(f1.reset().poll().verify(false, {both}));
    f1.update(0, true, false);
    TEST_DO(f1.reset().poll().verify(false, {in}));
    f1.update(0, false, true);
    TEST_DO(f1.reset().poll().verify(false, {out}));
    f1.update(0, false, false);
    TEST_DO(f1.reset().poll(10).verify(false, {none}));
    f1.update(0, true, true);
    f1.selector.wakeup();
    TEST_DO(f1.reset().poll().verify(true, {both}));
    TEST_DO(f1.reset().poll().verify(false, {both}));
}

TEST_FFF("require that sources can be added with some events disabled",
         Fixture(1, true, false), Fixture(1, false, true), Fixture(1, false, false))
{
    EXPECT_TRUE(f1.write(0, "test"));
    EXPECT_TRUE(f2.write(0, "test"));
    EXPECT_TRUE(f3.write(0, "test"));
    TEST_DO(f1.reset().poll().verify(false, {in}));
    TEST_DO(f2.reset().poll().verify(false, {out}));
    TEST_DO(f3.reset().poll(10).verify(false, {none}));
    f1.update(0, true, true);
    f2.update(0, true, true);
    f3.update(0, true, true);
    TEST_DO(f1.reset().poll().verify(false, {both}));
    TEST_DO(f2.reset().poll().verify(false, {both}));
    TEST_DO(f3.reset().poll().verify(false, {both}));
}

TEST_F("require that multiple sources can be selected on", Fixture(5, true, false)) {
    TEST_DO(f1.reset().poll(10).verify(false, {none, none, none, none, none}));
    EXPECT_TRUE(f1.write(1, "test"));
    EXPECT_TRUE(f1.write(3, "test"));
    TEST_DO(f1.reset().poll().verify(false, {none, in, none, in, none}));
    EXPECT_TRUE(f1.read(1, strlen("test")));
    EXPECT_TRUE(f1.read(3, strlen("te")));
    TEST_DO(f1.reset().poll().verify(false, {none, none, none, in, none}));
    EXPECT_TRUE(f1.read(3, strlen("st")));
    TEST_DO(f1.reset().poll(10).verify(false, {none, none, none, none, none}));
}

TEST_F("require that removed sources no longer produce events", Fixture(2, true, true)) {
    TEST_DO(f1.reset().poll().verify(false, {out, out}));
    EXPECT_TRUE(f1.write(0, "test"));
    EXPECT_TRUE(f1.write(1, "test"));
    TEST_DO(f1.reset().poll().verify(false, {both, both}));
    f1.selector.remove(f1.contexts[0].fd);
    TEST_DO(f1.reset().poll().verify(false, {none, both}));
}

TEST_F("require that filling the output buffer disables write events", Fixture(1, true, true)) {
    EXPECT_TRUE(f1.write(0, "test"));
    TEST_DO(f1.reset().poll().verify(false, {both}));
    size_t buffer_size = 0;
    while (f1.write_self(0, "x")) {
        ++buffer_size;
    }
    EXPECT_TRUE((errno == EWOULDBLOCK) || (errno == EAGAIN));
    fprintf(stderr, "buffer size: %zu\n", buffer_size);
    TEST_DO(f1.reset().poll().verify(false, {in}));
}

TEST_MT_FF("require that selector can be woken while waiting for events", 2, Fixture(0, true, false), TimeBomb(60)) {
    if (thread_id == 0) {
        TEST_DO(f1.reset().poll().verify(true, {}));
    } else {
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
        f1.selector.wakeup();
    }
}

TEST_MT_FF("require that selection criteria can be changed while waiting for events", 2, Fixture(1, true, false), TimeBomb(60)) {
    if (thread_id == 0) {
        TEST_DO(f1.reset().poll().verify(false, {out}));
    } else {
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
        f1.update(0, true, true);
    }
}

TEST_MT_FF("require that selection sources can be added while waiting for events", 2, Fixture(0, true, false), TimeBomb(60)) {
    if (thread_id == 0) {
        TEST_DO(f1.reset().poll().verify(false, {}));
        TEST_BARRIER();
    } else {
        SocketPair pair = SocketPair::create();
        Context ctx(pair.a.get());
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
        f1.selector.add(ctx.fd, ctx, true, true);
        TEST_BARRIER();
        EXPECT_TRUE(ctx.can_write);
    }
}

TEST_MT_FFF("require that single fd selector can wait for read events while handling wakeups correctly",
            2, SocketPair(SocketPair::create()), SingleFdSelector(f1.a.get()), TimeBomb(60))
{
    if (thread_id == 0) {
        EXPECT_EQUAL(f2.wait_readable(), false); // wakeup only
        TEST_BARRIER(); // #1
        EXPECT_EQUAL(f2.wait_readable(), true); // read only
        TEST_BARRIER(); // #2
        TEST_BARRIER(); // #3
        EXPECT_EQUAL(f2.wait_readable(), true); // read and wakeup
    } else {
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
        f2.wakeup();
        TEST_BARRIER(); // #1
        vespalib::string msg("test");
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
        ASSERT_EQUAL(f1.b.write(msg.data(), msg.size()), ssize_t(msg.size()));
        TEST_BARRIER(); // #2
        f2.wakeup();
        TEST_BARRIER(); // #3
    }
}

TEST_MT_FFF("require that single fd selector can wait for write events while handling wakeups correctly",
            2, SocketPair(SocketPair::create()), SingleFdSelector(f1.a.get()), TimeBomb(60))
{
    if (thread_id == 0) {
        EXPECT_EQUAL(f2.wait_writable(), true); // write only
        TEST_BARRIER(); // #1
        TEST_BARRIER(); // #2
        EXPECT_EQUAL(f2.wait_writable(), true); // write and wakeup
        size_t buffer_size = 0;
        while (f1.a.write("x", 1) == 1) {
            ++buffer_size;
        }
        EXPECT_TRUE((errno == EWOULDBLOCK) || (errno == EAGAIN));
        fprintf(stderr, "buffer size: %zu\n", buffer_size);
        TEST_BARRIER(); // #3
        EXPECT_EQUAL(f2.wait_readable(), false); // wakeup only
    } else {
        TEST_BARRIER(); // #1
        f2.wakeup();
        TEST_BARRIER(); // #2
        TEST_BARRIER(); // #3
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
        f2.wakeup();
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
