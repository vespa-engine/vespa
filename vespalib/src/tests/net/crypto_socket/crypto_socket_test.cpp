// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/time_bomb.h>
#include <vespa/vespalib/net/crypto_engine.h>
#include <vespa/vespalib/net/tls/tls_crypto_engine.h>
#include <vespa/vespalib/net/tls/maybe_tls_crypto_engine.h>
#include <vespa/vespalib/net/crypto_socket.h>
#include <vespa/vespalib/net/selector.h>
#include <vespa/vespalib/net/server_socket.h>
#include <vespa/vespalib/net/socket_handle.h>
#include <vespa/vespalib/net/socket_spec.h>
#include <vespa/vespalib/net/socket_utils.h>
#include <vespa/vespalib/data/smart_buffer.h>
#include <vespa/vespalib/test/make_tls_options_for_testing.h>
#include <vespa/vespalib/util/size_literals.h>
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

bool is_blocked(int res) {
    return ((res == -1) && ((errno == EWOULDBLOCK) || (errno == EAGAIN)));
}

void read(CryptoSocket &socket, SmartBuffer &buffer) {
    size_t chunk_size = std::max(size_t(4_Ki), socket.min_read_buffer_size());
    auto chunk = buffer.reserve(chunk_size);
    int res = socket.read(chunk.data, chunk.size);
    if (res > 0) {
        buffer.commit(res);
    } else {
        ASSERT_TRUE(is_blocked(res));
    }
}

void drain(CryptoSocket &socket, SmartBuffer &buffer) {
    int res;
    size_t chunk_size = std::max(size_t(4_Ki), socket.min_read_buffer_size());
    do {
        auto chunk = buffer.reserve(chunk_size);
        res = socket.drain(chunk.data, chunk.size);
        if (res > 0) {
            buffer.commit(res);
        }
    } while (res > 0);
    ASSERT_EQUAL(res, 0);
}

void write(CryptoSocket &socket, SmartBuffer &buffer) {
    auto chunk = buffer.obtain();
    auto res = socket.write(chunk.data, chunk.size);
    if (res > 0) {
        buffer.evict(res);
    } else {
        ASSERT_TRUE(is_blocked(res));
    }
}

void flush(CryptoSocket &socket) {
    int res = 1;
    while (res > 0) {
        res = socket.flush();
    }
    ASSERT_TRUE((res == 0) || is_blocked(res));
}

void half_close(CryptoSocket &socket) {
    auto res = socket.half_close();
    ASSERT_TRUE((res == 0) || is_blocked(res));
}

//-----------------------------------------------------------------------------

vespalib::string read_bytes(CryptoSocket &socket, SmartBuffer &read_buffer, size_t wanted_bytes) {
    SingleFdSelector selector(socket.get_fd());
    while (read_buffer.obtain().size < wanted_bytes) {
        ASSERT_TRUE(selector.wait_readable());
        read(socket, read_buffer);
        drain(socket, read_buffer);
    }
    auto data = read_buffer.obtain();
    vespalib::string message(data.data, wanted_bytes);
    read_buffer.evict(message.size());
    return message;
}

//-----------------------------------------------------------------------------

void read_EOF(CryptoSocket &socket, SmartBuffer &read_buffer) {
    ASSERT_EQUAL(read_buffer.obtain().size, 0u);
    SingleFdSelector selector(socket.get_fd());
    ASSERT_TRUE(selector.wait_readable());
    size_t chunk_size = std::max(size_t(4_Ki), socket.min_read_buffer_size());
    auto chunk = read_buffer.reserve(chunk_size);
    auto res = socket.read(chunk.data, chunk.size);
    while (is_blocked(res)) {
        ASSERT_TRUE(selector.wait_readable());
        res = socket.read(chunk.data, chunk.size);
    }
    ASSERT_EQUAL(res, 0);
}

//-----------------------------------------------------------------------------

void write_bytes(CryptoSocket &socket, const vespalib::string &message) {
    SmartBuffer write_buffer(message.size());
    SingleFdSelector selector(socket.get_fd());
    auto data = write_buffer.reserve(message.size());
    memcpy(data.data, message.data(), message.size());
    write_buffer.commit(message.size());
    while (write_buffer.obtain().size > 0) {
        ASSERT_TRUE(selector.wait_writable());
        write(socket, write_buffer);
        flush(socket);
    }
}

//-----------------------------------------------------------------------------

void write_EOF(CryptoSocket &socket) {
    SingleFdSelector selector(socket.get_fd());
    ASSERT_TRUE(selector.wait_writable());
    auto res = socket.half_close();
    while (is_blocked(res)) {
        ASSERT_TRUE(selector.wait_writable());
        res = socket.half_close();
    }
    ASSERT_EQUAL(res, 0);
}

