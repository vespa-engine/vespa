// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/connection.h>
#include <vespa/fnet/controlpacket.h>
#include <vespa/fnet/ipackethandler.h>
#include <vespa/fnet/simplepacketstreamer.h>
#include <vespa/fnet/transport.h>
#include <vespa/fnet/transport_thread.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/net/crypto_engine.h>
#include <vespa/vespalib/net/server_socket.h>
#include <vespa/vespalib/test/nexus.h>
#include <vespa/vespalib/test/time_bomb.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <cassert>

using namespace vespalib;
using vespalib::test::Nexus;

constexpr vespalib::duration short_time = 20ms;

struct BlockingHostResolver : public AsyncResolver::HostResolver {
    AsyncResolver::SimpleHostResolver resolver;
    Gate                              caller;
    Gate                              barrier;
    BlockingHostResolver() noexcept : resolver(), caller(), barrier() {}
    ~BlockingHostResolver() override;
    std::string ip_address(const std::string& host) override {
        fprintf(stderr, "blocking resolve request: '%s'\n", host.c_str());
        caller.countDown();
        barrier.await();
        std::string result = resolver.ip_address(host);
        fprintf(stderr, "returning resolve result: '%s'\n", result.c_str());
        return result;
    }
    void wait_for_caller() { caller.await(); }
    void release_caller() { barrier.countDown(); }
};

BlockingHostResolver::~BlockingHostResolver() = default;

AsyncResolver::SP make_resolver(AsyncResolver::HostResolver::SP host_resolver) {
    AsyncResolver::Params params;
    params.resolver = host_resolver;
    return AsyncResolver::create(params);
}

//-----------------------------------------------------------------------------

struct BlockingCryptoSocket : public CryptoSocket {
    SocketHandle socket;
    Gate&        handshake_work_enter;
    Gate&        handshake_work_exit;
    Gate&        handshake_socket_deleted;
    BlockingCryptoSocket(SocketHandle s, Gate& hs_work_enter, Gate& hs_work_exit, Gate& hs_socket_deleted)
        : socket(std::move(s)),
          handshake_work_enter(hs_work_enter),
          handshake_work_exit(hs_work_exit),
          handshake_socket_deleted(hs_socket_deleted) {}
    ~BlockingCryptoSocket() override { handshake_socket_deleted.countDown(); }
    int             get_fd() const override { return socket.get(); }
    HandshakeResult handshake() override { return HandshakeResult::NEED_WORK; }
    void            do_handshake_work() override {
        handshake_work_enter.countDown();
        handshake_work_exit.await();
    }
    size_t  min_read_buffer_size() const override { return 1; }
    ssize_t read(char* buf, size_t len) override { return socket.read(buf, len); }
    ssize_t drain(char*, size_t) override { return 0; }
    ssize_t write(const char* buf, size_t len) override { return socket.write(buf, len); }
    ssize_t flush() override { return 0; }
    ssize_t half_close() override { return socket.half_close(); }
    void    drop_empty_buffers() override {}
};

struct BlockingCryptoEngine : public CryptoEngine {
    Gate handshake_work_enter;
    Gate handshake_work_exit;
    Gate handshake_socket_deleted;
    ~BlockingCryptoEngine() override;
    bool             use_tls_when_client() const override { return false; }
    bool             always_use_tls_when_server() const override { return false; }
    CryptoSocket::UP create_client_crypto_socket(SocketHandle socket, const SocketSpec&) override {
        return std::make_unique<BlockingCryptoSocket>(
            std::move(socket), handshake_work_enter, handshake_work_exit, handshake_socket_deleted);
    }
    CryptoSocket::UP create_server_crypto_socket(SocketHandle socket) override {
        return std::make_unique<BlockingCryptoSocket>(
            std::move(socket), handshake_work_enter, handshake_work_exit, handshake_socket_deleted);
    }
};

BlockingCryptoEngine::~BlockingCryptoEngine() = default;

//-----------------------------------------------------------------------------

struct TransportFixture : FNET_IPacketHandler {
    FNET_SimplePacketStreamer streamer;
    FNET_Transport            transport;
    Gate                      conn_lost;
    TransportFixture() : streamer(nullptr), transport(), conn_lost() { transport.Start(); }
    TransportFixture(AsyncResolver::HostResolver::SP host_resolver)
        : streamer(nullptr),
          transport(fnet::TransportConfig().resolver(make_resolver(std::move(host_resolver)))),
          conn_lost() {
        transport.Start();
    }
    TransportFixture(CryptoEngine::SP crypto)
        : streamer(nullptr), transport(fnet::TransportConfig().crypto(std::move(crypto))), conn_lost() {
        transport.Start();
    }
    HP_RetCode HandlePacket(FNET_Packet* packet, FNET_Context) override {
        EXPECT_TRUE(packet->GetCommand() == FNET_ControlPacket::FNET_CMD_CHANNEL_LOST);
        assert(packet->GetCommand() == FNET_ControlPacket::FNET_CMD_CHANNEL_LOST);
        conn_lost.countDown();
        packet->Free();
        return FNET_FREE_CHANNEL;
    }
    FNET_Connection* connect(const std::string& spec) {
        FNET_Connection* conn = transport.Connect(spec.c_str(), &streamer);
        EXPECT_TRUE(conn != nullptr);
        assert(conn != nullptr);
        if (conn->OpenChannel(this, FNET_Context()) == nullptr) {
            conn_lost.countDown();
        }
        return conn;
    }
    ~TransportFixture() override { transport.ShutDown(true); }
};

