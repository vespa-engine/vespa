// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config/frt/protocol.h>
#include <vespa/config/common/configcontext.h>
#include <vespa/config/subscription/configsubscriber.hpp>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/rpcrequest.h>

#include "config-my.h"
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/nexus.h>
#include <barrier>
#include <latch>

#include <vespa/log/log.h>
LOG_SETUP("failover");

using namespace config;
using namespace config::protocol::v2;
using namespace vespalib::slime;
using namespace vespalib;
using vespalib::test::Nexus;

namespace {

int get_port(const std::string &spec) {
    const char *port = (spec.data() + spec.size());
    while ((port > spec.data()) && (port[-1] >= '0') && (port[-1] <= '9')) {
        --port;
    }
    return atoi(port);
}

const std::string requestTypes = "s";
const std::string responseTypes = "sx";

struct RPCServer : public FRT_Invokable {
    std::barrier<> barrier;
    int64_t gen;

    RPCServer() : barrier(2), gen(1) { }

    void init(FRT_Supervisor * s) {
        FRT_ReflectionBuilder rb(s);
        rb.DefineMethod("config.v3.getConfig", requestTypes.c_str(), responseTypes.c_str(),
                        FRT_METHOD(RPCServer::getConfig), this);
    }

    void getConfig(FRT_RPCRequest * req)
    {
        Slime slime;
        Cursor & root(slime.setObject());
        root.setLong(RESPONSE_VERSION, 3);
        root.setString(RESPONSE_DEF_NAME, Memory(MyConfig::CONFIG_DEF_NAME));
        root.setString(RESPONSE_DEF_NAMESPACE, Memory(MyConfig::CONFIG_DEF_NAMESPACE));
        root.setString(RESPONSE_DEF_MD5, Memory(MyConfig::CONFIG_DEF_MD5));
        Cursor &info = root.setObject("compressionInfo");
        info.setString("compressionType", "UNCOMPRESSED");
        info.setString("uncompressedSize", "0");
        root.setString(RESPONSE_CONFIGID, "myId");
        root.setString(RESPONSE_CLIENT_HOSTNAME, "myhost");
        root.setString(RESPONSE_CONFIG_XXHASH64, "xxhash64");
        root.setLong(RESPONSE_CONFIG_GENERATION, gen);
        root.setObject(RESPONSE_TRACE);
        Slime payload;
        payload.setObject().setString("myField", "myval");

        FRT_Values & ret = *req->GetReturn();
        SimpleBuffer buf;
        JsonFormat::encode(slime, buf, false);
        ret.AddString(buf.get().make_string().c_str());

        SimpleBuffer pbuf;
        JsonFormat::encode(payload, pbuf, false);
        std::string d = pbuf.get().make_string();
        ret.AddData(d.c_str(), d.size());
        LOG(info, "Answering...");
    }
    void wait() {
        barrier.arrive_and_wait();
    }
    void reload() { gen++; }
};

struct ServerFixture {
    using UP = std::unique_ptr<ServerFixture>;
    std::unique_ptr<fnet::frt::StandaloneFRT> frt;
    RPCServer server;
    std::barrier<> b;
    const std::string listenSpec;
    ServerFixture(const std::string & ls)
        : frt(),
          server(),
          b(2),
          listenSpec(ls)
    {
    }

    void wait()
    {
        b.arrive_and_wait();
    }

    void start()
    {
        frt = std::make_unique<fnet::frt::StandaloneFRT>();
        server.init(&frt->supervisor());
        frt->supervisor().Listen(get_port(listenSpec));
        wait(); // Wait until test runner signals we can start
        wait(); // Signalling that we have shut down
        wait(); // Wait for signal saying that supervisor is deleted
    }

    void stop()
    {
        if (frt) {
            frt.reset();
            wait(); // Wait for supervisor to shut down
            wait(); // Signal that we are done and start can return.
        }
    }

    ~ServerFixture() { stop(); }
};

struct NetworkFixture {
    std::vector<ServerFixture::UP> serverList;
    ServerSpec spec;
    bool running;
    NetworkFixture(const std::vector<std::string> & serverSpecs)
        : spec(serverSpecs), running(true)
    {
        for (size_t i = 0; i < serverSpecs.size(); i++) {
            serverList.push_back(std::make_unique<ServerFixture>(serverSpecs[i]));
        }
    }
    void start(size_t i) {
        serverList[i]->start();
    }
    void wait(size_t i) {
        serverList[i]->wait();
    }
    void waitAll() {
        for (size_t i = 0; i < serverList.size(); i++) {
            serverList[i]->wait();
        }
    }
    void run(size_t i) {
        while (running) {
            serverList[i]->start();
        }
    }
    void stopAll() {
        running = false;
        for (size_t i = 0; i < serverList.size(); i++) {
            serverList[i]->stop();
        }
    }
    void stop(size_t i) {
        serverList[i]->stop();
    }
    void reload() {
        for (size_t i = 0; i < serverList.size(); i++) {
            serverList[i]->server.reload();
        }
    }
};


TimingValues testTimingValues(
    500ms,  // successTimeout
    500ms,  // errorTimeout
    500ms,   // initialTimeout
    400ms,  // unsubscribeTimeout
    0ms,     // fixedDelay
    250ms,   // successDelay
    250ms,   // unconfiguredDelay
    500ms,   // configuredErrorDelay
    1,      // maxDelayMultiplier
    600ms,  // transientDelay
    1200ms); // fatalDelay

struct ConfigCheckFixture {
    std::shared_ptr<IConfigContext> ctx;
    NetworkFixture & nf;

    ConfigCheckFixture(NetworkFixture & f2)
        : ctx(std::make_shared<ConfigContext>(testTimingValues, f2.spec)),
          nf(f2)
    {
    }
    void checkSubscribe()
    {
        ConfigSubscriber s(ctx);
        ConfigHandle<MyConfig>::UP handle = s.subscribe<MyConfig>("myId");
        ASSERT_TRUE(s.nextConfig());
    }
    void verifySubscribeFailover(size_t index)
    {
        nf.stop(index);
        checkSubscribe();
        nf.wait(index);
    }

    void verifySubscribeFailover(size_t indexA, size_t indexB)
    {
        nf.stop(indexA);
        nf.stop(indexB);
        checkSubscribe();
        nf.wait(indexA);
        nf.wait(indexB);
    }
};

struct ThreeServersFixture {
    std::vector<std::string> specs;
    ThreeServersFixture() : specs() {
        specs.push_back("tcp/localhost:18590");
        specs.push_back("tcp/localhost:18592");
        specs.push_back("tcp/localhost:18594");
    }
};

}

TEST(FailoverTest, require_that_any_node_can_be_down_when_subscribing)
{
    constexpr size_t num_threads = 4;
    ThreeServersFixture f1;
    NetworkFixture f2(f1.specs);
    std::latch latch(num_threads);
    auto task = [&f2,&latch](Nexus& ctx) {
        auto thread_id = ctx.thread_id();
        if (thread_id == 0) {
            ConfigCheckFixture ccf(f2);
            f2.waitAll();
            ccf.checkSubscribe();
            ccf.verifySubscribeFailover(0);
            ccf.verifySubscribeFailover(1);
            ccf.verifySubscribeFailover(2);
            f2.stopAll();
            latch.arrive_and_wait();
        } else {
            f2.run(thread_id - 1);
            latch.arrive_and_wait();
        }
    };
    Nexus::run(num_threads, task);
}

GTEST_MAIN_RUN_ALL_TESTS()
