// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/testkit/time_bomb.h>
#include <vespa/vespalib/net/selector.h>
#include <vespa/vespalib/net/socket_spec.h>
#include <vespa/vespalib/net/server_socket.h>
#include <vespa/vespalib/net/socket_options.h>
#include <vespa/vespalib/net/socket.h>
#include <vespa/vespalib/test/nexus.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/test/socket_options_verifier.h>
#include <thread>
#include <functional>
#include <unistd.h>
#include <sys/stat.h>

using namespace vespalib;
using vespalib::test::Nexus;

class SocketTest : public ::testing::Test {
protected:
    static bool ipv4_enabled;
    static bool ipv6_enabled;

    SocketTest();
    ~SocketTest() override;
    static void SetUpTestSuite();
    static void TearDownTestSuite();
    static int my_inet();
};

bool SocketTest::ipv4_enabled = false;
bool SocketTest::ipv6_enabled = false;

SocketTest::SocketTest()
    : testing::Test()
{
}

SocketTest::~SocketTest() = default;

void
SocketTest::SetUpTestSuite()
{
    auto list = SocketAddress::resolve(4080);
    for (const auto &addr : list) {
        (void) addr;
        ipv4_enabled |= addr.is_ipv4();
        ipv6_enabled |= addr.is_ipv6();
    }
    ASSERT_TRUE(ipv4_enabled || ipv6_enabled) << "tcp/ip support not detected";
}

void
SocketTest::TearDownTestSuite()
{
}

int
SocketTest::my_inet()
{
    if (ipv6_enabled) {
        return AF_INET6;
    } else {
        return AF_INET;
    }
}

bool is_socket(const vespalib::string &path) {
    struct stat info;
    if (path.empty() || (lstat(path.c_str(), &info) != 0)) {
        return false;
    }
    return S_ISSOCK(info.st_mode);
}

bool is_file(const vespalib::string &path) {
    struct stat info;
    if (path.empty() || (lstat(path.c_str(), &info) != 0)) {
        return false;
    }
    return S_ISREG(info.st_mode);
}

void remove_file(const vespalib::string &path) {
    unlink(path.c_str());
}

void replace_file(const vespalib::string &path, const vespalib::string &data) {
    remove_file(path);
    int fd = creat(path.c_str(), 0600);
    ASSERT_NE(fd, -1);
    ASSERT_EQ(write(fd, data.data(), data.size()), ssize_t(data.size()));
    close(fd);
}

vespalib::string get_meta(const SocketAddress &addr) {
    vespalib::string meta;
    if (addr.is_ipv4()) {
        meta = "ipv4";
    } else if (addr.is_ipv6()) {
        meta = "ipv6";
    } else if (addr.is_ipc()) {
        meta = "ipc";
    } else {
        meta = "???";
    }
    if (addr.is_wildcard()) {
        meta += " wildcard";
    }
    if (addr.is_abstract()) {
        meta += " abstract";
    }
    return meta;
}

vespalib::string read_bytes(SocketHandle &socket, size_t wanted_bytes) {
    char tmp[64];
    vespalib::string result;
    while (result.size() < wanted_bytes) {
        size_t read_size = std::min(sizeof(tmp), wanted_bytes - result.size());
        ssize_t read_result = socket.read(tmp, read_size);
        if (read_result <= 0) {
            return result;
        }
        result.append(tmp, read_result);
    }
    return result;
}

void verify_socket_io(bool is_server, SocketHandle &socket) {
    vespalib::string server_message = "hello, this is the server speaking";
    vespalib::string client_message = "please pick up, I need to talk to you";
    if(is_server) {
        ssize_t written = socket.write(server_message.data(), server_message.size());
        EXPECT_EQ(written, ssize_t(server_message.size()));
        vespalib::string read = read_bytes(socket, client_message.size());
        EXPECT_EQ(client_message, read);
    } else {
        ssize_t written = socket.write(client_message.data(), client_message.size());
        EXPECT_EQ(written, ssize_t(client_message.size()));
        vespalib::string read = read_bytes(socket, server_message.size());
        EXPECT_EQ(server_message, read);
    }
}

SocketHandle connect_sockets(bool is_server, ServerSocket &server_socket) {
    if (is_server) {
        return server_socket.accept();
    } else {
        auto server = server_socket.address();
        auto spec = server.spec();
        auto client = SocketSpec(spec).client_address();
        fprintf(stderr, "connecting to '%s' (server: %s) (client: %s)\n",
                spec.c_str(), get_meta(server).c_str(), get_meta(client).c_str());
        return client.connect();
    }
}

