// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/websocket/socket.h>
#include <vespa/vespalib/websocket/server_socket.h>
#include <vespa/vespalib/websocket/handler.h>
#include <vespa/vespalib/websocket/acceptor.h>
#include <vespa/vespalib/websocket/key.h>
#include <vespa/vespalib/websocket/buffer.h>
#include <vespa/vespalib/util/sync.h>
#include <thread>
#include <functional>
#include <chrono>

using namespace vespalib::ws;

template <typename T>
struct Receptor : vespalib::ws::Handler<T> {
    std::unique_ptr<T> obj;
    vespalib::Gate gate;
    ~Receptor();
    void handle(std::unique_ptr<T> t) override {
        obj = std::move(t);
        gate.countDown();
    }
};

template <typename T>
Receptor<T>::~Receptor() { }

vespalib::string read_bytes(Socket &socket, size_t wanted_bytes) {
    char tmp[64];
    vespalib::string result;
    while (result.size() < wanted_bytes) {
        size_t read_size = std::min(sizeof(tmp), wanted_bytes - result.size());
        size_t read_result = socket.read(tmp, read_size);
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
        socket.write(server_message.data(), server_message.size());
        vespalib::string read = read_bytes(socket, client_message.size());
        EXPECT_EQUAL(client_message, read);
    } else {
        socket.write(client_message.data(), client_message.size());
        vespalib::string read = read_bytes(socket, server_message.size());
        EXPECT_EQUAL(server_message, read);
    }
}

void verify_socket_io_async(Socket &server, Socket &client) {
    std::thread server_thread(verify_socket_io, true, std::ref(server));
    std::thread client_thread(verify_socket_io, false, std::ref(client));
    server_thread.join();
    client_thread.join();
}

Socket::UP connect_sockets(bool is_server, ServerSocket &server_socket) {
    if (is_server) {
        return server_socket.accept();
    } else {
        return Socket::UP(new Socket("localhost", server_socket.port()));
    }
}

void check_buffer_stats(const Buffer &buffer, size_t dead, size_t used, size_t free) {
    EXPECT_EQUAL(dead, buffer.dead());
    EXPECT_EQUAL(used, buffer.used());
    EXPECT_EQUAL(free, buffer.free());    
}

TEST("require that basic reserve/commit/obtain/evict buffer cycle works") {
    Buffer buffer;
    check_buffer_stats(buffer, 0, 0, 0);
    char *a = buffer.reserve(1);
    check_buffer_stats(buffer, 0, 0, 1);
    *a = 'x';
    buffer.commit(1);
    check_buffer_stats(buffer, 0, 1, 0);
    EXPECT_EQUAL('x', *buffer.obtain());
    check_buffer_stats(buffer, 0, 1, 0);
    buffer.evict(1);
    check_buffer_stats(buffer, 1, 0, 0);
}

TEST("require that buffer moves contained data when more space is needed") {    
    Buffer buffer;
    strncpy(buffer.reserve(3), "xyz", 3);
    buffer.commit(3);
    EXPECT_EQUAL('x', *buffer.obtain());
    buffer.evict(1);
    EXPECT_EQUAL('y', *buffer.obtain());
    check_buffer_stats(buffer, 1, 2, 0);
    buffer.reserve(1);
    check_buffer_stats(buffer, 0, 2, 1);
    EXPECT_EQUAL('y', *buffer.obtain());
    buffer.evict(1);
    EXPECT_EQUAL('z', *buffer.obtain());
    check_buffer_stats(buffer, 1, 1, 1);
    buffer.reserve(3);
    check_buffer_stats(buffer, 0, 1, 3);
    EXPECT_EQUAL('z', *buffer.obtain());
}

TEST_MT_F("require that basic socket io works", 2, ServerSocket(0)) {
    bool is_server = (thread_id == 0);
    Socket::UP socket = connect_sockets(is_server, f1);
    TEST_DO(verify_socket_io(is_server, *socket));
}

TEST_MT_F("require that server accept can be interrupted", 2, ServerSocket(0)) {
    bool is_server = (thread_id == 0);
    if (is_server) {
        fprintf(stderr, "--> calling accept\n");
        Socket::UP socket = f1.accept();
        fprintf(stderr, "<-- accept returned\n");
        EXPECT_TRUE(socket.get() == nullptr);
        EXPECT_TRUE(f1.is_closed());
    } else {
        std::this_thread::sleep_for(std::chrono::milliseconds(500));
        fprintf(stderr, "--- closing server socket\n");
        f1.close();
    }
}

TEST("require that an acceptor can accept connections asynchronously") {
    Receptor<Socket> server;
    Acceptor acceptor(0, server);
    Socket::UP client(new Socket("localhost", acceptor.port()));
    server.gate.await(60000);
    EXPECT_TRUE(server.obj.get() != nullptr);
    EXPECT_TRUE(client.get() != nullptr);
    TEST_DO(verify_socket_io_async(*server.obj, *client));
}

TEST("require that websocket accept tokens are generated correctly") {
    vespalib::string key("dGhlIHNhbXBsZSBub25jZQ==");
    vespalib::string accept_token("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=");
    EXPECT_EQUAL(accept_token, Key::accept(key));
}

TEST_MAIN() { TEST_RUN_ALL(); }
