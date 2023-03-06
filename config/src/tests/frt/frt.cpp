// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config-my.h"
#include "config-bar.h"
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/common/trace.h>
#include <vespa/config/common/configdefinition.h>
#include <vespa/config/frt/connection.h>
#include <vespa/config/frt/frtsource.h>
#include <vespa/config/frt/frtconfigrequestv3.h>
#include <vespa/config/frt/frtconfigresponsev3.h>
#include <vespa/config/frt/connectionfactory.h>
#include <vespa/config/frt/frtconfigagent.h>
#include <vespa/config/frt/frtconfigrequestfactory.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/slime/json_format.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include <vespa/fnet/frt/error.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/config/frt/protocol.h>
#include <vespa/config/common/configvalue.hpp>

#include <lz4.h>
#include <thread>

using namespace config;
using namespace vespalib;
using namespace vespalib::slime;
using namespace config::protocol;
using namespace config::protocol::v2;
using namespace config::protocol::v3;

namespace {

struct Response {
    vespalib::string defName;
    vespalib::string defMd5;
    vespalib::string configId;
    vespalib::string configXxhash64;
    int changed;
    long generation;
    StringVector payload;
    vespalib::string ns;
    void encodeResponse(FRT_RPCRequest * req) const {
        FRT_Values & ret = *req->GetReturn();

        ret.AddString(defName.c_str());
        ret.AddString("");
        ret.AddString(defMd5.c_str());
        ret.AddString(configId.c_str());
        ret.AddString(configXxhash64.c_str());
        ret.AddInt32(changed);
        ret.AddInt64(generation);
        FRT_StringValue * payload_arr = ret.AddStringArray(payload.size());
        for (uint32_t i = 0; i < payload.size(); i++) {
            ret.SetString(&payload_arr[i], payload[i].c_str());
        }
        if (!ns.empty())
            ret.AddString(ns.c_str());
        req->SetError(FRTE_NO_ERROR);
    }
    Response(vespalib::stringref name, vespalib::stringref md5,
             vespalib::stringref id, vespalib::stringref hash,
             int changed_in=0, long generation_in=0)
        : defName(name),
          defMd5(md5),
          configId(id),
          configXxhash64(hash),
          changed(changed_in),
          generation(generation_in)
    {}
};

    struct RPCFixture
    {
        std::vector<FRT_RPCRequest *> requests;
        FRT_RPCRequest * createEmptyRequest() {
            FRT_RPCRequest * req = new FRT_RPCRequest();
            req->SetError(FRTE_NO_ERROR);
            requests.push_back(req);
            return req;
        }
        FRT_RPCRequest * createErrorRequest() {
            FRT_RPCRequest * req = new FRT_RPCRequest();
            req->SetError(FRTE_RPC_ABORT);
            requests.push_back(req);
            return req;
        }
        FRT_RPCRequest * createOKRequest(const Response & response)
        {
            FRT_RPCRequest * req = new FRT_RPCRequest();
            response.encodeResponse(req);
            requests.push_back(req);
            return req;
        }

        ~RPCFixture() {
            for (size_t i = 0; i < requests.size(); i++) {
                requests[i]->internal_subref();
            }
        }
    };

    struct ConnectionMock : public Connection {
        int errorCode;
        duration timeout;
        std::unique_ptr<Response> ans;
        fnet::frt::StandaloneFRT server;
        FRT_Supervisor & supervisor;
        FNET_Scheduler scheduler;
        vespalib::string address;
        ConnectionMock() : ConnectionMock(std::unique_ptr<Response>()) { }
        ConnectionMock(std::unique_ptr<Response> answer);
        ~ConnectionMock();
        FRT_RPCRequest * allocRPCRequest() override { return supervisor.AllocRPCRequest(); }
        void setError(int ec) override { errorCode = ec; }
        void invoke(FRT_RPCRequest * req, duration t, FRT_IRequestWait * waiter) override
        {
            timeout = t;
            if (ans != nullptr) {
                ans->encodeResponse(req);
                waiter->RequestDone(req);
            }
            else
                waiter->RequestDone(req);
        }
        const vespalib::string & getAddress() const override { return address; }
    };

