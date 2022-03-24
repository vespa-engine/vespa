// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/time_bomb.h>
#include <vespa/fnet/transport.h>
#include <vespa/fnet/transport_thread.h>
#include <vespa/fnet/simplepacketstreamer.h>
#include <vespa/fnet/ipackethandler.h>
#include <vespa/fnet/connection.h>
#include <vespa/fnet/controlpacket.h>
#include <vespa/vespalib/net/server_socket.h>
#include <vespa/vespalib/net/crypto_engine.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace vespalib;

constexpr vespalib::duration short_time = 20ms;

struct BlockingHostResolver : public AsyncResolver::HostResolver {
    AsyncResolver::SimpleHostResolver resolver;
    Gate caller;
    Gate barrier;
    BlockingHostResolver() : resolver(), caller(), barrier() {}
    vespalib::string ip_address(const vespalib::string &host) override {
        fprintf(stderr, "blocking resolve request: '%s'\n", host.c_str());
        caller.countDown();
        barrier.await();
        vespalib::string result = resolver.ip_address(host);
        fprintf(stderr, "returning resolve result: '%s'\n", result.c_str());
        return result;
    }
    void wait_for_caller() { caller.await(); }
    void release_caller() { barrier.countDown(); }
};

AsyncResolver::SP make_resolver(AsyncResolver::HostResolver::SP host_resolver) {
    AsyncResolver::Params params;
    params.resolver = host_resolver;
    return AsyncResolver::create(params);
}

//-----------------------------------------------------------------------------

struct BlockingCryptoSocket : public CryptoSocket {
    SocketHandle socket;
    Gate &handshake_work_enter;
    Gate &handshake_work_exit;
    Gate &handshake_socket_deleted;
    BlockingCryptoSocket(SocketHandle s, Gate &hs_work_enter, Gate &hs_work_exit, Gate &hs_socket_deleted)
        : socket(std::move(s)), handshake_work_enter(hs_work_enter), handshake_work_exit(hs_work_exit),
          handshake_socket_deleted(hs_socket_deleted) {}
    ~BlockingCryptoSocket() override {
        handshake_socket_deleted.countDown();
    }
    int get_fd() const override { return socket.get(); }
    HandshakeResult handshake() override { return HandshakeResult::NEED_WORK; }
    void do_handshake_work() override {
        handshake_work_enter.countDown();
        handshake_work_exit.await();
    }
    size_t min_read_buffer_size() const override { return 1; }
    ssize_t read(char *buf, size_t len) override { return socket.read(buf, len); }
    ssize_t drain(char *, size_t) override { return 0; }
    ssize_t write(const char *buf, size_t len) override { return socket.write(buf, len); }
    ssize_t flush() override { return 0; }
    ssize_t half_close() override { return socket.half_close(); }
    void drop_empty_buffers() override {}
};

struct BlockingCryptoEngine : public CryptoEngine {
    Gate handshake_work_enter;
    Gate handshake_work_exit;
    Gate handshake_socket_deleted;
    bool use_tls_when_client() const override { return false; }
    bool always_use_tls_when_server() const override { return false; }
    CryptoSocket::UP create_client_crypto_socket(SocketHandle socket, const SocketSpec &) override {
        return std::make_unique<BlockingCryptoSocket>(std::move(socket),
                handshake_work_enter, handshake_work_exit, handshake_socket_deleted);
    }
    CryptoSocket::UP create_server_crypto_socket(SocketHandle socket) override {
        return std::make_unique<BlockingCryptoSocket>(std::move(socket),
                handshake_work_enter, handshake_work_exit, handshake_socket_deleted);
    }
};

//-----------------------------------------------------------------------------