//-----------------------------------------------------------------------------

void verify_graceful_shutdown(CryptoSocket &socket, SmartBuffer &read_buffer, bool is_server) {
    if(is_server) {
        TEST_DO(write_EOF(socket));
        TEST_DO(read_EOF(socket, read_buffer));
        TEST_DO(read_EOF(socket, read_buffer));
        TEST_DO(read_EOF(socket, read_buffer));
    } else {
        TEST_DO(read_EOF(socket, read_buffer));
        TEST_DO(read_EOF(socket, read_buffer));
        TEST_DO(read_EOF(socket, read_buffer));
        TEST_DO(write_EOF(socket));
    }
}

//-----------------------------------------------------------------------------

void verify_socket_io(CryptoSocket &socket, SmartBuffer &read_buffer, bool is_server) {
    vespalib::string client_message = "please pick up, I need to talk to you";
    vespalib::string server_message = "hello, this is the server speaking";
    if(is_server) {
        vespalib::string read = read_bytes(socket, read_buffer, client_message.size());
        write_bytes(socket, server_message);
        EXPECT_EQUAL(client_message, read);
    } else {
        write_bytes(socket, client_message);
        vespalib::string read = read_bytes(socket, read_buffer, server_message.size());
        EXPECT_EQUAL(server_message, read);
    }
}

//-----------------------------------------------------------------------------

void verify_handshake(CryptoSocket &socket) {
    bool done = false;
    SingleFdSelector selector(socket.get_fd());
    while (!done) {
        auto res = socket.handshake();
        ASSERT_TRUE(res != CryptoSocket::HandshakeResult::FAIL);
        switch (res) {
        case CryptoSocket::HandshakeResult::FAIL:
        case CryptoSocket::HandshakeResult::DONE:
            done = true;
            break;
        case CryptoSocket::HandshakeResult::NEED_READ:
            ASSERT_TRUE(selector.wait_readable());
            break;
        case CryptoSocket::HandshakeResult::NEED_WRITE:
            ASSERT_TRUE(selector.wait_writable());
            break;
        case CryptoSocket::HandshakeResult::NEED_WORK:
            socket.do_handshake_work();
        }
    }
}

//-----------------------------------------------------------------------------

void verify_crypto_socket(SocketPair &sockets, CryptoEngine &engine, bool is_server) {
    SocketHandle &my_handle = is_server ? sockets.server : sockets.client;
    my_handle.set_blocking(false);
    SmartBuffer read_buffer(4_Ki);
    CryptoSocket::UP my_socket = is_server
                                 ? engine.create_server_crypto_socket(std::move(my_handle))
                                 : engine.create_client_crypto_socket(std::move(my_handle), make_local_spec());
    TEST_DO(verify_handshake(*my_socket));
    drain(*my_socket, read_buffer);
    TEST_DO(verify_socket_io(*my_socket, read_buffer, is_server));
    TEST_DO(verify_graceful_shutdown(*my_socket, read_buffer, is_server));
}

//-----------------------------------------------------------------------------

TEST_MT_FFF("require that encrypted async socket io works with NullCryptoEngine",
            2, SocketPair(), NullCryptoEngine(), TimeBomb(60))
{
    TEST_DO(verify_crypto_socket(f1, f2, (thread_id == 0)));
}

TEST_MT_FFF("require that encrypted async socket io works with XorCryptoEngine",
            2, SocketPair(), XorCryptoEngine(), TimeBomb(60))
{
    TEST_DO(verify_crypto_socket(f1, f2, (thread_id == 0)));
}

TEST_MT_FFF("require that encrypted async socket io works with TlsCryptoEngine",
            2, SocketPair(), TlsCryptoEngine(make_tls_options_for_testing()), TimeBomb(60))
{
    TEST_DO(verify_crypto_socket(f1, f2, (thread_id == 0)));
}

TEST_MT_FFF("require that encrypted async socket io works with MaybeTlsCryptoEngine(true)",
            2, SocketPair(), MaybeTlsCryptoEngine(std::make_shared<TlsCryptoEngine>(make_tls_options_for_testing()), true), TimeBomb(60))
{
    TEST_DO(verify_crypto_socket(f1, f2, (thread_id == 0)));
}

TEST_MT_FFF("require that encrypted async socket io works with MaybeTlsCryptoEngine(false)",
            2, SocketPair(), MaybeTlsCryptoEngine(std::make_shared<TlsCryptoEngine>(make_tls_options_for_testing()), false), TimeBomb(60))
{
    TEST_DO(verify_crypto_socket(f1, f2, (thread_id == 0)));
}

TEST_MAIN() { TEST_RUN_ALL(); }