    ConnectionMock::ConnectionMock(std::unique_ptr<Response> answer)
        : errorCode(0),
          timeout(0ms),
          ans(std::move(answer)),
          server(),
          supervisor(server.supervisor()),
          address()
    { }
    ConnectionMock::~ConnectionMock() = default;

    struct FactoryMock : public ConnectionFactory {
        ConnectionMock * current;
        FactoryMock(ConnectionMock * c) noexcept : current(c) { }
        ~FactoryMock() = default;
        Connection * getCurrent() override {
            return current;
        }
        FNET_Scheduler * getScheduler() override { return &current->scheduler; }
        void syncTransport() override { }
    };

    struct AgentResultFixture
    {
        bool notified;
        duration waitTime;
        duration timeout;
        ConfigState state;
        AgentResultFixture(duration w, duration t)
            : notified(false),
              waitTime(w),
              timeout(t),
              state()
        { }
    };

    struct AgentFixture : public ConfigAgent
    {
        AgentResultFixture * result;

        AgentFixture(AgentResultFixture * r)
            : result(r)
        {
        }

        const ConfigState & getConfigState() const override { return result->state; }
        duration getWaitTime () const override { return result->waitTime; }
        duration getTimeout() const override { return result->timeout; }
        void handleResponse(const ConfigRequest & request, std::unique_ptr<ConfigResponse> response) override
        {
            (void) request;
            (void) response;
            result->notified = true;
        }
        void handleRequest(std::unique_ptr<ConfigResponse> request)
        {
            (void) request;
        }
        bool abort() { return true; }
    };

    struct SourceFixture {
        RPCFixture rpc;
        ConnectionMock conn;
        ConfigKey key;
        SourceFixture()
            : rpc(),
              conn(std::make_unique<Response>("foo", "baz", "4", "boo")),
              key("foo", "bar", "4", "boo")
        { }

    };

    struct FRTFixture
    {
        AgentResultFixture result;
        FRTConfigRequestFactory requestFactory;
        FRTSource src;

        FRTFixture(SourceFixture & f1)
            : result(2s, 10s),
              requestFactory(3, VespaVersion::fromString("1.2.3"), CompressionType::UNCOMPRESSED),
              src(std::make_shared<FactoryMock>(&f1.conn),
                  requestFactory,
                  std::make_unique<AgentFixture>(&result),
                  f1.key)
        { }
    };
}


TEST_F("require that empty config response does not validate", RPCFixture()) {
    FRTConfigResponseV3 fail1(f1.createEmptyRequest());
    ASSERT_FALSE(fail1.validateResponse());
    ASSERT_FALSE(fail1.hasValidResponse());
    ASSERT_TRUE(fail1.isError());
}

TEST_F("require that response containing errors does not validate", RPCFixture()) {
    FRTConfigResponseV3 fail1(f1.createErrorRequest());
    ASSERT_FALSE(fail1.validateResponse());
    ASSERT_FALSE(fail1.hasValidResponse());
    ASSERT_TRUE(fail1.isError());
    ASSERT_TRUE(fail1.errorCode() != 0);
}

TEST_F("require that response contains all values", RPCFixture()) {
    FRTConfigResponseV3 ok(f1.createOKRequest(Response("foo", "baz", "bim", "boo", 12, 15)));
    ASSERT_FALSE(ok.validateResponse());
    ASSERT_FALSE(ok.hasValidResponse());
}

TEST_FF("require that request is config task is scheduled", SourceFixture(), FRTFixture(f1))
{
    f2.src.getConfig();
    ASSERT_TRUE(f2.result.notified);
    f2.result.notified = false;
    vespalib::Timer timer;
    while (timer.elapsed() < 10s) {
        f1.conn.scheduler.CheckTasks();
        if (f2.result.notified)
            break;
        std::this_thread::sleep_for(500ms);
    }
    ASSERT_TRUE(f2.result.notified);
    f2.src.close();
}

