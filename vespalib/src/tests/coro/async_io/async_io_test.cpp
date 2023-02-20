// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/coro/lazy.h>
#include <vespa/vespalib/coro/detached.h>
#include <vespa/vespalib/coro/completion.h>
#include <vespa/vespalib/coro/async_io.h>
#include <vespa/vespalib/coro/async_crypto_socket.h>
#include <vespa/vespalib/net/socket_spec.h>
#include <vespa/vespalib/net/server_socket.h>
#include <vespa/vespalib/net/socket_handle.h>
#include <vespa/vespalib/net/socket_address.h>
#include <vespa/vespalib/net/crypto_engine.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/util/classname.h>
#include <vespa/vespalib/test/make_tls_options_for_testing.h>
#include <vespa/vespalib/net/tls/tls_crypto_engine.h>
#include <vespa/vespalib/net/tls/maybe_tls_crypto_engine.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::coro;
using namespace vespalib::test;

vespalib::string impl_spec(AsyncIo &async) {
    switch (async.get_impl_tag()) {
    case AsyncIo::ImplTag::EPOLL: return "epoll";
    case AsyncIo::ImplTag::URING: return "uring";
    }
    abort();
}

Detached self_exiting_run_loop(AsyncIo::SP async) {
    bool ok = co_await async->schedule();
    for (size_t i = 0; ok; ++i) {
        fprintf(stderr, "self_exiting_run_loop -> current value: %zu\n", i);
        ok = co_await async->schedule();
    }
    fprintf(stderr, "self_exiting_run_loop -> exiting\n");
}

Work run_loop(AsyncIo &async, int a, int b) {
    for (int i = a; i < b; ++i) {
        co_await async.schedule();
        fprintf(stderr, "run_loop [%d,%d> -> current value: %d\n", a, b, i);
    }
    co_return Done{};
}

TEST(AsyncIoTest, create_async_io) {
    auto async = AsyncIo::create();
    AsyncIo &api = async;
    fprintf(stderr, "async_io impl: %s\n", impl_spec(api).c_str());
}

TEST(AsyncIoTest, run_stuff_in_async_io_context) {
    auto async = AsyncIo::create();
    auto f1 = make_future(run_loop(async, 10, 20));
    auto f2 = make_future(run_loop(async, 20, 30));
    auto f3 = make_future(run_loop(async, 30, 40));
    f1.wait();
    f2.wait();
    f3.wait();
}

TEST(AsyncIoTest, shutdown_with_self_exiting_coroutine) {
    auto async = AsyncIo::create();
    auto f1 = make_future(run_loop(async, 10, 20));
    auto f2 = make_future(run_loop(async, 20, 30));
    self_exiting_run_loop(async.share());
    f1.wait();
    f2.wait();
}

Lazy<size_t> write_msg(AsyncCryptoSocket &socket, const vespalib::string &msg) {
    size_t written = 0;
    while (written < msg.size()) {
        size_t write_size = (msg.size() - written);
        ssize_t write_result = co_await socket.write(msg.data() + written, write_size);
        if (write_result <= 0) {
            co_return written;
        }
        written += write_result;
    }
    co_return written;
}

Lazy<vespalib::string> read_msg(AsyncCryptoSocket &socket, size_t wanted_bytes) {
    char tmp[64];
    vespalib::string result;
    while (result.size() < wanted_bytes) {
        size_t read_size = std::min(sizeof(tmp), wanted_bytes - result.size());
        ssize_t read_result = co_await socket.read(tmp, read_size);
        if (read_result <= 0) {
            co_return result;
        }
        result.append(tmp, read_result);
    }
    co_return result;
}

