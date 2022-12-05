// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/coro/lazy.h>
#include <vespa/vespalib/coro/completion.h>
#include <vespa/vespalib/coro/async_io.h>
#include <vespa/vespalib/net/socket_spec.h>
#include <vespa/vespalib/net/server_socket.h>
#include <vespa/vespalib/net/socket_handle.h>
#include <vespa/vespalib/net/socket_address.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::coro;

Work run_loop(AsyncIo &async, int a, int b) {
    for (int i = a; i < b; ++i) {
        co_await async.schedule();
        fprintf(stderr, "run_loop [%d,%d> -> current value: %d\n", a, b, i);
    }
    co_return Done{};
}

TEST(AsyncIoTest, create_async_io) {
    auto async = AsyncIo::create();
    ASSERT_TRUE(async);
    fprintf(stderr, "async_io impl: %s\n", async->get_impl_spec().c_str());
}

TEST(AsyncIoTest, run_stuff_in_async_io_context) {
    auto async = AsyncIo::create();
    auto f1 = make_future(run_loop(*async, 10, 20));
    auto f2 = make_future(run_loop(*async, 20, 30));
    auto f3 = make_future(run_loop(*async, 30, 40));
    f1.wait();
    f2.wait();
    f3.wait();
}

Lazy<size_t> write_msg(AsyncIo &async, SocketHandle &socket, const vespalib::string &msg) {
    size_t written = 0;
    while (written < msg.size()) {
        size_t write_size = (msg.size() - written);
        ssize_t write_result = co_await async.write(socket, msg.data() + written, write_size);
        if (write_result <= 0) {
            co_return written;
        }
        written += write_result;
    }
    co_return written;
}

Lazy<vespalib::string> read_msg(AsyncIo &async, SocketHandle &socket, size_t wanted_bytes) {
    char tmp[64];
    vespalib::string result;
    while (result.size() < wanted_bytes) {
        size_t read_size = std::min(sizeof(tmp), wanted_bytes - result.size());
        ssize_t read_result = co_await async.read(socket, tmp, read_size);
        if (read_result <= 0) {
            co_return result;
        }
        result.append(tmp, read_result);
    }
    co_return result;
}

Work verify_socket_io(AsyncIo &async, SocketHandle &socket, bool is_server) {
    vespalib::string server_message = "hello, this is the server speaking";
    vespalib::string client_message = "please pick up, I need to talk to you";
    if (is_server) {
        vespalib::string read = co_await read_msg(async, socket, client_message.size());
        EXPECT_EQ(client_message, read);
        size_t written = co_await write_msg(async, socket, server_message);
        EXPECT_EQ(written, ssize_t(server_message.size()));
    } else {
        size_t written = co_await write_msg(async, socket, client_message);
        EXPECT_EQ(written, ssize_t(client_message.size()));
        vespalib::string read = co_await read_msg(async, socket, server_message.size());
        EXPECT_EQ(server_message, read);
    }
    co_return Done{};
}

Work async_server(AsyncIo &async, ServerSocket &server_socket) {
    auto server_addr = server_socket.address();
    auto server_spec = server_addr.spec();
    fprintf(stderr, "listening at '%s' (fd = %d)\n", server_spec.c_str(), server_socket.get_fd());
    auto socket = co_await async.accept(server_socket);
    fprintf(stderr, "server fd: %d\n", socket.get());
    co_return co_await verify_socket_io(async, socket, true);
}

Work async_client(AsyncIo &async, ServerSocket &server_socket) {
    auto server_addr = server_socket.address();
    auto server_spec = server_addr.spec();
    fprintf(stderr, "connecting to '%s'\n", server_spec.c_str());
    auto client_addr = SocketSpec(server_spec).client_address();
    auto socket = co_await async.connect(client_addr);
    fprintf(stderr, "client fd: %d\n", socket.get());
    co_return co_await verify_socket_io(async, socket, false);
}

TEST(AsyncIoTest, raw_socket_io) {
    ServerSocket server_socket("tcp/0");
    server_socket.set_blocking(false);
    auto async = AsyncIo::create();
    auto f1 = make_future(async_server(*async, server_socket));
    auto f2 = make_future(async_client(*async, server_socket));
    f1.wait();
    f2.wait();
}

GTEST_MAIN_RUN_ALL_TESTS()
