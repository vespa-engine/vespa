// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/net/lazy_resolver.h>
#include <vespa/vespalib/net/socket_spec.h>

using namespace vespalib;

TEST("require that lazy resolver internal duration type is appropriate") {
    LazyResolver::seconds my_secs = std::chrono::milliseconds(500);
    EXPECT_EQUAL(my_secs.count(), 0.5);
}

TEST("require that lazy resolver can be used to resolve connect spec") {
    vespalib::string spec("tcp/localhost:123");
    auto resolver = LazyResolver::create();
    auto address = resolver->make_address(spec);
    auto resolved = address->resolve();
    fprintf(stderr, "resolver(spec:%s) -> '%s'\n", spec.c_str(), resolved.c_str());
    EXPECT_EQUAL(spec, address->spec());
    EXPECT_NOT_EQUAL(resolved, address->spec());
    EXPECT_EQUAL(resolved, SocketSpec(spec).client_address().spec());
    EXPECT_EQUAL(resolved, SocketAddress::select_remote(123, "localhost").spec());
}

TEST("require that lazy resolver can be used to resolve host name") {
    vespalib::string host_name("localhost");
    auto resolver = LazyResolver::create();
    auto host = resolver->make_host(host_name);
    auto resolved = host->resolve();
    fprintf(stderr, "resolver(host_name:%s) -> '%s'\n", host_name.c_str(), resolved.c_str());
    EXPECT_EQUAL(host_name, host->host_name());
    EXPECT_NOT_EQUAL(resolved, host->host_name());
    EXPECT_EQUAL(resolved, SocketSpec("tcp/localhost:123").client_address().ip_address());
    EXPECT_EQUAL(resolved, SocketAddress::select_remote(123, "localhost").ip_address());
    EXPECT_EQUAL(resolved, LazyResolver::default_resolve_host(host_name));
}

vespalib::string dummy_resolve_host(const vespalib::string &) { return "ip.addr"; }

TEST("require that host name resolve function can be overridden (bonus: slow resolve warning)") {
    LazyResolver::Params params;
    params.resolve_host = dummy_resolve_host;
    params.max_resolve_time = LazyResolver::seconds(0);
    auto resolver = LazyResolver::create(params);
    EXPECT_EQUAL(resolver->make_address("tcp/host_name:123")->resolve(), "tcp/ip.addr:123");
}