//-----------------------------------------------------------------------------

TEST_F(SocketTest, my_local_address)
{
    auto list = SocketAddress::resolve(4080);
    fprintf(stderr, "resolve(4080):\n");
    for (const auto &addr: list) {
        EXPECT_TRUE(addr.is_wildcard());
        EXPECT_TRUE(addr.is_ipv4() || addr.is_ipv6());
        EXPECT_TRUE(!addr.is_ipc());
        EXPECT_TRUE(!addr.is_abstract());
        EXPECT_EQ(addr.port(), 4080);
        fprintf(stderr, "  %s (%s)\n", addr.spec().c_str(), get_meta(addr).c_str());
    }
}

TEST_F(SocketTest, yahoo_com_address)
{
    auto list = SocketAddress::resolve(80, "yahoo.com");
    fprintf(stderr, "resolve(80, 'yahoo.com'):\n");
    for (const auto &addr: list) {
        EXPECT_TRUE(!addr.is_wildcard());
        EXPECT_TRUE(addr.is_ipv4() || addr.is_ipv6());
        EXPECT_TRUE(!addr.is_ipc());
        EXPECT_TRUE(!addr.is_abstract());
        EXPECT_EQ(addr.port(), 80);
        fprintf(stderr, "  %s (%s)\n", addr.spec().c_str(), get_meta(addr).c_str());
    }
}

TEST_F(SocketTest, ipc_address_with_path)
{
    auto addr = SocketAddress::from_path("my_socket");
    EXPECT_TRUE(!addr.is_ipv4());
    EXPECT_TRUE(!addr.is_ipv6());
    EXPECT_TRUE(addr.is_ipc());
    EXPECT_TRUE(!addr.is_abstract());
    EXPECT_TRUE(!addr.is_wildcard());
    EXPECT_EQ(addr.port(), -1);
    EXPECT_EQ(vespalib::string("my_socket"), addr.path());
    EXPECT_TRUE(addr.name().empty());
    fprintf(stderr, "from_path(my_socket)\n");
    fprintf(stderr, "  %s (%s)\n", addr.spec().c_str(), get_meta(addr).c_str());
}

TEST_F(SocketTest, ipc_address_with_name)
{
    auto addr = SocketAddress::from_name("my_socket");
    EXPECT_TRUE(!addr.is_ipv4());
    EXPECT_TRUE(!addr.is_ipv6());
    EXPECT_TRUE(addr.is_ipc());
    EXPECT_TRUE(addr.is_abstract());
    EXPECT_TRUE(!addr.is_wildcard());
    EXPECT_EQ(addr.port(), -1);
    EXPECT_TRUE(addr.path().empty());
    EXPECT_EQ(vespalib::string("my_socket"), addr.name());
    fprintf(stderr, "from_path(my_socket)\n");
    fprintf(stderr, "  %s (%s)\n", addr.spec().c_str(), get_meta(addr).c_str());
}

TEST_F(SocketTest, local_client_and_server_addresses) {
    auto spec = SocketSpec("tcp/123");
    auto client = spec.client_address();
    auto server = spec.server_address();
    EXPECT_TRUE(!client.is_wildcard());
    EXPECT_EQ(client.port(), 123);
    EXPECT_TRUE(server.is_wildcard());
    EXPECT_EQ(server.port(), 123);
    fprintf(stderr, "client(tcp/123): %s (%s)\n", client.spec().c_str(), get_meta(client).c_str());
    fprintf(stderr, "server(tcp/123): %s (%s)\n", server.spec().c_str(), get_meta(server).c_str());
}

TEST_F(SocketTest, require_that_basic_socket_io_works)
{
    constexpr size_t num_threads = 2;
    ServerSocket f1("tcp/0");
    TimeBomb f2(60);
    auto task = [&f1](Nexus& ctx) {
                    bool is_server = (ctx.thread_id() == 0);
                    SocketHandle socket = connect_sockets(is_server, f1);
                    verify_socket_io(is_server, socket);
                };
    Nexus::run(num_threads, task);
}

