// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/testkit/time_bomb.h>
#include <vespa/vespalib/net/async_resolver.h>
#include <vespa/vespalib/net/socket_spec.h>
#include <atomic>

using namespace vespalib;

struct ResultSetter : public AsyncResolver::ResultHandler {
    SocketAddress &addr;
    std::atomic<bool> done;
    ResultSetter(SocketAddress &addr_out) noexcept : addr(addr_out), done(false) {}
    void handle_result(SocketAddress result) override {
        addr = result;
        done = true;
    }
};

struct MyClock : public AsyncResolver::Clock {
    using time_point = AsyncResolver::time_point;
    using seconds = AsyncResolver::seconds;
    time_point my_now;
    ~MyClock() override;
    void set_now(seconds t) {
        my_now = time_point(std::chrono::duration_cast<time_point::duration>(t));
    }
    AsyncResolver::time_point now() override { return my_now; }
};

MyClock::~MyClock() = default;

struct BlockingHostResolver : public AsyncResolver::HostResolver {
    CountDownLatch callers;
    Gate barrier;
    BlockingHostResolver(size_t num_callers) noexcept
        : callers(num_callers), barrier() {}
    std::string ip_address(const std::string &) override {
        callers.countDown();
        barrier.await();
        return "127.0.0.7";
    }
    void wait_for_callers() { callers.await(); }
    void release_callers() { barrier.countDown(); }
};

struct MyHostResolver : public AsyncResolver::HostResolver {
    std::mutex ip_lock;
    std::map<std::string,std::string> ip_map;
    std::map<std::string, size_t> ip_cnt;
    MyHostResolver() : ip_lock(), ip_map(), ip_cnt() {}
    ~MyHostResolver() override;
    std::string ip_address(const std::string &host) override {
        std::lock_guard<std::mutex> guard(ip_lock);
        ++ip_cnt[host];
        return ip_map[host];
    }
    void set_ip_addr(const std::string &host, const std::string &ip_addr) {
        std::lock_guard<std::mutex> guard(ip_lock);
        ip_map[host] = ip_addr;
    }
    size_t get_cnt(const std::string &host) {
        std::lock_guard<std::mutex> guard(ip_lock);
        return ip_cnt[host];
    }
    size_t get_total_cnt() {
        size_t total = 0;
        std::lock_guard<std::mutex> guard(ip_lock);
        for (const auto &entry: ip_cnt) {
            total += entry.second;
        }
        return total;
    }
};

MyHostResolver::~MyHostResolver() = default;

struct ResolveFixture {
    std::shared_ptr<MyClock> clock;
    std::shared_ptr<MyHostResolver> host_resolver;
    AsyncResolver::SP async_resolver;
    void set_ip_addr(const std::string &host, const std::string &ip) {
        host_resolver->set_ip_addr(host, ip);
    }
    size_t get_cnt(const std::string &host) { return host_resolver->get_cnt(host); }
    size_t get_total_cnt() { return host_resolver->get_total_cnt(); }
    void set_now(double s) { clock->set_now(MyClock::seconds(s)); }
    ResolveFixture(size_t max_cache_size = 10000)
        : clock(new MyClock()), host_resolver(new MyHostResolver()), async_resolver()
    {
        AsyncResolver::Params params;
        params.clock = clock;
        params.resolver = host_resolver;
        params.max_cache_size = max_cache_size;
        params.max_result_age = AsyncResolver::seconds(60.0);
        params.max_resolve_time = AsyncResolver::seconds(1.0);
        params.num_threads = 4;
        async_resolver = AsyncResolver::create(params);
        set_ip_addr("localhost", "127.0.0.1");
        set_ip_addr("127.0.0.1", "127.0.0.1");
        set_ip_addr("a", "127.0.1.1");
        set_ip_addr("b", "127.0.2.1");
        set_ip_addr("c", "127.0.3.1");
        set_ip_addr("d", "127.0.4.1");
        set_ip_addr("e", "127.0.5.1");
    }
    ~ResolveFixture();
    std::string resolve(const std::string &spec) {
        SocketAddress result;
        auto handler = std::make_shared<ResultSetter>(result);
        async_resolver->resolve_async(spec, handler);
        async_resolver->wait_for_pending_resolves();
        EXPECT_TRUE(handler->done);
        return result.spec();
    }
};
ResolveFixture::~ResolveFixture() = default;

