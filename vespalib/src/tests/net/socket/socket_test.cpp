// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/net/socket_spec.h>
#include <vespa/vespalib/net/server_socket.h>
#include <vespa/vespalib/net/socket_options.h>
#include <vespa/vespalib/net/socket.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/test/socket_options_verifier.h>
#include <thread>
#include <functional>
#include <chrono>

using namespace vespalib;

bool ipv4_enabled = false;
bool ipv6_enabled = false;

int my_inet() {
    if (ipv6_enabled) {
        return AF_INET6;
    }
    if (ipv4_enabled) {
        return AF_INET;
    }
    TEST_ERROR("tcp/ip support not detected");
    return AF_UNIX;
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
    ASSERT_NOT_EQUAL(fd, -1);
    ASSERT_EQUAL(write(fd, data.data(), data.size()), data.size());
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

vespalib::string read_bytes(Socket &socket, size_t wanted_bytes) {
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

void verify_socket_io(bool is_server, Socket &socket) {
    vespalib::string server_message = "hello, this is the server speaking";
    vespalib::string client_message = "please pick up, I need to talk to you";
    if(is_server) {
        ssize_t written = socket.write(server_message.data(), server_message.size());
        EXPECT_EQUAL(written, server_message.size());
        vespalib::string read = read_bytes(socket, client_message.size());
        EXPECT_EQUAL(client_message, read);
    } else {
        ssize_t written = socket.write(client_message.data(), client_message.size());
        EXPECT_EQUAL(written, client_message.size());
        vespalib::string read = read_bytes(socket, server_message.size());
        EXPECT_EQUAL(server_message, read);
    }
}

Socket::UP connect_sockets(bool is_server, ServerSocket &server_socket) {
    if (is_server) {
        return server_socket.accept();
    } else {
        auto server = server_socket.address();
        auto spec = server.spec();
        auto client = SocketSpec(spec).client_address();
        fprintf(stderr, "connecting to '%s' (server: %s) (client: %s)\n",
                spec.c_str(), get_meta(server).c_str(), get_meta(client).c_str());
        return Socket::connect(SocketSpec(spec));
    }
}

//-----------------------------------------------------------------------------

TEST("my local address") {
    auto list = SocketAddress::resolve(4080);
    fprintf(stderr, "resolve(4080):\n");
    for (const auto &addr: list) {
        EXPECT_TRUE(addr.is_wildcard());
        EXPECT_TRUE(addr.is_ipv4() || addr.is_ipv6());
        ipv4_enabled |= addr.is_ipv4();
        ipv6_enabled |= addr.is_ipv6();
        EXPECT_TRUE(!addr.is_ipc());
        EXPECT_TRUE(!addr.is_abstract());
        EXPECT_EQUAL(addr.port(), 4080);
        fprintf(stderr, "  %s (%s)\n", addr.spec().c_str(), get_meta(addr).c_str());
    }
}

TEST("yahoo.com address") {
    auto list = SocketAddress::resolve(80, "yahoo.com");
    fprintf(stderr, "resolve(80, 'yahoo.com'):\n");
    for (const auto &addr: list) {
        EXPECT_TRUE(!addr.is_wildcard());
        EXPECT_TRUE(addr.is_ipv4() || addr.is_ipv6());
        EXPECT_TRUE(!addr.is_ipc());
        EXPECT_TRUE(!addr.is_abstract());
        EXPECT_EQUAL(addr.port(), 80);
        fprintf(stderr, "  %s (%s)\n", addr.spec().c_str(), get_meta(addr).c_str());
    }
}

TEST("ipc address (path)") {
    auto addr = SocketAddress::from_path("my_socket");
    EXPECT_TRUE(!addr.is_ipv4());
    EXPECT_TRUE(!addr.is_ipv6());
    EXPECT_TRUE(addr.is_ipc());
    EXPECT_TRUE(!addr.is_abstract());
    EXPECT_TRUE(!addr.is_wildcard());
    EXPECT_EQUAL(addr.port(), -1);
    EXPECT_EQUAL(vespalib::string("my_socket"), addr.path());
    EXPECT_TRUE(addr.name().empty());
    fprintf(stderr, "from_path(my_socket)\n");
    fprintf(stderr, "  %s (%s)\n", addr.spec().c_str(), get_meta(addr).c_str());
}

TEST("ipc address (name)") {
    auto addr = SocketAddress::from_name("my_socket");
    EXPECT_TRUE(!addr.is_ipv4());
    EXPECT_TRUE(!addr.is_ipv6());
    EXPECT_TRUE(addr.is_ipc());
    EXPECT_TRUE(addr.is_abstract());
    EXPECT_TRUE(!addr.is_wildcard());
    EXPECT_EQUAL(addr.port(), -1);
    EXPECT_TRUE(addr.path().empty());
    EXPECT_EQUAL(vespalib::string("my_socket"), addr.name());
    fprintf(stderr, "from_path(my_socket)\n");
    fprintf(stderr, "  %s (%s)\n", addr.spec().c_str(), get_meta(addr).c_str());
}

TEST("local client/server addresses") {
    auto spec = SocketSpec("tcp/123");
    auto client = spec.client_address();
    auto server = spec.server_address();
    EXPECT_TRUE(!client.is_wildcard());
    EXPECT_EQUAL(client.port(), 123);
    EXPECT_TRUE(server.is_wildcard());
    EXPECT_EQUAL(server.port(), 123);
    fprintf(stderr, "client(tcp/123): %s (%s)\n", client.spec().c_str(), get_meta(client).c_str());
    fprintf(stderr, "server(tcp/123): %s (%s)\n", server.spec().c_str(), get_meta(server).c_str());
}

struct ServerWrapper {
    ServerSocket::UP server;
    ServerWrapper(const vespalib::string &spec) : server(ServerSocket::listen(SocketSpec(spec))) {}
};

TEST_MT_FF("require that basic socket io works", 2, ServerWrapper("tcp/0"), TimeBomb(60)) {
    bool is_server = (thread_id == 0);
    Socket::UP socket = connect_sockets(is_server, *f1.server);
    TEST_DO(verify_socket_io(is_server, *socket));
}

TEST_MT_FF("require that basic unix domain socket io works (path)", 2,
           ServerWrapper("ipc/file:my_socket"), TimeBomb(60))
{
    bool is_server = (thread_id == 0);
    Socket::UP socket = connect_sockets(is_server, *f1.server);
    TEST_DO(verify_socket_io(is_server, *socket));
}

TEST_MT_FF("require that basic unix domain socket io works (name)", 2,
           ServerWrapper(make_string("ipc/name:my_socket-%d", int(getpid()))), TimeBomb(60))
{
    bool is_server = (thread_id == 0);
    Socket::UP socket = connect_sockets(is_server, *f1.server);
    TEST_DO(verify_socket_io(is_server, *socket));
}

TEST_MT_FF("require that server accept can be interrupted", 2, ServerWrapper("tcp/0"), TimeBomb(60)) {
    bool is_server = (thread_id == 0);
    if (is_server) {
        fprintf(stderr, "--> calling accept\n");
        Socket::UP socket = f1.server->accept();
        fprintf(stderr, "<-- accept returned\n");
        EXPECT_TRUE(!socket->valid());
    } else {
        std::this_thread::sleep_for(std::chrono::milliseconds(500));
        fprintf(stderr, "--- closing server socket\n");
        f1.server->shutdown();
    }
}

TEST("require that socket file is removed by server socket when destructed") {
    remove_file("my_socket");
    ServerSocket::UP server = ServerSocket::listen(SocketSpec("ipc/file:my_socket"));
    EXPECT_TRUE(server->valid());
    EXPECT_TRUE(is_socket("my_socket"));
    server.reset();
    EXPECT_TRUE(!is_socket("my_socket"));
}

TEST("require that socket file is only removed on destruction if it is a socket") {
    remove_file("my_socket");
    ServerSocket::UP server = ServerSocket::listen(SocketSpec("ipc/file:my_socket"));
    EXPECT_TRUE(server->valid());
    EXPECT_TRUE(is_socket("my_socket"));
    replace_file("my_socket", "hello\n");
    server.reset();
    EXPECT_TRUE(is_file("my_socket"));
    remove_file("my_socket");
}

TEST("require that a server socket will fail to listen to a path that is already a regular file") {
    replace_file("my_socket", "hello\n");
    ServerSocket::UP server = ServerSocket::listen(SocketSpec("ipc/file:my_socket"));
    EXPECT_TRUE(!server->valid());
    server.reset();
    EXPECT_TRUE(is_file("my_socket"));
    remove_file("my_socket");
}

TEST("require that a server socket will fail to listen to a path that is already taken by another server") {
    remove_file("my_socket");
    ServerSocket::UP server1 = ServerSocket::listen(SocketSpec("ipc/file:my_socket"));
    ServerSocket::UP server2 = ServerSocket::listen(SocketSpec("ipc/file:my_socket"));
    EXPECT_TRUE(server1->valid());
    EXPECT_TRUE(!server2->valid());
    EXPECT_TRUE(is_socket("my_socket"));
    server1.reset();
    EXPECT_TRUE(!is_socket("my_socket"));
}

TEST("require that a server socket will remove an old socket file if it cannot be connected to") {
    remove_file("my_socket");
    {
        SocketHandle server_handle = SocketAddress::from_path("my_socket").listen();
        EXPECT_TRUE(is_socket("my_socket"));
    }
    EXPECT_TRUE(is_socket("my_socket"));
    ServerSocket::UP server = ServerSocket::listen(SocketSpec("ipc/file:my_socket"));
    EXPECT_TRUE(server->valid());
    server.reset();
    EXPECT_TRUE(!is_socket("my_socket"));
}

TEST("require that two server sockets cannot have the same abstract unix domain socket name") {
    vespalib::string spec = make_string("ipc/name:my_socket-%d", int(getpid()));
    ServerSocket::UP server1 = ServerSocket::listen(SocketSpec(spec));
    ServerSocket::UP server2 = ServerSocket::listen(SocketSpec(spec));
    EXPECT_TRUE(server1->valid());
    EXPECT_TRUE(!server2->valid());
}

TEST("require that abstract socket names are freed when the server socket is destructed") {
    vespalib::string spec = make_string("ipc/name:my_socket-%d", int(getpid()));
    ServerSocket::UP server1 = ServerSocket::listen(SocketSpec(spec));
    EXPECT_TRUE(server1->valid());
    server1.reset();
    ServerSocket::UP server2 = ServerSocket::listen(SocketSpec(spec));
    EXPECT_TRUE(server2->valid());
}

TEST("require that abstract sockets do not have socket files") {
    vespalib::string name = make_string("my_socket-%d", int(getpid()));
    vespalib::string spec = make_string("ipc/name:%s", name.c_str());
    ServerSocket::UP server = ServerSocket::listen(SocketSpec(spec));
    EXPECT_TRUE(server->valid());
    EXPECT_TRUE(!is_socket(name));
    EXPECT_TRUE(!is_file(name));    
}

TEST_MT_FFF("require that abstract and file-based unix domain sockets are not in conflict", 4,
            ServerWrapper(make_string("ipc/file:my_socket-%d", int(getpid()))),
            ServerWrapper(make_string("ipc/name:my_socket-%d", int(getpid()))), TimeBomb(60))
{
    bool is_server = ((thread_id % 2) == 0);
    ServerSocket &server_socket = ((thread_id / 2) == 0) ? *f1.server : *f2.server;
    Socket::UP socket = connect_sockets(is_server, server_socket);
    TEST_DO(verify_socket_io(is_server, *socket));
}

TEST("require that sockets can be set blocking and non-blocking") {
    SocketHandle handle(socket(my_inet(), SOCK_STREAM, 0));
    test::SocketOptionsVerifier verifier(handle.get());
    EXPECT_TRUE(!SocketOptions::set_blocking(-1, true));
    EXPECT_TRUE(SocketOptions::set_blocking(handle.get(), true));
    TEST_DO(verifier.verify_blocking(true));
    EXPECT_TRUE(SocketOptions::set_blocking(handle.get(), false));
    TEST_DO(verifier.verify_blocking(false));
}

TEST("require that tcp nodelay can be enabled and disabled") {
    SocketHandle handle(socket(my_inet(), SOCK_STREAM, 0));
    test::SocketOptionsVerifier verifier(handle.get());
    EXPECT_TRUE(!SocketOptions::set_nodelay(-1, true));
    EXPECT_TRUE(SocketOptions::set_nodelay(handle.get(), true));
    TEST_DO(verifier.verify_nodelay(true));
    EXPECT_TRUE(SocketOptions::set_nodelay(handle.get(), false));
    TEST_DO(verifier.verify_nodelay(false));
}

TEST("require that reuse addr can be set and cleared") {
    SocketHandle handle(socket(my_inet(), SOCK_STREAM, 0));
    test::SocketOptionsVerifier verifier(handle.get());
    EXPECT_TRUE(!SocketOptions::set_reuse_addr(-1, true));
    EXPECT_TRUE(SocketOptions::set_reuse_addr(handle.get(), true));
    TEST_DO(verifier.verify_reuse_addr(true));
    EXPECT_TRUE(SocketOptions::set_reuse_addr(handle.get(), false));
    TEST_DO(verifier.verify_reuse_addr(false));
}

TEST("require that ipv6_only can be set and cleared") {
    if (ipv6_enabled) {
        SocketHandle handle(socket(my_inet(), SOCK_STREAM, 0));
        test::SocketOptionsVerifier verifier(handle.get());
        EXPECT_TRUE(!SocketOptions::set_ipv6_only(-1, true));
        EXPECT_TRUE(SocketOptions::set_ipv6_only(handle.get(), true));
        TEST_DO(verifier.verify_ipv6_only(true));
        EXPECT_TRUE(SocketOptions::set_ipv6_only(handle.get(), false));
        TEST_DO(verifier.verify_ipv6_only(false));
    } else {
        fprintf(stderr, "WARNING: skipping ipv6_only test since ipv6 is disabled");
    }
}

TEST("require that tcp keepalive can be set and cleared") {
    SocketHandle handle(socket(my_inet(), SOCK_STREAM, 0));
    test::SocketOptionsVerifier verifier(handle.get());
    EXPECT_TRUE(!SocketOptions::set_keepalive(-1, true));
    EXPECT_TRUE(SocketOptions::set_keepalive(handle.get(), true));
    TEST_DO(verifier.verify_keepalive(true));
    EXPECT_TRUE(SocketOptions::set_keepalive(handle.get(), false));
    TEST_DO(verifier.verify_keepalive(false));
}

TEST("require that tcp lingering can be adjusted") {
    SocketHandle handle(socket(my_inet(), SOCK_STREAM, 0));
    test::SocketOptionsVerifier verifier(handle.get());
    EXPECT_TRUE(!SocketOptions::set_linger(-1, true, 0));
    EXPECT_TRUE(SocketOptions::set_linger(handle.get(), true, 0));
    TEST_DO(verifier.verify_linger(true, 0));
    EXPECT_TRUE(SocketOptions::set_linger(handle.get(), true, 10));
    TEST_DO(verifier.verify_linger(true, 10));
    EXPECT_TRUE(SocketOptions::set_linger(handle.get(), false, 0));
    TEST_DO(verifier.verify_linger(false, 0));
    EXPECT_TRUE(SocketOptions::set_linger(handle.get(), false, 10));
    TEST_DO(verifier.verify_linger(false, 0));
}

TEST_MAIN() { TEST_RUN_ALL(); }
