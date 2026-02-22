// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/connection.h>
#include <vespa/fnet/connector.h>
#include <vespa/fnet/ipacketstreamer.h>
#include <vespa/fnet/iserveradapter.h>
#include <vespa/fnet/transport.h>
#include <vespa/fnet/transport_thread.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <chrono>
#include <set>
#include <thread>

using namespace std::literals;

struct DummyAdapter : FNET_IServerAdapter {
    bool InitChannel(FNET_Channel*, uint32_t) override { return false; }
};

struct DummyStreamer : FNET_IPacketStreamer {
    bool         GetPacketInfo(FNET_DataBuffer*, uint32_t*, uint32_t*, uint32_t*, bool*) override { return false; }
    FNET_Packet* Decode(FNET_DataBuffer*, uint32_t, uint32_t, FNET_Context) override { return nullptr; }
    void         Encode(FNET_Packet*, uint32_t, FNET_DataBuffer*) override {}
};

struct Fixture {
    DummyStreamer  streamer;
    DummyAdapter   adapter;
    FNET_Transport client;
    FNET_Transport server;
    Fixture() : streamer(), adapter(), client(8), server(8) {}
    void start() {
        ASSERT_TRUE(client.Start());
        ASSERT_TRUE(server.Start());
    }
    void wait_for_components(size_t client_cnt, size_t server_cnt) {
        bool ok = false;
        for (size_t i = 0; !ok && (i < 10000); ++i) {
            std::this_thread::sleep_for(3ms);
            ok = ((client.GetNumIOComponents() == client_cnt) && (server.GetNumIOComponents() == server_cnt));
        }
        EXPECT_EQ(client.GetNumIOComponents(), client_cnt);
        EXPECT_EQ(server.GetNumIOComponents(), server_cnt);
    }
    ~Fixture() {
        server.ShutDown(true);
        client.ShutDown(true);
    }
};

void check_threads(FNET_Transport& transport, size_t num_threads, const std::string& tag) {
    std::set<FNET_TransportThread*> threads;
    while (threads.size() < num_threads) {
        threads.insert(transport.select_thread(nullptr, 0));
    }
    for (auto thread : threads) {
        uint32_t cnt = thread->GetNumIOComponents();
        fprintf(stderr, "-- %s thread: %u io components\n", tag.c_str(), cnt);
        EXPECT_GT(cnt, 1u);
    }
}

TEST(ConnectionSpreadTest, require_that_connections_are_spread_among_transport_threads) {
    Fixture f1;
    ASSERT_NO_FATAL_FAILURE(f1.start());
    FNET_Connector* listener = f1.server.Listen("tcp/0", &f1.streamer, &f1.adapter);
    ASSERT_TRUE(listener);
    uint32_t                      port = listener->GetPortNumber();
    std::string                   spec = vespalib::make_string("tcp/localhost:%u", port);
    std::vector<FNET_Connection*> connections;
    for (size_t i = 0; i < 256; ++i) {
        std::this_thread::sleep_for(1ms);
        if (i > f1.server.GetNumIOComponents() + 16) {
            /*
             * tcp listen backlog is limited (cf. SOMAXCONN).
             * Slow down when getting too far ahead of server.
             */
            std::this_thread::sleep_for(10ms);
        }
        connections.push_back(f1.client.Connect(spec.c_str(), &f1.streamer));
        ASSERT_TRUE(connections.back());
    }
    f1.wait_for_components(256, 257);
    check_threads(f1.client, 8, "client");
    check_threads(f1.server, 8, "server");
    listener->internal_subref();
    for (FNET_Connection* conn : connections) {
        conn->internal_subref();
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
