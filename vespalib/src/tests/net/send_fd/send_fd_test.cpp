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
#include <chrono>
#include <functional>
#include <latch>
#include <optional>
#include <thread>
#include <unistd.h>
#include <sys/stat.h>

using namespace vespalib;
using vespalib::test::Nexus;

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
    memset(buf, 0, sizeof(buf));
    msg.msg_iov = &data;
    msg.msg_iovlen = 1;
    msg.msg_control = buf;
    msg.msg_controllen = sizeof(buf);
    struct cmsghdr *hdr = CMSG_FIRSTHDR(&msg);
    hdr->cmsg_level = SOL_SOCKET;
    hdr->cmsg_type = SCM_RIGHTS;
    hdr->cmsg_len = CMSG_LEN(sizeof(int));
    int *fd_dst = (int *) (void *) CMSG_DATA(hdr);
    fd_dst[0] = fd.get();
    ssize_t res = sendmsg(socket.get(), &msg, 0);
    ASSERT_EQ(res, 1);
}

void recv_fd(SocketHandle &socket, std::optional<SocketHandle>& result) {
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
    ASSERT_EQ(res, 1);
    struct cmsghdr *hdr = CMSG_FIRSTHDR(&msg);
    bool type_ok = ((hdr->cmsg_level == SOL_SOCKET) &&
                    (hdr->cmsg_type == SCM_RIGHTS));
    ASSERT_TRUE(type_ok);
    int *fd_src = (int *) (void *) CMSG_DATA(hdr);
    fprintf(stderr, "got fd: %d\n", fd_src[0]);
    result = SocketHandle(fd_src[0]);
}

//-----------------------------------------------------------------------------

namespace {

class WaitLatch {
    std::latch& _latch;
public:
    explicit WaitLatch(std::latch& latch) noexcept
        : _latch(latch)
    {
    }
    ~WaitLatch() { _latch.arrive_and_wait(); }
};

}

TEST(SendFdTest, require_that_an_open_socket_handle_can_be_passed_over_a_unix_domain_socket)
{
    constexpr size_t num_threads = 3;
    ServerSocket f1("tcp/0");
    ServerSocket f2("ipc/file:my_socket");
    std::latch latch(num_threads);
    TimeBomb f3(60);
    auto task = [&f1,&f2,&latch](Nexus& ctx) {
                    auto thread_id = ctx.thread_id();
                    if (thread_id == 0) {        // server
                        SocketHandle socket = accept(f1);
                        WaitLatch wait(latch);
                        SCOPED_TRACE("verify socket io server side");
                        verify_socket_io(true, socket);  // server side
                    } else if (thread_id == 1) { // proxy
                        SocketHandle server_socket = connect(f1);
                        SocketHandle client_socket = accept(f2);
                        WaitLatch wait(latch);
                        ASSERT_NO_FATAL_FAILURE(send_fd(client_socket, std::move(server_socket)));
                    } else {                     // client
                        SocketHandle proxy_socket = connect(f2);
                        std::optional<SocketHandle> socket;
                        WaitLatch wait(latch);
                        ASSERT_NO_FATAL_FAILURE(recv_fd(proxy_socket, socket));
                        ASSERT_TRUE(socket.has_value());
                        SCOPED_TRACE("verify socket io client side");
                        verify_socket_io(false, socket.value()); // client side
                    }
                };
    Nexus::run(num_threads, task);
}

GTEST_MAIN_RUN_ALL_TESTS()