TEST_F(SocketTest, require_that_basic_unix_domain_socket_io_works_with_path)
{
    constexpr size_t num_threads = 2;
    ServerSocket f1("ipc/file:my_socket");
    TimeBomb f2(60);
    auto task = [&f1](Nexus& ctx) {
                    bool is_server = (ctx.thread_id() == 0);
                    SocketHandle socket = connect_sockets(is_server, f1);
                    verify_socket_io(is_server, socket);
                };
    Nexus::run(num_threads, task);
}

TEST_F(SocketTest, require_that_server_accept_can_be_interrupted)
{
    constexpr size_t num_threads = 2;
    ServerSocket f1("tcp/0");
    TimeBomb f2(60);
    auto task = [&f1](Nexus& ctx) {
                    bool is_server = (ctx.thread_id() == 0);
                    if (is_server) {
                        fprintf(stderr, "--> calling accept\n");
                        SocketHandle socket = f1.accept();
                        fprintf(stderr, "<-- accept returned\n");
                        EXPECT_TRUE(!socket.valid());
                    } else {
                        std::this_thread::sleep_for(std::chrono::milliseconds(20));
                        fprintf(stderr, "--- closing server socket\n");
                        f1.shutdown();
                    }
                };
    Nexus::run(num_threads, task);
}

TEST_F(SocketTest, require_that_socket_file_is_removed_by_server_socket_when_destructed)
{
    remove_file("my_socket");
    ServerSocket server("ipc/file:my_socket");
    EXPECT_TRUE(server.valid());
    EXPECT_TRUE(is_socket("my_socket"));
    server = ServerSocket();
    EXPECT_TRUE(!is_socket("my_socket"));
}

TEST_F(SocketTest, require_that_socket_file_is_only_removed_on_destruction_if_it_is_a_socket)
{
    remove_file("my_socket");
    ServerSocket server("ipc/file:my_socket");
    EXPECT_TRUE(server.valid());
    EXPECT_TRUE(is_socket("my_socket"));
    replace_file("my_socket", "hello\n");
    server = ServerSocket();
    EXPECT_TRUE(is_file("my_socket"));
    remove_file("my_socket");
}

TEST_F(SocketTest, require_that_a_server_socket_will_fail_to_listen_to_a_path_that_is_already_a_regular_file)
{
    replace_file("my_socket", "hello\n");
    ServerSocket server("ipc/file:my_socket");
    EXPECT_TRUE(!server.valid());
    server = ServerSocket();
    EXPECT_TRUE(is_file("my_socket"));
    remove_file("my_socket");
}

TEST_F(SocketTest, require_that_a_server_socket_will_fail_to_listen_to_a_path_that_is_already_taken_by_another_server)
{
    remove_file("my_socket");
    ServerSocket server1("ipc/file:my_socket");
    ServerSocket server2("ipc/file:my_socket");
    EXPECT_TRUE(server1.valid());
    EXPECT_TRUE(!server2.valid());
    EXPECT_TRUE(is_socket("my_socket"));
    server1 = ServerSocket();
    EXPECT_TRUE(!is_socket("my_socket"));
}

TEST_F(SocketTest, require_that_a_server_socket_will_remove_an_old_socket_file_if_it_cannot_be_connected_to)
{
    remove_file("my_socket");
    {
        SocketHandle server_handle = SocketAddress::from_path("my_socket").listen();
        EXPECT_TRUE(is_socket("my_socket"));
    }
    EXPECT_TRUE(is_socket("my_socket"));
    ServerSocket server("ipc/file:my_socket");
    EXPECT_TRUE(server.valid());
    server = ServerSocket();
    EXPECT_TRUE(!is_socket("my_socket"));
}

#ifdef __linux__
TEST_F(SocketTest, require_that_basic_unix_domain_socket_io_works_with_name)
{
    constexpr size_t num_threads = 2;
    ServerSocket f1(make_string("ipc/name:my_socket-%d", int(getpid())));
    TimeBomb f2(60);
    auto task = [&f1](Nexus& ctx) {
                    bool is_server = (ctx.thread_id() == 0);
                    SocketHandle socket = connect_sockets(is_server, f1);
                    verify_socket_io(is_server, socket);
                };
    Nexus::run(num_threads, task);
}

TEST_F(SocketTest, require_that_two_server_sockets_cannot_have_the_same_abstract_unix_domain_socket_name)
{
    vespalib::string spec = make_string("ipc/name:my_socket-%d", int(getpid()));
    ServerSocket server1(spec);
    ServerSocket server2(spec);
    EXPECT_TRUE(server1.valid());
    EXPECT_TRUE(!server2.valid());
}

