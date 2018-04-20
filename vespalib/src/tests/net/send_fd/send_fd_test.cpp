// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/net/selector.h>
#include <vespa/vespalib/net/socket_spec.h>
#include <vespa/vespalib/net/server_socket.h>
#include <vespa/vespalib/net/socket_options.h>
#include <vespa/vespalib/net/socket.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/test/socket_options_verifier.h>
#include <thread>
#include <functional>
#include <chrono>
#include <unistd.h>
#include <sys/stat.h>

using namespace vespalib;

vespalib::string read_bytes(SocketHandle &socket, size_t wanted_bytes) {
    char tmp[64];
    vespalib::string result;
    while (result.size() < wanted_bytes) {
        size_t read_size = std::min(sizeof(tmp), wanted_bytes - result.size());
        ssize_t read_result = socket.read(tmp, read_size);
        ASSERT_GREATER(read_result, 0);
        result.append(tmp, read_result);
    }
    return result;
}

void verify_socket_io(bool is_server, SocketHandle &socket) {
    vespalib::string server_message = "hello, this is the server speaking";
    vespalib::string client_message = "please pick up, I need to talk to you";
    if(is_server) {
        ssize_t written = socket.write(server_message.data(), server_message.size());
        EXPECT_EQUAL(written, ssize_t(server_message.size()));
        vespalib::string read = read_bytes(socket, client_message.size());
        EXPECT_EQUAL(client_message, read);
    } else {
        ssize_t written = socket.write(client_message.data(), client_message.size());
        EXPECT_EQUAL(written, ssize_t(client_message.size()));
        vespalib::string read = read_bytes(socket, server_message.size());
        EXPECT_EQUAL(server_message, read);
    }
}

SocketHandle connect(ServerSocket &server_socket) {
    auto server = server_socket.address();
    auto spec = server.spec();
    fprintf(stderr, "connecting to '%s'\n", spec.c_str());
    return SocketSpec(spec).client_address().connect();
}

SocketHandle accept(ServerSocket &server_socket) {
    auto server = server_socket.address();
    auto spec = server.spec();
    fprintf(stderr, "accepting from '%s'\n", spec.c_str());
    return server_socket.accept();
}

void send_fd(SocketHandle &socket, SocketHandle fd) {
    fprintf(stderr, "sending fd: %d\n", fd.get());
    struct msghdr msg = {};
    char tag = '*';
    struct iovec data;
    data.iov_base = &tag;
    data.iov_len = 1;
    char buf[CMSG_SPACE(sizeof(int))];
    msg.msg_iov = &data;
    msg.msg_iovlen = 1;
    msg.msg_control = buf;
    msg.msg_controllen = sizeof(buf);
    struct cmsghdr *hdr = CMSG_FIRSTHDR(&msg);
    hdr->cmsg_level = SOL_SOCKET;
    hdr->cmsg_type = SCM_RIGHTS;
    hdr->cmsg_len = CMSG_LEN(sizeof(int));
    int *fd_dst = (int *) CMSG_DATA(hdr);
    fd_dst[0] = fd.get();
    ssize_t res = sendmsg(socket.get(), &msg, 0);
    ASSERT_EQUAL(res, 1);
}

SocketHandle recv_fd(SocketHandle &socket) {
    struct msghdr msg = {};
    char tag = '*';
    struct iovec data;
    data.iov_base = &tag;
    data.iov_len = 1;
    char buf[CMSG_SPACE(sizeof(int))];
    msg.msg_iov = &data;
    msg.msg_iovlen = 1;
    msg.msg_control = buf;
    msg.msg_controllen = sizeof(buf);
    ssize_t res = recvmsg(socket.get(), &msg, 0);
    ASSERT_EQUAL(res, 1);
    struct cmsghdr *hdr = CMSG_FIRSTHDR(&msg);
    bool type_ok = ((hdr->cmsg_level == SOL_SOCKET) &&
                    (hdr->cmsg_type == SCM_RIGHTS));
    ASSERT_TRUE(type_ok);
    int *fd_src = (int *) CMSG_DATA(hdr);
    fprintf(stderr, "got fd: %d\n", fd_src[0]);
    return SocketHandle(fd_src[0]);
}

//-----------------------------------------------------------------------------

TEST_MT_FFF("require that an open socket (handle) can be passed over a unix domain socket", 3,
            ServerSocket("tcp/0"), ServerSocket("ipc/file:my_socket"), TimeBomb(60))
{
    if (thread_id == 0) {        // server
        SocketHandle socket = accept(f1);
        TEST_DO(verify_socket_io(true, socket));  // server side
        TEST_BARRIER();
    } else if (thread_id == 1) { // proxy
        SocketHandle server_socket = connect(f1);
        SocketHandle client_socket = accept(f2);
        send_fd(client_socket, std::move(server_socket));
        TEST_BARRIER();
    } else {                     // client
        SocketHandle proxy_socket = connect(f2);
        SocketHandle socket = recv_fd(proxy_socket);
        TEST_DO(verify_socket_io(false, socket)); // client side
        TEST_BARRIER();
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