struct ResolveFixture {
    std::mutex ip_lock;
    std::map<vespalib::string,vespalib::string> ip_map;
    std::map<vespalib::string, size_t> ip_cnt;
    LazyResolver::SP resolver;
    void set_ip_addr(const vespalib::string &host, const vespalib::string &ip_addr) {
        std::lock_guard<std::mutex> guard(ip_lock);
        ip_map[host] = ip_addr;
    }
    vespalib::string get_ip_addr(const vespalib::string &host) {
        std::lock_guard<std::mutex> guard(ip_lock);
        ++ip_cnt[host];
        return ip_map[host];
    }
    size_t get_cnt(const vespalib::string &host) {
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
    ResolveFixture(double max_age) : ip_lock(), ip_map(), ip_cnt(), resolver() {
        LazyResolver::Params params;
        params.resolve_host = [this](const vespalib::string &host_name){ return get_ip_addr(host_name); };
        params.max_result_age = LazyResolver::seconds(max_age);
        resolver = LazyResolver::create(std::move(params));
        set_ip_addr("localhost", "127.0.0.1");
        set_ip_addr("127.0.0.1", "127.0.0.1");
    }
    LazyResolver::Address::SP make(const vespalib::string &spec) { return resolver->make_address(spec); }
};

TEST_F("require that lazy resolver can be used to resolve connect specs without host names", ResolveFixture(300)) {
    EXPECT_EQUAL(f1.make("this is bogus")->resolve(), "this is bogus");
    EXPECT_EQUAL(f1.make("tcp/123")->resolve(), "tcp/123");
    EXPECT_EQUAL(f1.make("ipc/file:my_socket")->resolve(), "ipc/file:my_socket");
    EXPECT_EQUAL(f1.make("ipc/name:my_socket")->resolve(), "ipc/name:my_socket");
    f1.resolver->wait_for_pending_updates();
    EXPECT_EQUAL(f1.get_total_cnt(), 0u);
}

TEST_F("require that resolved hosts can be shared between addresses", ResolveFixture(300)) {
    auto addr1 = f1.make("tcp/localhost:123");
    auto addr2 = f1.make("tcp/localhost:456");
    EXPECT_EQUAL(addr1->resolve(), "tcp/127.0.0.1:123");
    EXPECT_EQUAL(addr2->resolve(), "tcp/127.0.0.1:456");
    f1.resolver->wait_for_pending_updates();
    EXPECT_EQUAL(f1.get_cnt("localhost"), 1u);
    EXPECT_EQUAL(f1.get_total_cnt(), 1u);
}

TEST_F("require that resolved hosts are discarded when not used", ResolveFixture(300)) {
    EXPECT_EQUAL(f1.make("tcp/localhost:123")->resolve(), "tcp/127.0.0.1:123");
    EXPECT_EQUAL(f1.make("tcp/localhost:456")->resolve(), "tcp/127.0.0.1:456");
    f1.resolver->wait_for_pending_updates();
    EXPECT_EQUAL(f1.get_cnt("localhost"), 2u);
    EXPECT_EQUAL(f1.get_total_cnt(), 2u);
}

TEST_F("require that host names resolving to themselves (ip addresses) are not shared", ResolveFixture(300)) {
    auto addr1 = f1.make("tcp/127.0.0.1:123");
    auto addr2 = f1.make("tcp/127.0.0.1:456");
    EXPECT_EQUAL(addr1->resolve(), "tcp/127.0.0.1:123");
    EXPECT_EQUAL(addr2->resolve(), "tcp/127.0.0.1:456");
    f1.resolver->wait_for_pending_updates();
    EXPECT_EQUAL(f1.get_cnt("127.0.0.1"), 2u);
    EXPECT_EQUAL(f1.get_total_cnt(), 2u);
}

TEST_F("require that resolve changes can be detected", ResolveFixture(0)) {
    auto addr = f1.make("tcp/localhost:123");
    f1.set_ip_addr("localhost", "127.0.0.2");
    EXPECT_EQUAL(addr->resolve(), "tcp/127.0.0.1:123");
    f1.resolver->wait_for_pending_updates();
    f1.set_ip_addr("localhost", "127.0.0.3");
    EXPECT_EQUAL(addr->resolve(), "tcp/127.0.0.2:123");
    f1.resolver->wait_for_pending_updates();
    EXPECT_EQUAL(addr->resolve(), "tcp/127.0.0.3:123");
    f1.resolver->wait_for_pending_updates();
    EXPECT_EQUAL(f1.get_cnt("localhost"), 4u);
    EXPECT_EQUAL(f1.get_total_cnt(), 4u);
}

TEST_F("require that resolve changes are not detected when old results are still fresh", ResolveFixture(300)) {
    auto addr = f1.make("tcp/localhost:123");
    f1.set_ip_addr("localhost", "127.0.0.2");
    EXPECT_EQUAL(addr->resolve(), "tcp/127.0.0.1:123");
    f1.resolver->wait_for_pending_updates();
    f1.set_ip_addr("localhost", "127.0.0.3");
    EXPECT_EQUAL(addr->resolve(), "tcp/127.0.0.1:123");
    f1.resolver->wait_for_pending_updates();
    EXPECT_EQUAL(addr->resolve(), "tcp/127.0.0.1:123");
    f1.resolver->wait_for_pending_updates();
    EXPECT_EQUAL(f1.get_cnt("localhost"), 1u);
    EXPECT_EQUAL(f1.get_total_cnt(), 1u);
}

TEST_F("require that missing ip address gives invalid spec", ResolveFixture(300)) {
    f1.set_ip_addr("localhost", "");
    auto addr = f1.make("tcp/localhost:123");
    EXPECT_EQUAL(addr->resolve(), "invalid");
    f1.resolver->wait_for_pending_updates();
    EXPECT_EQUAL(f1.get_cnt("localhost"), 1u);
    EXPECT_EQUAL(f1.get_total_cnt(), 1u);
}

TEST_F("require that all ip address results are treated equally (including empty ones)", ResolveFixture(0)) {
    auto addr = f1.make("tcp/localhost:123");
    f1.set_ip_addr("localhost", "");
    EXPECT_EQUAL(addr->resolve(), "tcp/127.0.0.1:123");
    f1.resolver->wait_for_pending_updates();
    f1.set_ip_addr("localhost", "127.0.0.2");
    EXPECT_EQUAL(addr->resolve(), "invalid");
    f1.resolver->wait_for_pending_updates();
    EXPECT_EQUAL(addr->resolve(), "tcp/127.0.0.2:123");
    f1.resolver->wait_for_pending_updates();
    EXPECT_EQUAL(f1.get_cnt("localhost"), 4u);
    EXPECT_EQUAL(f1.get_total_cnt(), 4u);
}

TEST_MAIN() { TEST_RUN_ALL(); }
