// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/time_bomb.h>
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

using namespace vespalib;
using namespace vespalib::test;

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

vespalib::string read_bytes(SyncCryptoSocket &socket, size_t wanted_bytes) {
    SmartBuffer read_buffer(wanted_bytes);
    while (read_buffer.obtain().size < wanted_bytes) {
        auto chunk = read_buffer.reserve(wanted_bytes - read_buffer.obtain().size);
        auto res = socket.read(chunk.data, chunk.size);
        ASSERT_TRUE(res > 0);
        read_buffer.commit(res);
    }
    auto data = read_buffer.obtain();
    return vespalib::string(data.data, wanted_bytes);
}

void read_EOF(SyncCryptoSocket &socket) {
    char buf[16];
    auto res = socket.read(buf, sizeof(buf));
    ASSERT_EQUAL(res, 0);
}

//-----------------------------------------------------------------------------

void write_bytes(SyncCryptoSocket &socket, const vespalib::string &message) {
    auto res = socket.write(message.data(), message.size());
    ASSERT_EQUAL(size_t(res), message.size());
}

void write_EOF(SyncCryptoSocket &socket) {
    ASSERT_EQUAL(socket.half_close(), 0);
}

//-----------------------------------------------------------------------------

void verify_graceful_shutdown(SyncCryptoSocket &socket, bool is_server) {
    if(is_server) {
        TEST_DO(write_EOF(socket));
        TEST_DO(read_EOF(socket));
        TEST_DO(read_EOF(socket));
        TEST_DO(read_EOF(socket));
    } else {
        TEST_DO(read_EOF(socket));
        TEST_DO(read_EOF(socket));
        TEST_DO(read_EOF(socket));
        TEST_DO(write_EOF(socket));
    }
}

//-----------------------------------------------------------------------------

void verify_socket_io(SyncCryptoSocket &socket, bool is_server) {
    vespalib::string client_message = "please pick up, I need to talk to you";
    vespalib::string server_message = "hello, this is the server speaking";
    if(is_server) {
        vespalib::string read = read_bytes(socket, client_message.size());
        write_bytes(socket, server_message);
        EXPECT_EQUAL(client_message, read);
    } else {
        write_bytes(socket, client_message);
        vespalib::string read = read_bytes(socket, server_message.size());
        EXPECT_EQUAL(server_message, read);
    }
}

//-----------------------------------------------------------------------------

void verify_crypto_socket(SocketPair &sockets, CryptoEngine &engine, bool is_server) {
    SocketHandle &my_handle = is_server ? sockets.server : sockets.client;
    my_handle.set_blocking(false);
    SyncCryptoSocket::UP my_socket = is_server
                                     ? SyncCryptoSocket::create_server(engine, std::move(my_handle))
                                     : SyncCryptoSocket::create_client(engine, std::move(my_handle), make_local_spec());
    ASSERT_TRUE(my_socket);
    TEST_DO(verify_socket_io(*my_socket, is_server));
    TEST_DO(verify_graceful_shutdown(*my_socket, is_server));
}

//-----------------------------------------------------------------------------

TEST_MT_FFF("require that encrypted sync socket io works with NullCryptoEngine",
            2, SocketPair(), NullCryptoEngine(), TimeBomb(60))
{
    TEST_DO(verify_crypto_socket(f1, f2, (thread_id == 0)));
}

TEST_MT_FFF("require that encrypted sync socket io works with XorCryptoEngine",
            2, SocketPair(), XorCryptoEngine(), TimeBomb(60))
{
    TEST_DO(verify_crypto_socket(f1, f2, (thread_id == 0)));
}

TEST_MT_FFF("require that encrypted sync socket io works with TlsCryptoEngine",
            2, SocketPair(), TlsCryptoEngine(make_tls_options_for_testing()), TimeBomb(60))
{
    TEST_DO(verify_crypto_socket(f1, f2, (thread_id == 0)));
}

TEST_MT_FFF("require that encrypted sync socket io works with MaybeTlsCryptoEngine(true)",
            2, SocketPair(), MaybeTlsCryptoEngine(std::make_shared<TlsCryptoEngine>(make_tls_options_for_testing()), true), TimeBomb(60))
{
    TEST_DO(verify_crypto_socket(f1, f2, (thread_id == 0)));
}

TEST_MT_FFF("require that encrypted sync socket io works with MaybeTlsCryptoEngine(false)",
            2, SocketPair(), MaybeTlsCryptoEngine(std::make_shared<TlsCryptoEngine>(make_tls_options_for_testing()), false), TimeBomb(60))
{
    TEST_DO(verify_crypto_socket(f1, f2, (thread_id == 0)));
}

TEST_MAIN() { TEST_RUN_ALL(); }