TEST("require that v3 request is correctly initialized") {
    ConnectionMock conn;
    ConfigKey key = ConfigKey::create<MyConfig>("foobi");
    vespalib::string xxhash64 = "myxxhash64";
    int64_t currentGeneration = 3;
    vespalib::string hostName = "myhost";
    duration timeout = 3s;
    Trace traceIn(3);
    traceIn.trace(2, "Hei");
    FRTConfigRequestV3 v3req(&conn, key, xxhash64, currentGeneration, hostName,
                             timeout, traceIn, VespaVersion::fromString("1.2.3"), CompressionType::LZ4);
    ASSERT_TRUE(v3req.verifyState(ConfigState(xxhash64, 3, false)));
    ASSERT_FALSE(v3req.verifyState(ConfigState(xxhash64, 2, false)));
    ASSERT_FALSE(v3req.verifyState(ConfigState("xxx", 3, false)));
    ASSERT_FALSE(v3req.verifyState(ConfigState("xxx", 2, false)));

    ConfigDefinition origDef(MyConfig::CONFIG_DEF_SCHEMA);

    FRT_RPCRequest * req = v3req.getRequest();
    ASSERT_TRUE(req != nullptr);
    FRT_Values & params(*req->GetParams());
    std::string json(params[0]._string._str);
    Slime slime;
    JsonFormat::decode(Memory(json), slime);
    Inspector & root(slime.get());
    EXPECT_EQUAL(3, root[REQUEST_VERSION].asLong());
    EXPECT_EQUAL(key.getDefName(), root[REQUEST_DEF_NAME].asString().make_string());
    EXPECT_EQUAL(key.getDefNamespace(), root[REQUEST_DEF_NAMESPACE].asString().make_string());
    EXPECT_EQUAL(key.getDefMd5(), root[REQUEST_DEF_MD5].asString().make_string());
    EXPECT_EQUAL(key.getConfigId(), root[REQUEST_CLIENT_CONFIGID].asString().make_string());
    EXPECT_EQUAL(hostName, root[REQUEST_CLIENT_HOSTNAME].asString().make_string());
    EXPECT_EQUAL(currentGeneration, root[REQUEST_CURRENT_GENERATION].asLong());
    EXPECT_EQUAL(xxhash64, root[REQUEST_CONFIG_XXHASH64].asString().make_string());
    EXPECT_EQUAL(count_ms(timeout), root[REQUEST_TIMEOUT].asLong());
    EXPECT_EQUAL("LZ4", root[REQUEST_COMPRESSION_TYPE].asString().make_string());
    EXPECT_EQUAL(root[REQUEST_VESPA_VERSION].asString().make_string(), "1.2.3");
    Trace trace;
    trace.deserialize(root[REQUEST_TRACE]);
    EXPECT_TRUE(trace.shouldTrace(2));
    EXPECT_TRUE(trace.shouldTrace(3));
    EXPECT_FALSE(trace.shouldTrace(4));
    EXPECT_EQUAL(count_ms(timeout), root[REQUEST_TIMEOUT].asLong());
    ConfigDefinition def;
    def.deserialize(root[REQUEST_DEF_CONTENT]);
    EXPECT_EQUAL(origDef.asString(), def.asString());
    std::unique_ptr<ConfigResponse> response(v3req.createResponse(req));
    req->GetReturn()->AddString("foobar");
    req->GetReturn()->AddData("foo", 3);
    EXPECT_TRUE(response->validateResponse());
}

struct V3RequestFixture {
    ConnectionMock conn;
    Slime slime;
    Cursor & root;
    FRT_RPCRequest * req;
    ConfigKey key;
    vespalib::string xxhash64;
    int64_t generation;
    vespalib::string hostname;
    Trace traceIn;