//-----------------------------------------------------------------------------

TEST(AsyncResolverTest, require_that_async_resolver_internal_duration_type_is_appropriate) {
    AsyncResolver::seconds my_secs = std::chrono::milliseconds(500);
    EXPECT_EQ(my_secs.count(), 0.5);
}

TEST(AsyncResolverTest, require_that_default_async_resolver_is_tuned_as_expected) {
    AsyncResolver::Params params;
    EXPECT_EQ(params.max_cache_size, 10000u);
    EXPECT_EQ(params.max_result_age.count(), 60.0);
    EXPECT_EQ(params.max_resolve_time.count(), 1.0);
    EXPECT_EQ(params.num_threads, 4u);    
}

TEST(AsyncResolverTest, require_that_shared_async_resolver_is_shared) {
    auto resolver1 = AsyncResolver::get_shared();
    auto resolver2 = AsyncResolver::get_shared();
    EXPECT_TRUE(resolver1.get() != nullptr);
    EXPECT_TRUE(resolver2.get() != nullptr);
    EXPECT_TRUE(resolver1.get() == resolver2.get());
}

TEST(AsyncResolverTest, require_that_shared_async_resolver_can_resolve_connect_spec) {
    std::string spec("tcp/localhost:123");
    SocketAddress result;
    auto resolver = AsyncResolver::get_shared();
    auto handler = std::make_shared<ResultSetter>(result);
    resolver->resolve_async(spec, handler);
    resolver->wait_for_pending_resolves();
    std::string resolved = result.spec();
    fprintf(stderr, "resolver(spec:%s) -> '%s'\n", spec.c_str(), resolved.c_str());
    EXPECT_TRUE(handler->done);
    EXPECT_NE(resolved, spec);
    EXPECT_EQ(resolved, SocketSpec(spec).client_address().spec());
    EXPECT_EQ(resolved, SocketAddress::select_remote(123, "localhost").spec());
}

TEST(AsyncResolverTest, require_that_steady_clock_is_steady_clock) {
    AsyncResolver::SteadyClock clock;
    auto past = std::chrono::steady_clock::now();
    for (size_t i = 0; i < 10; ++i) {
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
        auto now = ((i % 2) == 0) ? clock.now() : std::chrono::steady_clock::now();
        EXPECT_GE(now.time_since_epoch().count(), past.time_since_epoch().count());
        past = now;
    }
}

TEST(AsyncResolverTest, require_that_simple_host_resolver_can_resolve_host_name) {
    std::string host_name("localhost");
    AsyncResolver::SimpleHostResolver resolver;
    auto resolved = resolver.ip_address(host_name);
    fprintf(stderr, "resolver(host_name:%s) -> '%s'\n", host_name.c_str(), resolved.c_str());
    EXPECT_NE(resolved, host_name);
    EXPECT_EQ(resolved, SocketSpec("tcp/localhost:123").client_address().ip_address());
    EXPECT_EQ(resolved, SocketAddress::select_remote(123, "localhost").ip_address());
}

TEST(AsyncResolverTest, require_that_alternative_host_name_resolution_works) {
    ResolveFixture f1;
    f1.set_ip_addr("host_name", "127.0.0.7");
    EXPECT_EQ(f1.resolve("tcp/host_name:123"), "tcp/127.0.0.7:123");
}

TEST(AsyncResolverTest, require_that_async_resolver_can_be_used_to_resolve_connect_specs_without_host_names) {
    ResolveFixture f1;
    EXPECT_EQ(f1.resolve("this is bogus"), "invalid");
    EXPECT_EQ(f1.resolve("tcp/123"), SocketSpec("tcp/123").client_address().spec());
    EXPECT_EQ(f1.resolve("ipc/file:my_socket"), "ipc/file:my_socket");
    EXPECT_EQ(f1.resolve("ipc/name:my_socket"), "ipc/name:my_socket");
    EXPECT_EQ(f1.get_total_cnt(), 0u);
}

TEST(AsyncResolverTest, require_that_resolved_hosts_are_cached) {
    ResolveFixture f1;
    EXPECT_EQ(f1.resolve("tcp/localhost:123"), "tcp/127.0.0.1:123");
    EXPECT_EQ(f1.resolve("tcp/localhost:456"), "tcp/127.0.0.1:456");
    EXPECT_EQ(f1.get_cnt("localhost"), 1u);
    EXPECT_EQ(f1.get_total_cnt(), 1u);
}

