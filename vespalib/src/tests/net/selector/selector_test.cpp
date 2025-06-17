// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/nexus.h>
#include <vespa/vespalib/testkit/time_bomb.h>
#include <vespa/vespalib/net/socket_address.h>
#include <vespa/vespalib/net/selector.h>
#include <vespa/vespalib/net/socket_utils.h>
#include <thread>
#include <functional>
#include <chrono>
#include <fcntl.h>

using namespace vespalib;
using vespalib::test::Nexus;

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
    ~Fixture();
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
        EXPECT_EQ(expect_wakeup, wakeup);
        ASSERT_EQ(expect_events.size(), contexts.size());
        for (size_t i = 0; i < expect_events.size(); ++i) {
            EXPECT_EQ(expect_events[i].first, contexts[i].can_read);
            EXPECT_EQ(expect_events[i].second, contexts[i].can_write);
        }
    }
    // selector callbacks
    void handle_wakeup() { wakeup = true; }
    void handle_event(Context &ctx, bool read, bool write) {
        ctx.can_read = read;
        ctx.can_write = write;
    }
};
Fixture::~Fixture() = default;

constexpr std::pair<bool,bool> none = std::make_pair(false, false);
constexpr std::pair<bool,bool> in   = std::make_pair(true,  false);
constexpr std::pair<bool,bool> out  = std::make_pair(false, true);
constexpr std::pair<bool,bool> both = std::make_pair(true,  true);

TEST(SelectorTest, require_that_basic_events_trigger_correctly) {
    Fixture f1(1, true, true);
    GTEST_DO(f1.reset().poll().verify(false, {out}));
    EXPECT_TRUE(f1.write(0, "test"));
    GTEST_DO(f1.reset().poll().verify(false, {both}));
    f1.update(0, true, false);
    GTEST_DO(f1.reset().poll().verify(false, {in}));
    f1.update(0, false, true);
    GTEST_DO(f1.reset().poll().verify(false, {out}));
    f1.update(0, false, false);
    GTEST_DO(f1.reset().poll(10).verify(false, {none}));
    f1.update(0, true, true);
    f1.selector.wakeup();
    GTEST_DO(f1.reset().poll().verify(true, {both}));
    GTEST_DO(f1.reset().poll().verify(false, {both}));
}

TEST(SelectorTest, require_that_sources_can_be_added_with_some_events_disabled) {
    Fixture f1(1, true, false);
    Fixture f2(1, false, true);
    Fixture f3(1, false, false);
    EXPECT_TRUE(f1.write(0, "test"));
    EXPECT_TRUE(f2.write(0, "test"));
    EXPECT_TRUE(f3.write(0, "test"));
    GTEST_DO(f1.reset().poll().verify(false, {in}));
    GTEST_DO(f2.reset().poll().verify(false, {out}));
    GTEST_DO(f3.reset().poll(10).verify(false, {none}));
    f1.update(0, true, true);
    f2.update(0, true, true);
    f3.update(0, true, true);
    GTEST_DO(f1.reset().poll().verify(false, {both}));
    GTEST_DO(f2.reset().poll().verify(false, {both}));
    GTEST_DO(f3.reset().poll().verify(false, {both}));
}

TEST(SelectorTest, require_that_multiple_sources_can_be_selected_on) {
    Fixture f1(5, true, false);
    GTEST_DO(f1.reset().poll(10).verify(false, {none, none, none, none, none}));
    EXPECT_TRUE(f1.write(1, "test"));
    EXPECT_TRUE(f1.write(3, "test"));
    GTEST_DO(f1.reset().poll().verify(false, {none, in, none, in, none}));
    EXPECT_TRUE(f1.read(1, strlen("test")));
    EXPECT_TRUE(f1.read(3, strlen("te")));
    GTEST_DO(f1.reset().poll().verify(false, {none, none, none, in, none}));
    EXPECT_TRUE(f1.read(3, strlen("st")));
    GTEST_DO(f1.reset().poll(10).verify(false, {none, none, none, none, none}));
}

TEST(SelectorTest, require_that_removed_sources_no_longer_produce_events) {
    Fixture f1(2, true, true);
    GTEST_DO(f1.reset().poll().verify(false, {out, out}));
    EXPECT_TRUE(f1.write(0, "test"));
    EXPECT_TRUE(f1.write(1, "test"));
    GTEST_DO(f1.reset().poll().verify(false, {both, both}));
    f1.selector.remove(f1.contexts[0].fd);
    GTEST_DO(f1.reset().poll().verify(false, {none, both}));
}