//-----------------------------------------------------------------------------

struct ConnCheck {
    uint64_t target;
    ConnCheck() : target(FNET_Connection::get_num_connections()) { EXPECT_EQ(target, uint64_t(0)); }
    bool at_target() const { return (FNET_Connection::get_num_connections() == target); };
    bool await(duration max_wait) const {
        auto until = saturated_add(steady_clock::now(), max_wait);
        while (!at_target() && steady_clock::now() < until) {
            std::this_thread::sleep_for(1ms);
        }
        return at_target();
    }
    void await() const { ASSERT_TRUE(await(3600s)); }
};

TEST(ConnectTest, require_that_normal_connect_works) {
    constexpr size_t num_threads = 2;
    ServerSocket     f1("tcp/0");
    TransportFixture f2;
    ConnCheck        f3;
    TimeBomb         f4(60);
    auto             task = [&f1, &f2, &f3](Nexus& ctx) {
        auto thread_id = ctx.thread_id();
        if (thread_id == 0) {
            SocketHandle socket = f1.accept();
            EXPECT_TRUE(socket.valid());
            ctx.barrier();
        } else {
            std::string      spec = make_string("tcp/localhost:%d", f1.address().port());
            FNET_Connection* conn = f2.connect(spec);
            ctx.barrier();
            conn->Owner()->Close(conn);
            f2.conn_lost.await();
            EXPECT_TRUE(!f3.await(short_time));
            conn->internal_subref();
            f3.await();
        }
    };
    Nexus::run(num_threads, task);
}

TEST(ConnectTest, require_that_bogus_connect_fail_asynchronously) {
    TransportFixture f1;
    ConnCheck        f2;
    TimeBomb         f3(60);
    FNET_Connection* conn = f1.connect("invalid");
    f1.conn_lost.await();
    EXPECT_TRUE(!f2.await(short_time));
    conn->internal_subref();
    f2.await();
}

TEST(ConnectTest, require_that_async_close_can_be_called_before_async_resolve_completes) {
    constexpr size_t                      num_threads = 2;
    ServerSocket                          f1("tcp/0");
    std::shared_ptr<BlockingHostResolver> f2(std::make_shared<BlockingHostResolver>());
    TransportFixture                      f3(f2);
    ConnCheck                             f4;
    TimeBomb                              f5(60);
    auto                                  task = [&f1, &f2, &f3, &f4](Nexus& ctx) {
        auto thread_id = ctx.thread_id();
        if (thread_id == 0) {
            SocketHandle socket = f1.accept();
            EXPECT_TRUE(!socket.valid());
        } else {
            std::string      spec = make_string("tcp/localhost:%d", f1.address().port());
            FNET_Connection* conn = f3.connect(spec);
            f2->wait_for_caller();
            conn->Owner()->Close(conn);
            f3.conn_lost.await();
            f2->release_caller();
            EXPECT_TRUE(!f4.await(short_time));
            conn->internal_subref();
            f4.await();
            f1.shutdown();
        }
    };
    Nexus::run(num_threads, task);
}

TEST(ConnectTest, require_that_async_close_during_async_do_handshake_work_works) {
    constexpr size_t                      num_threads = 2;
    ServerSocket                          f1("tcp/0");
    std::shared_ptr<BlockingCryptoEngine> f2(std::make_shared<BlockingCryptoEngine>());
    TransportFixture                      f3(f2);
    ConnCheck                             f4;
    TimeBomb                              f5(60);
    auto                                  task = [&f1, &f2, &f3, &f4](Nexus& ctx) {
        auto thread_id = ctx.thread_id();
        if (thread_id == 0) {
            SocketHandle socket = f1.accept();
            EXPECT_TRUE(socket.valid());
            ctx.barrier(); // #1
        } else {
            std::string      spec = make_string("tcp/localhost:%d", f1.address().port());
            FNET_Connection* conn = f3.connect(spec);
            f2->handshake_work_enter.await();
            conn->Owner()->Close(conn, false);
            conn = nullptr; // ref given away
            f3.conn_lost.await();
            ctx.barrier(); // #1
            // verify that pending work keeps relevant objects alive
            EXPECT_TRUE(!f4.await(short_time));
            EXPECT_TRUE(!f2->handshake_socket_deleted.await(short_time));
            f2->handshake_work_exit.countDown();
            f4.await();
            f2->handshake_socket_deleted.await();
        }
    };
    Nexus::run(num_threads, task);
}

GTEST_MAIN_RUN_ALL_TESTS()