TEST(AsyncResolverTest, require_that_host_names_resolving_to_themselves__ip_addresses__are_not_cached) {
    ResolveFixture f1;
    EXPECT_EQ(f1.resolve("tcp/127.0.0.1:123"), "tcp/127.0.0.1:123");
    EXPECT_EQ(f1.resolve("tcp/127.0.0.1:456"), "tcp/127.0.0.1:456");
    EXPECT_EQ(f1.get_cnt("127.0.0.1"), 2u);
    EXPECT_EQ(f1.get_total_cnt(), 2u);
}

TEST(AsyncResolverTest, require_that_cached_results_expire_at_the_right_time) {
    ResolveFixture f1;
    EXPECT_EQ(f1.resolve("tcp/localhost:123"), "tcp/127.0.0.1:123");
    f1.set_ip_addr("localhost", "127.0.0.2");
    f1.set_now(59.5);
    EXPECT_EQ(f1.resolve("tcp/localhost:123"), "tcp/127.0.0.1:123");
    f1.set_now(60.0);
    EXPECT_EQ(f1.resolve("tcp/localhost:123"), "tcp/127.0.0.2:123");
    EXPECT_EQ(f1.get_cnt("localhost"), 2u);
    EXPECT_EQ(f1.get_total_cnt(), 2u);
}

TEST(AsyncResolverTest, require_that_max_cache_size_is_honored) {
    ResolveFixture f1(3);
    EXPECT_EQ(f1.resolve("tcp/a:123"), "tcp/127.0.1.1:123");
    EXPECT_EQ(f1.resolve("tcp/b:123"), "tcp/127.0.2.1:123");
    EXPECT_EQ(f1.resolve("tcp/c:123"), "tcp/127.0.3.1:123");
    EXPECT_EQ(f1.resolve("tcp/d:123"), "tcp/127.0.4.1:123");
    EXPECT_EQ(f1.get_total_cnt(), 4u);
    EXPECT_EQ(f1.resolve("tcp/b:123"), "tcp/127.0.2.1:123");
    EXPECT_EQ(f1.get_total_cnt(), 4u);
    EXPECT_EQ(f1.resolve("tcp/a:123"), "tcp/127.0.1.1:123");
    EXPECT_EQ(f1.get_total_cnt(), 5u);
    EXPECT_EQ(f1.resolve("tcp/b:123"), "tcp/127.0.2.1:123");
    EXPECT_EQ(f1.get_total_cnt(), 6u);
}

TEST(AsyncResolverTest, require_that_missing_ip_address_gives_invalid_address) {
    ResolveFixture f1;
    f1.set_ip_addr("localhost", "");
    EXPECT_EQ(f1.resolve("tcp/localhost:123"), "invalid");
    EXPECT_EQ(f1.get_cnt("localhost"), 1u);
    EXPECT_EQ(f1.get_total_cnt(), 1u);
}

TEST(AsyncResolverTest, require_that_empty_lookup_results_are_cached) {
    ResolveFixture f1;
    f1.set_ip_addr("localhost", "");
    EXPECT_EQ(f1.resolve("tcp/localhost:123"), "invalid");
    f1.set_ip_addr("localhost", "127.0.0.1");
    f1.set_now(59.5);
    EXPECT_EQ(f1.resolve("tcp/localhost:123"), "invalid");
    f1.set_now(60.0);
    EXPECT_EQ(f1.resolve("tcp/localhost:123"), "tcp/127.0.0.1:123");
    EXPECT_EQ(f1.get_cnt("localhost"), 2u);
    EXPECT_EQ(f1.get_total_cnt(), 2u);
}