TEST(SelectorTest, require_that_filling_the_output_buffer_disables_write_events) {
    Fixture f1(1, true, true);
    EXPECT_TRUE(f1.write(0, "test"));
    GTEST_DO(f1.reset().poll().verify(false, {both}));
    size_t buffer_size = 0;
    while (f1.write_self(0, "x")) {
        ++buffer_size;
    }
    EXPECT_TRUE((errno == EWOULDBLOCK) || (errno == EAGAIN));
    fprintf(stderr, "buffer size: %zu\n", buffer_size);
    GTEST_DO(f1.reset().poll().verify(false, {in}));
}

TEST(SelectorTest, require_that_selector_can_be_woken_while_waiting_for_events) {
    size_t num_threads = 2;
    Fixture f1(0, true, false);
    TimeBomb f2(60);
    auto task = [&](Nexus &ctx){
                    if (ctx.thread_id() == 0) {
                        GTEST_DO(f1.reset().poll().verify(true, {}));
                    } else {
                        std::this_thread::sleep_for(std::chrono::milliseconds(20));
                        f1.selector.wakeup();
                    }
                };
    Nexus::run(num_threads, task);
}

TEST(SelectorTest, require_that_selection_criteria_can_be_changed_while_waiting_for_events) {
    size_t num_threads = 2;
    Fixture f1(1, true, false);
    TimeBomb f2(60);
    auto task = [&](Nexus &ctx){
                    if (ctx.thread_id() == 0) {
                        GTEST_DO(f1.reset().poll().verify(false, {out}));
                    } else {
                        std::this_thread::sleep_for(std::chrono::milliseconds(20));
                        f1.update(0, true, true);
                    }
                };
    Nexus::run(num_threads, task);
}

TEST(SelectorTest, require_that_selection_sources_can_be_added_while_waiting_for_events) {
    size_t num_threads = 2;
    Fixture f1(0, true, false);
    TimeBomb f2(60);
    auto task = [&](Nexus &n){
                    if (n.thread_id() == 0) {
                        GTEST_DO(f1.reset().poll().verify(false, {}));
                        n.barrier();
                    } else {
                        SocketPair pair = SocketPair::create();
                        Context ctx(pair.a.get());
                        std::this_thread::sleep_for(std::chrono::milliseconds(20));
                        f1.selector.add(ctx.fd, ctx, true, true);
                        n.barrier();
                        EXPECT_TRUE(ctx.can_write);
                    }
                };
    Nexus::run(num_threads, task);
}

TEST(SelectorTest, require_that_single_fd_selector_can_wait_for_read_events_while_handling_wakeups_correctly) {
    size_t num_threads = 2;
    SocketPair f1(SocketPair::create());
    SingleFdSelector f2(f1.a.get());
    TimeBomb f3(60);
    auto task = [&](Nexus &ctx){
                    if (ctx.thread_id() == 0) {
                        EXPECT_EQ(f2.wait_readable(), false); // wakeup only
                        ctx.barrier(); // #1
                        EXPECT_EQ(f2.wait_readable(), true); // read only
                        ctx.barrier(); // #2
                        ctx.barrier(); // #3
                        EXPECT_EQ(f2.wait_readable(), true); // read and wakeup
                    } else {
                        std::this_thread::sleep_for(std::chrono::milliseconds(20));
                        f2.wakeup();
                        ctx.barrier(); // #1
                        std::string msg("test");
                        std::this_thread::sleep_for(std::chrono::milliseconds(20));
                        ASSERT_EQ(f1.b.write(msg.data(), msg.size()), ssize_t(msg.size()));
                        ctx.barrier(); // #2
                        f2.wakeup();
                        ctx.barrier(); // #3
                    }
                };
    Nexus::run(num_threads, task);
}

TEST(SelectorTest, require_that_single_fd_selector_can_wait_for_write_events_while_handling_wakeups_correctly) {
    size_t num_threads = 2;
    SocketPair f1(SocketPair::create());
    SingleFdSelector f2(f1.a.get());
    TimeBomb f3(60);
    auto task = [&](Nexus &ctx){
                    if (ctx.thread_id() == 0) {
                        EXPECT_EQ(f2.wait_writable(), true); // write only
                        ctx.barrier(); // #1
                        ctx.barrier(); // #2
                        EXPECT_EQ(f2.wait_writable(), true); // write and wakeup
                        size_t buffer_size = 0;
                        while (f1.a.write("x", 1) == 1) {
                            ++buffer_size;
                        }
                        EXPECT_TRUE((errno == EWOULDBLOCK) || (errno == EAGAIN));
                        fprintf(stderr, "buffer size: %zu\n", buffer_size);
                        ctx.barrier(); // #3
                        EXPECT_EQ(f2.wait_readable(), false); // wakeup only
                    } else {
                        ctx.barrier(); // #1
                        f2.wakeup();
                        ctx.barrier(); // #2
                        ctx.barrier(); // #3
                        std::this_thread::sleep_for(std::chrono::milliseconds(20));
                        f2.wakeup();
                    }
                };
    Nexus::run(num_threads, task);
}

GTEST_MAIN_RUN_ALL_TESTS()
