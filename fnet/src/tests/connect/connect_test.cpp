// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/fnet/fnet.h>
#include <vespa/vespalib/net/server_socket.h>
#include <vespa/vespalib/util/sync.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace vespalib;

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

struct TransportFixture : FNET_IPacketHandler, FNET_IConnectionCleanupHandler {
    FNET_SimplePacketStreamer streamer;
    FastOS_ThreadPool pool;
    FNET_Transport transport;
    Gate conn_lost;
    Gate conn_deleted;
    TransportFixture() : streamer(nullptr), pool(128 * 1024), transport(),
                         conn_lost(), conn_deleted()
    {
        transport.Start(&pool);
    }
    TransportFixture(AsyncResolver::HostResolver::SP host_resolver)
        : streamer(nullptr), pool(128 * 1024), transport(make_resolver(std::move(host_resolver)), 1),
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
        FNET_Connection *conn = transport.Connect(spec.c_str(), &streamer, this);
        ASSERT_TRUE(conn != nullptr);
        conn->SetCleanupHandler(this);
        return conn;
    }
    ~TransportFixture() {
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
        EXPECT_TRUE(f2.conn_lost.await(60000));
        EXPECT_TRUE(!f2.conn_deleted.await(20));
        conn->SubRef();
        EXPECT_TRUE(f2.conn_deleted.await(60000));
    }
}

TEST_FF("require that bogus connect fail asynchronously", TransportFixture(), TimeBomb(60)) {
    FNET_Connection *conn = f1.connect("invalid");
    EXPECT_TRUE(f1.conn_lost.await(60000));
    EXPECT_TRUE(!f1.conn_deleted.await(20));
    conn->SubRef();
    EXPECT_TRUE(f1.conn_deleted.await(60000));
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
        EXPECT_TRUE(f3.conn_lost.await(60000));
        f2->release_caller();
        EXPECT_TRUE(!f3.conn_deleted.await(20));
        conn->SubRef();
        EXPECT_TRUE(f3.conn_deleted.await(60000));
        f1.shutdown();
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