TEST(AsyncResolverTest, require_that_multiple_cache_entries_can_be_evicted_at_the_same_time) {
    ResolveFixture f1;
    EXPECT_EQ(f1.resolve("tcp/a:123"), "tcp/127.0.1.1:123");
    f1.set_now(10.0);
    EXPECT_EQ(f1.resolve("tcp/b:123"), "tcp/127.0.2.1:123");
    f1.set_now(20.0);
    EXPECT_EQ(f1.resolve("tcp/c:123"), "tcp/127.0.3.1:123");
    f1.set_now(30.0);
    EXPECT_EQ(f1.resolve("tcp/d:123"), "tcp/127.0.4.1:123");
    f1.set_now(40.0);
    EXPECT_EQ(f1.resolve("tcp/e:123"), "tcp/127.0.5.1:123");
    EXPECT_EQ(f1.get_total_cnt(), 5u);
    f1.set_now(85.0); // c too old, d still good
    EXPECT_EQ(f1.resolve("tcp/c:123"), "tcp/127.0.3.1:123");
    EXPECT_EQ(f1.get_total_cnt(), 6u);
    EXPECT_EQ(f1.resolve("tcp/d:123"), "tcp/127.0.4.1:123");
    EXPECT_EQ(f1.get_total_cnt(), 6u);
    f1.set_now(0.0); // a has already been evicted from cache
    EXPECT_EQ(f1.resolve("tcp/a:123"), "tcp/127.0.1.1:123");
    EXPECT_EQ(f1.get_total_cnt(), 7u);
}

TEST(AsyncResolverTest, require_that_slow_host_lookups_trigger_warning__manual_log_inspection) {
    TimeBomb f1(60);
    auto my_clock = std::make_shared<MyClock>();
    auto host_resolver = std::make_shared<BlockingHostResolver>(1);
    AsyncResolver::Params params;
    params.clock = my_clock;
    params.resolver = host_resolver;
    params.max_resolve_time = AsyncResolver::seconds(1.0);
    auto resolver = AsyncResolver::create(params);
    SocketAddress result;
    auto handler = std::make_shared<ResultSetter>(result);
    resolver->resolve_async("tcp/some_host:123", handler);
    host_resolver->wait_for_callers();
    my_clock->set_now(MyClock::seconds(1.0));
    EXPECT_TRUE(!handler->done);
    host_resolver->release_callers();
    resolver->wait_for_pending_resolves();
    EXPECT_TRUE(handler->done);
    EXPECT_EQ(result.spec(), "tcp/127.0.0.7:123");
}

TEST(AsyncResolverTest, require_that_discarding_result_handlers_will_avoid_pending_work__but_complete_started_work) {
    TimeBomb f1(60);
    auto host_resolver = std::make_shared<BlockingHostResolver>(2);
    AsyncResolver::Params params;
    params.resolver = host_resolver;
    params.num_threads = 2;
    auto resolver = AsyncResolver::create(params);
    SocketAddress result1;
    SocketAddress result2;
    SocketAddress result3;
    auto handler1 = std::make_shared<ResultSetter>(result1);
    auto handler2 = std::make_shared<ResultSetter>(result2);
    auto handler3 = std::make_shared<ResultSetter>(result3);
    resolver->resolve_async("tcp/x:123", handler1);
    resolver->resolve_async("tcp/y:123", handler2);
    resolver->resolve_async("tcp/z:123", handler3);
    host_resolver->wait_for_callers();
    handler1.reset();
    handler2.reset();
    handler3.reset();
    host_resolver->release_callers();
    resolver->wait_for_pending_resolves();
    EXPECT_EQ(result1.spec(), "tcp/127.0.0.7:123");
    EXPECT_EQ(result2.spec(), "tcp/127.0.0.7:123");
    EXPECT_EQ(result3.spec(), "invalid");
}

TEST(AsyncResolverTest, require_that_cache_races_can_be_provoked) {
    TimeBomb f1(60);
    auto host_resolver = std::make_shared<BlockingHostResolver>(2);
    AsyncResolver::Params params;
    params.resolver = host_resolver;
    params.num_threads = 2;
    auto resolver = AsyncResolver::create(params);
    SocketAddress result1;
    SocketAddress result2;
    auto handler1 = std::make_shared<ResultSetter>(result1);
    auto handler2 = std::make_shared<ResultSetter>(result2);
    resolver->resolve_async("tcp/same_host:123", handler1);
    resolver->resolve_async("tcp/same_host:123", handler2);
    host_resolver->wait_for_callers();
    host_resolver->release_callers();
    resolver->wait_for_pending_resolves();
    EXPECT_EQ(result1.spec(), "tcp/127.0.0.7:123");
    EXPECT_EQ(result2.spec(), "tcp/127.0.0.7:123");
}

GTEST_MAIN_RUN_ALL_TESTS()