struct TransportFixture : FNET_IPacketHandler, FNET_IConnectionCleanupHandler {
    FNET_SimplePacketStreamer streamer;
    FastOS_ThreadPool pool;
    FNET_Transport transport;
    Gate conn_lost;
    Gate conn_deleted;
    TransportFixture() : streamer(nullptr), pool(128_Ki), transport(),
                         conn_lost(), conn_deleted()
    {
        transport.Start(&pool);
    }
    TransportFixture(AsyncResolver::HostResolver::SP host_resolver)
        : streamer(nullptr), pool(128_Ki), transport(fnet::TransportConfig().resolver(make_resolver(std::move(host_resolver)))),
          conn_lost(), conn_deleted()
    {
        transport.Start(&pool);
    }
    TransportFixture(CryptoEngine::SP crypto)
        : streamer(nullptr), pool(128_Ki), transport(fnet::TransportConfig().crypto(std::move(crypto))),
          conn_lost(), conn_deleted()
    {
        transport.Start(&pool);
    }
    HP_RetCode HandlePacket(FNET_Packet *packet, FNET_Context) override {
        ASSERT_TRUE(packet->GetCommand() == FNET_ControlPacket::FNET_CMD_CHANNEL_LOST);
        conn_lost.countDown();
        packet->Free();
        return FNET_FREE_CHANNEL;
    }
    void Cleanup(FNET_Connection *) override { conn_deleted.countDown(); }
    FNET_Connection *connect(const vespalib::string &spec) {
        FNET_Connection *conn = transport.Connect(spec.c_str(), &streamer);
        ASSERT_TRUE(conn != nullptr);
        if (conn->OpenChannel(this, FNET_Context()) == nullptr) {
            conn_lost.countDown();
        }
        conn->SetCleanupHandler(this);
        return conn;
    }
    ~TransportFixture() override {
        transport.ShutDown(true);
        pool.Close();
    }
};

//-----------------------------------------------------------------------------

TEST_MT_FFF("require that normal connect works", 2,
            ServerSocket("tcp/0"), TransportFixture(), TimeBomb(60))
{
    if (thread_id == 0) {
        SocketHandle socket = f1.accept();
        EXPECT_TRUE(socket.valid());
        TEST_BARRIER();
    } else {
        vespalib::string spec = make_string("tcp/localhost:%d", f1.address().port());
        FNET_Connection *conn = f2.connect(spec);
        TEST_BARRIER();
        conn->Owner()->Close(conn);
        f2.conn_lost.await();
        EXPECT_TRUE(!f2.conn_deleted.await(short_time));
        conn->SubRef();
        f2.conn_deleted.await();
    }
}

TEST_FF("require that bogus connect fail asynchronously", TransportFixture(), TimeBomb(60)) {
    FNET_Connection *conn = f1.connect("invalid");
    f1.conn_lost.await();
    EXPECT_TRUE(!f1.conn_deleted.await(short_time));
    conn->SubRef();
    f1.conn_deleted.await();
}

TEST_MT_FFFF("require that async close can be called before async resolve completes", 2,
             ServerSocket("tcp/0"), std::shared_ptr<BlockingHostResolver>(new BlockingHostResolver()),
             TransportFixture(f2), TimeBomb(60))
{
    if (thread_id == 0) {
        SocketHandle socket = f1.accept();
        EXPECT_TRUE(!socket.valid());
    } else {
        vespalib::string spec = make_string("tcp/localhost:%d", f1.address().port());
        FNET_Connection *conn = f3.connect(spec);
        f2->wait_for_caller();
        conn->Owner()->Close(conn);
        f3.conn_lost.await();
        f2->release_caller();
        EXPECT_TRUE(!f3.conn_deleted.await(short_time));
        conn->SubRef();
        f3.conn_deleted.await();
        f1.shutdown();
    }
}

TEST_MT_FFFF("require that async close during async do_handshake_work works", 2,
             ServerSocket("tcp/0"), std::shared_ptr<BlockingCryptoEngine>(new BlockingCryptoEngine()),
             TransportFixture(f2), TimeBomb(60))
{
    if (thread_id == 0) {
        SocketHandle socket = f1.accept();
        EXPECT_TRUE(socket.valid());
        TEST_BARRIER(); // #1
    } else {
        vespalib::string spec = make_string("tcp/localhost:%d", f1.address().port());
        FNET_Connection *conn = f3.connect(spec);
        f2->handshake_work_enter.await();
        conn->Owner()->Close(conn, false);
        conn = nullptr; // ref given away
        f3.conn_lost.await();
        TEST_BARRIER(); // #1
        // verify that pending work keeps relevant objects alive
        EXPECT_TRUE(!f3.conn_deleted.await(short_time));
        EXPECT_TRUE(!f2->handshake_socket_deleted.await(short_time));
        f2->handshake_work_exit.countDown();
        f3.conn_deleted.await();
        f2->handshake_socket_deleted.await();
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