TEST_F(SocketTest, require_that_abstract_socket_names_are_freed_when_the_server_socket_is_destructed)
{
    vespalib::string spec = make_string("ipc/name:my_socket-%d", int(getpid()));
    ServerSocket server1(spec);
    EXPECT_TRUE(server1.valid());
    server1 = ServerSocket();
    ServerSocket server2(spec);
    EXPECT_TRUE(server2.valid());
}

TEST_F(SocketTest, require_that_abstract_sockets_do_not_have_socket_files)
{
    vespalib::string name = make_string("my_socket-%d", int(getpid()));
    ServerSocket server(SocketSpec::from_name(name));
    EXPECT_TRUE(server.valid());
    EXPECT_TRUE(!is_socket(name));
    EXPECT_TRUE(!is_file(name));
}

TEST_F(SocketTest, require_that_abstract_and_file_based_unix_domain_sockets_are_not_in_conflict)
{
    constexpr size_t num_threads = 4;
    ServerSocket f1(make_string("ipc/file:my_socket-%d", int(getpid())));
    ServerSocket f2(make_string("ipc/name:my_socket-%d", int(getpid())));
    TimeBomb f3(60);
    auto task = [&f1,&f2](Nexus& ctx) {
                    auto thread_id = ctx.thread_id();
                    bool is_server = ((thread_id % 2) == 0);
                    ServerSocket &server_socket = ((thread_id / 2) == 0) ? f1 : f2;
                    SocketHandle socket = connect_sockets(is_server, server_socket);
                    verify_socket_io(is_server, socket);
                };
    Nexus::run(num_threads, task);
}
#endif

TEST_F(SocketTest, require_that_sockets_can_be_set_blocking_and_non_blocking)
{
    SocketHandle handle(socket(my_inet(), SOCK_STREAM, 0));
    test::SocketOptionsVerifier verifier(handle.get());
    EXPECT_TRUE(!SocketOptions::set_blocking(-1, true));
    EXPECT_TRUE(handle.set_blocking(true));
    {
        SCOPED_TRACE("verify blocking true");
        verifier.verify_blocking(true);
    }
    EXPECT_TRUE(handle.set_blocking(false));
    {
        SCOPED_TRACE("verify blocking false");
        verifier.verify_blocking(false);
    }
}

TEST_F(SocketTest, require_that_server_sockets_use_non_blocking_underlying_socket)
{
    ServerSocket tcp_server("tcp/0");
    ServerSocket ipc_server("ipc/file:my_socket");
    test::SocketOptionsVerifier tcp_verifier(tcp_server.get_fd());
    test::SocketOptionsVerifier ipc_verifier(ipc_server.get_fd());
    {
        SCOPED_TRACE("verify tcp nonblocking");
        tcp_verifier.verify_blocking(false);
    }
    {
        SCOPED_TRACE("verify ipc nonblocking");
        ipc_verifier.verify_blocking(false);
    }
}

TEST_F(SocketTest, require_that_tcp_nodelay_can_be_enabled_and_disabled)
{
    SocketHandle handle(socket(my_inet(), SOCK_STREAM, 0));
    test::SocketOptionsVerifier verifier(handle.get());
    EXPECT_TRUE(!SocketOptions::set_nodelay(-1, true));
    EXPECT_TRUE(handle.set_nodelay(true));
    {
        SCOPED_TRACE("verify nodelay true");
        verifier.verify_nodelay(true);
    }
    EXPECT_TRUE(handle.set_nodelay(false));
    {
        SCOPED_TRACE("verify nodelay false");
        verifier.verify_nodelay(false);
    }
}

TEST_F(SocketTest, require_that_reuse_addr_can_be_set_and_cleared)
{
    SocketHandle handle(socket(my_inet(), SOCK_STREAM, 0));
    test::SocketOptionsVerifier verifier(handle.get());
    EXPECT_TRUE(!SocketOptions::set_reuse_addr(-1, true));
    EXPECT_TRUE(handle.set_reuse_addr(true));
    {
        SCOPED_TRACE("verify reuse addr true");
        verifier.verify_reuse_addr(true);
    }
    EXPECT_TRUE(handle.set_reuse_addr(false));
    {
        SCOPED_TRACE("verify reuse addr false");
        verifier.verify_reuse_addr(false);
    }
}