Work verify_socket_io(AsyncCryptoSocket &socket, bool is_server) {
    vespalib::string server_message = "hello, this is the server speaking";
    vespalib::string client_message = "please pick up, I need to talk to you";
    if (is_server) {
        vespalib::string read = co_await read_msg(socket, client_message.size());
        EXPECT_EQ(client_message, read);
        size_t written = co_await write_msg(socket, server_message);
        EXPECT_EQ(written, ssize_t(server_message.size()));
    } else {
        size_t written = co_await write_msg(socket, client_message);
        EXPECT_EQ(written, ssize_t(client_message.size()));
        vespalib::string read = co_await read_msg(socket, server_message.size());
        EXPECT_EQ(server_message, read);
    }
    co_return Done{};
}

Work async_server(AsyncIo &async, CryptoEngine &engine, ServerSocket &server_socket) {
    auto server_addr = server_socket.address();
    auto server_spec = server_addr.spec();
    fprintf(stderr, "listening at '%s' (fd = %d)\n", server_spec.c_str(), server_socket.get_fd());
    auto raw_socket = co_await async.accept(server_socket);
    fprintf(stderr, "server fd: %d\n", raw_socket.get());
    auto socket = co_await AsyncCryptoSocket::accept(async, engine, std::move(raw_socket));
    EXPECT_TRUE(socket);
    REQUIRE(socket);
    fprintf(stderr, "server socket type: %s\n", getClassName(*socket).c_str());
    co_return co_await verify_socket_io(*socket, true);
}

Work async_client(AsyncIo &async, CryptoEngine &engine, ServerSocket &server_socket) {
    auto server_addr = server_socket.address();
    auto server_spec = SocketSpec(server_addr.spec());
    fprintf(stderr, "connecting to '%s'\n", server_spec.spec().c_str());
    auto client_addr = server_spec.client_address();
    auto raw_socket = co_await async.connect(client_addr);
    fprintf(stderr, "client fd: %d\n", raw_socket.get());
    auto socket = co_await AsyncCryptoSocket::connect(async, engine, std::move(raw_socket), server_spec);
    EXPECT_TRUE(socket);
    REQUIRE(socket);
    fprintf(stderr, "client socket type: %s\n", getClassName(*socket).c_str());
    co_return co_await verify_socket_io(*socket, false);
}

void verify_socket_io(CryptoEngine &engine, AsyncIo::ImplTag prefer_impl = AsyncIo::ImplTag::EPOLL) {
    ServerSocket server_socket("tcp/0");
    server_socket.set_blocking(false);
    auto async = AsyncIo::create(prefer_impl);
    fprintf(stderr, "verify_socket_io: crypto engine: %s, async impl: %s\n",
            getClassName(engine).c_str(), impl_spec(async).c_str());
    auto f1 = make_future(async_server(async, engine, server_socket));
    auto f2 = make_future(async_client(async, engine, server_socket));
    (void) f1.get();
    (void) f2.get();
}

TEST(AsyncIoTest, raw_socket_io) {
    NullCryptoEngine engine;
    verify_socket_io(engine);
}

TEST(AsyncIoTest, tls_socket_io) {
    TlsCryptoEngine engine(make_tls_options_for_testing());
    verify_socket_io(engine);
}

TEST(AsyncIoTest, maybe_tls_true_socket_io) {
    MaybeTlsCryptoEngine engine(std::make_shared<TlsCryptoEngine>(make_tls_options_for_testing()), true);
    verify_socket_io(engine);
}

TEST(AsyncIoTest, maybe_tls_false_socket_io) {
    MaybeTlsCryptoEngine engine(std::make_shared<TlsCryptoEngine>(make_tls_options_for_testing()), false);
    verify_socket_io(engine);
}

TEST(AsyncIoTest, raw_socket_io_with_io_uring_maybe) {
    NullCryptoEngine engine;
    verify_socket_io(engine, AsyncIo::ImplTag::URING);
}

TEST(AsyncIoTest, tls_socket_io_with_io_uring_maybe) {
    TlsCryptoEngine engine(make_tls_options_for_testing());
    verify_socket_io(engine, AsyncIo::ImplTag::URING);
}

GTEST_MAIN_RUN_ALL_TESTS()