    V3RequestFixture()
        : conn(),
          slime(),
          root(slime.setObject()),
          req(conn.allocRPCRequest()), 
          key(ConfigKey::create<BarConfig>("foobi")),
          xxhash64("myxxhash64"),
          generation(3),
          hostname("myhhost"),
          traceIn(3)
    {
        traceIn.trace(2, "Hei!");
        root.setLong(RESPONSE_VERSION, 3ul);
        root.setString(RESPONSE_DEF_NAME, Memory(key.getDefName()));
        root.setString(RESPONSE_DEF_NAMESPACE, Memory(key.getDefNamespace()));
        root.setString(RESPONSE_DEF_MD5, Memory(key.getDefMd5()));
        root.setString(RESPONSE_CONFIGID, Memory(key.getConfigId()));
        root.setString(RESPONSE_CLIENT_HOSTNAME, Memory(hostname));
        root.setString(RESPONSE_CONFIG_XXHASH64, Memory(xxhash64));
        root.setLong(RESPONSE_CONFIG_GENERATION, generation);
        traceIn.serialize(root.setObject(RESPONSE_TRACE));
    }

    ~V3RequestFixture() {
        req->internal_subref();
    }

    void encodePayload(const char * payload, uint32_t payloadSize, uint32_t uncompressedSize, const CompressionType & compressionType) {
        Cursor & compressionInfo(root.setObject(RESPONSE_COMPRESSION_INFO));
        compressionInfo.setString("compressionType", Memory(compressionTypeToString(compressionType)));
        compressionInfo.setLong("uncompressedSize", uncompressedSize);
        SimpleBuffer buf;
        JsonFormat::encode(slime, buf, true);
        req->GetReturn()->AddString(buf.get().make_string().c_str());
        req->GetReturn()->AddData(payload, payloadSize);
    }

    FRTConfigResponseV3 * createResponse() {
        return new FRTConfigResponseV3(req);
    }

    void assertResponse(const FRTConfigResponseV3 & response, const char *expectedValue) {
        Trace trace(response.getTrace());
        EXPECT_TRUE(trace.shouldTrace(3));
        EXPECT_FALSE(trace.shouldTrace(4));
        ConfigKey responseKey(response.getKey());
        EXPECT_EQUAL(key.getDefName(), responseKey.getDefName());
        EXPECT_EQUAL(key.getDefNamespace(), responseKey.getDefNamespace());
        EXPECT_EQUAL(key.getDefMd5(), responseKey.getDefMd5());
        EXPECT_EQUAL(key.getConfigId(), responseKey.getConfigId());
        EXPECT_EQUAL(hostname, response.getHostName());
        ConfigState state(response.getConfigState());
        EXPECT_EQUAL(xxhash64, state.xxhash64);
        EXPECT_EQUAL(generation, state.generation);
        ConfigValue value(response.getValue());
        BarConfig::UP config(value.newInstance<BarConfig>());
        EXPECT_EQUAL(expectedValue, config->barValue);
    }
};

TEST_F("require that v3 uncompressed reponse is correctly initialized", V3RequestFixture()) {
    const char *payload = "{\"barValue\":\"foobiar\"}";
    f1.encodePayload(payload, strlen(payload), strlen(payload), CompressionType::UNCOMPRESSED);
    std::unique_ptr<FRTConfigResponseV3> response(f1.createResponse());
    ASSERT_TRUE(response->validateResponse());
    response->fill();
    f1.assertResponse(*response, "foobiar");
}

TEST_F("require that v3 compressed reponse is correctly initialized", V3RequestFixture()) {
    const char *payload = "{\"barValue\":\"foobiar\"}";
    int maxSize = LZ4_compressBound(strlen(payload));
    char *output = (char *)malloc(maxSize);
    int sz = LZ4_compress_default(payload, output, strlen(payload), maxSize);

    f1.encodePayload(output, sz, strlen(payload), CompressionType::LZ4);
    std::unique_ptr<FRTConfigResponseV3> response(f1.createResponse());
    ASSERT_TRUE(response->validateResponse());
    response->fill();
    f1.assertResponse(*response, "foobiar");
    free(output);
}

TEST_F("require that empty v3 reponse is correctly initialized", V3RequestFixture()) {
    const char *payload = "";
    f1.encodePayload(payload, strlen(payload), strlen(payload), CompressionType::UNCOMPRESSED);
    std::unique_ptr<FRTConfigResponseV3> response(f1.createResponse());
    ASSERT_TRUE(response->validateResponse());
    response->fill();
    f1.assertResponse(*response, "defaultBar");
}

TEST_MAIN() { TEST_RUN_ALL(); }