TEST_F(SocketTest, require_that_ipv6_only_can_be_set_and_cleared)
{
    if (ipv6_enabled) {
        SocketHandle handle(socket(my_inet(), SOCK_STREAM, 0));
        test::SocketOptionsVerifier verifier(handle.get());
        EXPECT_TRUE(!SocketOptions::set_ipv6_only(-1, true));
        EXPECT_TRUE(handle.set_ipv6_only(true));
        {
            SCOPED_TRACE("verify ipv6 only true");
            verifier.verify_ipv6_only(true);
        }
        EXPECT_TRUE(handle.set_ipv6_only(false));
        {
            SCOPED_TRACE("verify ipv6 only false");
            verifier.verify_ipv6_only(false);
        }
    } else {
        fprintf(stderr, "WARNING: skipping ipv6_only test since ipv6 is disabled");
    }
}

TEST_F(SocketTest, require_that_tcp_keepalive_can_be_set_and_cleared)
{
    SocketHandle handle(socket(my_inet(), SOCK_STREAM, 0));
    test::SocketOptionsVerifier verifier(handle.get());
    EXPECT_TRUE(!SocketOptions::set_keepalive(-1, true));
    EXPECT_TRUE(handle.set_keepalive(true));
    {
        SCOPED_TRACE("verify keepalive true");
        verifier.verify_keepalive(true);
    }
    EXPECT_TRUE(handle.set_keepalive(false));
    {
        SCOPED_TRACE("verify keepalive false");
        verifier.verify_keepalive(false);
    }
}

TEST_F(SocketTest, require_that_tcp_lingering_can_be_adjusted)
{
    SocketHandle handle(socket(my_inet(), SOCK_STREAM, 0));
    test::SocketOptionsVerifier verifier(handle.get());
    EXPECT_TRUE(!SocketOptions::set_linger(-1, true, 0));
    EXPECT_TRUE(handle.set_linger(true, 0));
    {
        SCOPED_TRACE("verify linger true 0");
        verifier.verify_linger(true, 0);
    }
    EXPECT_TRUE(handle.set_linger(true, 10));
    {
        SCOPED_TRACE("verify linger true 10");
        verifier.verify_linger(true, 10);
    }
    EXPECT_TRUE(handle.set_linger(false, 0));
    {
        SCOPED_TRACE("verify linger false 0");
        verifier.verify_linger(false, 0);
    }
    EXPECT_TRUE(handle.set_linger(false, 10));
    {
        SCOPED_TRACE("verify linger false 0 (overridden)");
        verifier.verify_linger(false, 0);
    }
}

SocketHandle connect_async(const SocketAddress &addr) {
    struct ConnectContext {
        SocketHandle handle;
        bool connect_done = false;
        int error = 0;
        void handle_wakeup() {}
        void handle_event(ConnectContext &ctx, bool, bool write) {
            if ((&ctx == this) && write) {
                connect_done = true;
                error = ctx.handle.get_so_error();
            }
        }
    };
    Selector<ConnectContext> selector;
    ConnectContext ctx;
    ctx.handle = addr.connect_async();
    EXPECT_TRUE(ctx.handle.valid());
    test::SocketOptionsVerifier verifier(ctx.handle.get());
    {
        SCOPED_TRACE("verify blocking false");
        verifier.verify_blocking(false);
    }
    if (ctx.handle.valid()) {
        selector.add(ctx.handle.get(), ctx, true, true);
        while (!ctx.connect_done) {
            selector.poll(1000);
            selector.dispatch(ctx);
        }
        selector.remove(ctx.handle.get());
    }
    EXPECT_EQ(ctx.error, 0);
    return std::move(ctx.handle);
}

TEST_F(SocketTest, require_that_async_connect_pattern_works)
{
    constexpr size_t num_threads = 2;
    ServerSocket f1("tcp/0");
    TimeBomb f2(60);
    auto task = [&f1](Nexus& ctx) {
                    if (ctx.thread_id() == 0) {
                        SocketHandle socket = f1.accept();
                        EXPECT_TRUE(socket.valid());
                        SCOPED_TRACE("verify socket io true");
                        verify_socket_io(true, socket);
                    } else {
                        SocketAddress addr = SocketSpec::from_port(f1.address().port()).client_address();
                        SocketHandle socket = connect_async(addr);
                        socket.set_blocking(true);
                        SCOPED_TRACE("verify socket io false");
                        verify_socket_io(false, socket);
                    }
                };
    Nexus::run(num_threads, task);
}

GTEST_MAIN_RUN_ALL_TESTS()
