// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/nexus.h>
#include <vespa/vespalib/test/time_bomb.h>
#include <vespa/vespalib/net/crypto_engine.h>
#include <vespa/vespalib/net/tls/tls_crypto_engine.h>
#include <vespa/vespalib/net/tls/maybe_tls_crypto_engine.h>
#include <vespa/vespalib/net/sync_crypto_socket.h>
#include <vespa/vespalib/net/selector.h>
#include <vespa/vespalib/net/server_socket.h>
#include <vespa/vespalib/net/socket_handle.h>
#include <vespa/vespalib/net/socket_spec.h>
#include <vespa/vespalib/net/socket_utils.h>
#include <vespa/vespalib/data/smart_buffer.h>
#include <vespa/vespalib/test/make_tls_options_for_testing.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <fcntl.h>
#include <cassert>

using namespace vespalib;
using namespace vespalib::test;
using vespalib::test::Nexus;

struct SocketPair {
    SocketHandle client;
    SocketHandle server;
    SocketPair() : client(), server() {
        int sockets[2];
        socketutils::nonblocking_socketpair(AF_UNIX, SOCK_STREAM, 0, sockets);
        client.reset(sockets[0]);
        server.reset(sockets[1]);
    }
};

//-----------------------------------------------------------------------------

std::string read_bytes(SyncCryptoSocket &socket, size_t wanted_bytes) {
    SmartBuffer read_buffer(wanted_bytes);
    while (read_buffer.obtain().size < wanted_bytes) {
        auto chunk = read_buffer.reserve(wanted_bytes - read_buffer.obtain().size);
        auto res = socket.read(chunk.data, chunk.size);
        assert(res > 0);
        read_buffer.commit(res);
    }
    auto data = read_buffer.obtain();
    return std::string(data.data, wanted_bytes);
}

void read_EOF(SyncCryptoSocket &socket) {
    char buf[16];
    auto res = socket.read(buf, sizeof(buf));
    ASSERT_EQ(res, 0);
}

//-----------------------------------------------------------------------------

void write_bytes(SyncCryptoSocket &socket, const std::string &message) {
    auto res = socket.write(message.data(), message.size());
    ASSERT_EQ(size_t(res), message.size());
}

void write_EOF(SyncCryptoSocket &socket) {
    ASSERT_EQ(socket.half_close(), 0);
}

//-----------------------------------------------------------------------------

void verify_graceful_shutdown(SyncCryptoSocket &socket, bool is_server) {
    if(is_server) {
        GTEST_DO(write_EOF(socket));
        GTEST_DO(read_EOF(socket));
        GTEST_DO(read_EOF(socket));
        GTEST_DO(read_EOF(socket));
    } else {
        GTEST_DO(read_EOF(socket));
        GTEST_DO(read_EOF(socket));
        GTEST_DO(read_EOF(socket));
        GTEST_DO(write_EOF(socket));
    }
}

//-----------------------------------------------------------------------------

void verify_socket_io(SyncCryptoSocket &socket, bool is_server) {
    std::string client_message = "please pick up, I need to talk to you";
    std::string server_message = "hello, this is the server speaking";
    if(is_server) {
        std::string read = read_bytes(socket, client_message.size());
        write_bytes(socket, server_message);
        EXPECT_EQ(client_message, read);
    } else {
        write_bytes(socket, client_message);
        std::string read = read_bytes(socket, server_message.size());
        EXPECT_EQ(server_message, read);
    }
}

//-----------------------------------------------------------------------------

using MyParams = std::tuple<std::string, std::function<std::unique_ptr<CryptoEngine>()>>;
struct CryptoSocketFixture : ::testing::TestWithParam<MyParams> {};

TEST_P(CryptoSocketFixture, verify_sync_crypto_socket) {
    size_t num_threads = 2;
    SocketPair sockets;
    auto [name, factory] = GetParam();
    auto engine = factory();
    TimeBomb time_bomb(60);
    auto task = [&](Nexus &ctx){
                    bool is_server = (ctx.thread_id() == 0);
                    SocketHandle &my_handle = is_server ? sockets.server : sockets.client;
                    my_handle.set_blocking(false);
                    SyncCryptoSocket::UP my_socket = is_server
                        ? SyncCryptoSocket::create_server(*engine, std::move(my_handle))
                        : SyncCryptoSocket::create_client(*engine, std::move(my_handle), make_local_spec());
                    ASSERT_TRUE(my_socket);
                    GTEST_DO(verify_socket_io(*my_socket, is_server));
                    GTEST_DO(verify_graceful_shutdown(*my_socket, is_server));
                };
    Nexus::run(num_threads, task);
}

INSTANTIATE_TEST_SUITE_P(
    CryptoSocketTest,
    CryptoSocketFixture,
    ::testing::Values(
        MyParams{"NullCryptoEngine",
                 [](){ return std::make_unique<NullCryptoEngine>(); }},
        MyParams{"TlsCryptoEngine",
                 [](){ return std::make_unique<TlsCryptoEngine>(make_tls_options_for_testing()); }},
        MyParams{"MaybeTlsCryptoEngine__false",
                 [](){ return std::make_unique<MaybeTlsCryptoEngine>(std::make_shared<TlsCryptoEngine>(make_tls_options_for_testing()), false); }},
        MyParams{"MaybeTlsCryptoEngine__true",
                 [](){ return std::make_unique<MaybeTlsCryptoEngine>(std::make_shared<TlsCryptoEngine>(make_tls_options_for_testing()), true); }}),
        [](const testing::TestParamInfo<MyParams>& my_info) { return std::get<0>(my_info.param); }
);

GTEST_MAIN_RUN_ALL_TESTS()
