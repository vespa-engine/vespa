// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/common/iconfigholder.h>
#include <vespa/config/common/trace.h>
#include <vespa/config/common/configdefinition.h>
#include <vespa/config/frt/connection.h>
#include <vespa/config/frt/frtsource.h>
#include <vespa/config/frt/frtconfigresponse.h>
#include <vespa/config/frt/frtconfigrequest.h>
#include <vespa/config/frt/frtconfigrequestv2.h>
#include <vespa/config/frt/frtconfigresponsev2.h>
#include <vespa/config/frt/frtconfigrequestv3.h>
#include <vespa/config/frt/frtconfigresponsev3.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/slime/json_format.h>
#include <vespa/fnet/fnet.h>
#include <vespa/fnet/frt/frt.h>
#include <vespa/fnet/frt/error.h>
#include <vespa/config/frt/protocol.h>
#include <lz4.h>
#include "config-my.h"
#include "config-bar.h"


using namespace config;
using namespace vespalib;
using namespace vespalib::slime;
using namespace config::protocol;
using namespace config::protocol::v2;
using namespace config::protocol::v3;

namespace {

    struct UpdateFixture : public IConfigHolder {
        ConfigUpdate::UP update;
        bool notified;

        UpdateFixture()
            : update(),
              notified(false)
        { }
        ConfigUpdate::UP provide() override { return ConfigUpdate::UP(); }
        void handle(ConfigUpdate::UP u) override { update = std::move(u); }
        bool wait(int timeoutInMillis) { (void) timeoutInMillis; return notified; }
        bool poll() override { return notified; }
        void interrupt() override { }

        bool waitUntilResponse(int timeoutInMillis)
        {
            FastOS_Time timer;
            timer.SetNow();
            while (timer.MilliSecsToNow() < timeoutInMillis) {
                if (notified)
                    break;
                FastOS_Thread::Sleep(100);
            }
            return notified;
        }
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
        FRT_RPCRequest * createOKResponse(const vespalib::string & defName="",
                                          const vespalib::string & defMd5="",
                                          const vespalib::string & configId="",
                                          const vespalib::string & configMd5="",
                                          int changed=0,
                                          long generation=0,
                                          const std::vector<vespalib::string> & payload = std::vector<vespalib::string>(),
                                          const vespalib::string & ns = "")
        {
            FRT_RPCRequest * req = new FRT_RPCRequest();
            FRT_Values & ret = *req->GetReturn();

            ret.AddString(defName.c_str());
            ret.AddString("");
            ret.AddString(defMd5.c_str());
            ret.AddString(configId.c_str());
            ret.AddString(configMd5.c_str());
            ret.AddInt32(changed);
            ret.AddInt64(generation);
            FRT_StringValue * payload_arr = ret.AddStringArray(payload.size());
            for (uint32_t i = 0; i < payload.size(); i++) {
                ret.SetString(&payload_arr[i], payload[i].c_str());
            }
            if (!ns.empty())
                ret.AddString(ns.c_str());
            req->SetError(FRTE_NO_ERROR);
            requests.push_back(req);
            return req;
        }

        ~RPCFixture() {
            for (size_t i = 0; i < requests.size(); i++) {
                requests[i]->SubRef();
            }
        }
    };


    struct MyAbortHandler : public FRT_IAbortHandler
    {
        bool aborted;
        MyAbortHandler() : aborted(false) { }
        bool HandleAbort() override { aborted = true; return true; }
    };

    struct ConnectionMock : public Connection {
        int errorCode;
        int timeout;
        FRT_RPCRequest * ans;
        FRT_Supervisor supervisor;
        FNET_Scheduler scheduler;
        vespalib::string address;
        ConnectionMock(FRT_RPCRequest * answer = NULL);
        ~ConnectionMock();
        FRT_RPCRequest * allocRPCRequest() override { return supervisor.AllocRPCRequest(); }
        void setError(int ec) override { errorCode = ec; }
        void invoke(FRT_RPCRequest * req, double t, FRT_IRequestWait * waiter) override
        {
            timeout = static_cast<int>(t);
            if (ans != NULL)
                waiter->RequestDone(ans);
            else
                waiter->RequestDone(req);
        }
        const vespalib::string & getAddress() const override { return address; }
        void setTransientDelay(int64_t delay) override { (void) delay; }
    };

    ConnectionMock::ConnectionMock(FRT_RPCRequest * answer)
        : errorCode(0),
          timeout(0),
          ans(answer),
          supervisor(),
          address()
    { }
    ConnectionMock::~ConnectionMock() { }

    struct FactoryMock : public ConnectionFactory {
        ConnectionMock * current;
        FactoryMock(ConnectionMock * c) : current(c) { }
        Connection * getCurrent() override {
            return current;
        }
        FNET_Scheduler * getScheduler() override { return &current->scheduler; }
        void syncTransport() override { }
    };


    struct AgentResultFixture
    {
        bool notified;
        uint64_t waitTime;
        uint64_t timeout;
        ConfigState state;
        AgentResultFixture(uint64_t w, uint64_t t)
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
        uint64_t getWaitTime () const override { return result->waitTime; }
        uint64_t getTimeout() const override { return result->timeout; }
        void handleResponse(const ConfigRequest & request, ConfigResponse::UP response) override
        {
            (void) request;
            (void) response;
            result->notified = true;
        }
        void handleRequest(ConfigRequest::UP request)
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
              conn(rpc.createOKResponse("foo", "baz", "4", "boo")),
              key("foo", "bar", "4", "boo")
        { }

    };

    struct FRTFixture
    {
        AgentResultFixture result;
        FRTConfigRequestFactory requestFactory;
        FRTSource src;

        FRTFixture(SourceFixture & f1)
            : result(2000, 10000),
              requestFactory(1, 3, VespaVersion::fromString("1.2.3"), CompressionType::UNCOMPRESSED),
              src(ConnectionFactory::SP(new FactoryMock(&f1.conn)),
                  requestFactory,
                  ConfigAgent::UP(new AgentFixture(&result)),
                  f1.key)
        { }
    };
}


TEST_F("require that empty config response does not validate", RPCFixture()) {
    FRTConfigResponseV1 fail1(f1.createEmptyRequest());
    ASSERT_FALSE(fail1.validateResponse());
    ASSERT_FALSE(fail1.hasValidResponse());
    ASSERT_TRUE(fail1.isError());
}

TEST_F("require that response containing errors does not validate", RPCFixture()) {
    FRTConfigResponseV1 fail1(f1.createErrorRequest());
    ASSERT_FALSE(fail1.validateResponse());
    ASSERT_FALSE(fail1.hasValidResponse());
    ASSERT_TRUE(fail1.isError());
    ASSERT_TRUE(fail1.errorCode() != 0);
}

TEST_F("require that valid response validates", RPCFixture()) {
    std::vector<vespalib::string> vec;
    vec.push_back("bar \"foo\"");
    FRTConfigResponseV1 ok(f1.createOKResponse("foo", "baz", "bim", "boo", 12, 15, vec, "mn"));
    ASSERT_TRUE(ok.validateResponse());
    ASSERT_TRUE(ok.hasValidResponse());
}

TEST_F("require that response contains all values", RPCFixture()) {
    FRTConfigResponseV1 ok(f1.createOKResponse("foo", "baz", "bim", "boo", 12, 15));
    ASSERT_FALSE(ok.validateResponse());
    ASSERT_FALSE(ok.hasValidResponse());
}

TEST_F("require that valid response returns values after fill", RPCFixture()) {
    std::vector<vespalib::string> vec;
    vec.push_back("bar \"foo\"");
    FRTConfigResponseV1 ok(f1.createOKResponse("foo", "baz", "bim", "boo", 12, 15, vec, "mn"));
    ASSERT_TRUE(ok.validateResponse());
    ASSERT_TRUE(ok.hasValidResponse());

    // Should not be valid
    ASSERT_TRUE(ConfigKey() == ok.getKey());
    ASSERT_TRUE(ConfigValue() == ok.getValue());

    ok.fill();
    ConfigKey key(ok.getKey());
    ASSERT_EQUAL("foo", key.getDefName());
    ASSERT_EQUAL("baz", key.getDefMd5());
    ASSERT_EQUAL("bim", key.getConfigId());
    ASSERT_EQUAL("mn", key.getDefNamespace());

    ConfigValue value(ok.getValue());
    ASSERT_TRUE(vec == value.getLines());
}

TEST("require that request parameters are correctly initialized") {
    ConnectionMock conn;
    std::vector<vespalib::string> schema;
    schema.push_back("foo");
    schema.push_back("bar");
    ConfigKey key("foo", "bar", "bim", "boo", schema);
    FRTConfigRequestV1 frtReq(key, &conn, "mymd5", 1337, 8);

    FRT_RPCRequest * req = frtReq.getRequest();
    FRT_Values & params(*req->GetParams());
    ASSERT_EQUAL("bar", std::string(params[0]._string._str));
    ASSERT_EQUAL("", std::string(params[1]._string._str));
    ASSERT_EQUAL("boo", std::string(params[2]._string._str));
    ASSERT_EQUAL("foo", std::string(params[3]._string._str));
    ASSERT_EQUAL("mymd5", std::string(params[4]._string._str));
    ASSERT_EQUAL(1337u, params[5]._intval64);
    ASSERT_EQUAL(8u, params[6]._intval64);
    ASSERT_EQUAL("bim", std::string(params[7]._string._str));
    ASSERT_EQUAL(2u, params[8]._string_array._len);
    ASSERT_EQUAL("foo", std::string(params[8]._string_array._pt[0]._str));
    ASSERT_EQUAL("bar", std::string(params[8]._string_array._pt[1]._str));
    ASSERT_EQUAL(1u, params[9]._intval32);
}

TEST("require that request is aborted") {
    MyAbortHandler handler;
    ConnectionMock conn;
    ConfigKey key("foo", "bar", "bim", "boo");
    FRTConfigRequestV1 frtReq(key, &conn, "mymd5", 1337, 8);
    frtReq.getRequest()->SetAbortHandler(&handler);
    ASSERT_FALSE(frtReq.isAborted());
    ASSERT_TRUE(frtReq.abort());
}

TEST_FF("require that request is invoked", SourceFixture(),
                                           FRTFixture(f1))
{
    f2.result.state = ConfigState("foo", 3);
    f2.src.getConfig();
    ASSERT_TRUE(f2.src.getCurrentRequest().verifyKey(f1.key));
    ASSERT_FALSE(f2.src.getCurrentRequest().verifyKey(ConfigKey("foo", "bal", "bim", "boo", std::vector<vespalib::string>())));
    ASSERT_FALSE(f2.src.getCurrentRequest().verifyState(ConfigState("foo", 0)));
    ASSERT_FALSE(f2.src.getCurrentRequest().verifyState(ConfigState("foo", 1)));
    ASSERT_FALSE(f2.src.getCurrentRequest().verifyState(ConfigState("bar", 1)));
    ASSERT_TRUE(f2.src.getCurrentRequest().verifyState(ConfigState("foo", 3)));
    ASSERT_TRUE(f2.result.notified);
    f2.src.close();
}

TEST_FF("require that request is config task is scheduled", SourceFixture(),
                                                            FRTFixture(f1))
{
    f2.src.getConfig();
    ASSERT_TRUE(f2.result.notified);
    f2.result.notified = false;
    FastOS_Time timer;
    timer.SetNow();
    while (timer.MilliSecsToNow() < 10000) {
        f1.conn.scheduler.CheckTasks();
        if (f2.result.notified)
            break;
        FastOS_Thread::Sleep(500);
    }
    ASSERT_TRUE(f2.result.notified);
    f2.src.close();
}

TEST("require that v2 request is correctly initialized") {
    ConnectionMock conn;
    ConfigKey key = ConfigKey::create<MyConfig>("foobi");
    vespalib::string md5 = "mymd5";
    int64_t currentGeneration = 3;
    int64_t wantedGeneration = 4;
    vespalib::string hostName = "myhost";
    int64_t timeout = 3000;
    Trace traceIn(3);
    traceIn.trace(2, "Hei");
    FRTConfigRequestV2 v2req(&conn, key, md5, currentGeneration, wantedGeneration, hostName, timeout, traceIn);
    ConfigDefinition origDef(MyConfig::CONFIG_DEF_SCHEMA);

    FRT_RPCRequest * req = v2req.getRequest();
    ASSERT_TRUE(req != NULL);
    FRT_Values & params(*req->GetParams());
    std::string json(params[0]._string._str);
    Slime slime;
    JsonFormat::decode(Memory(json), slime);
    Inspector & root(slime.get());
    EXPECT_EQUAL(2, root[REQUEST_VERSION].asLong());
    EXPECT_EQUAL(key.getDefName(), root[REQUEST_DEF_NAME].asString().make_string());
    EXPECT_EQUAL(key.getDefNamespace(), root[REQUEST_DEF_NAMESPACE].asString().make_string());
    EXPECT_EQUAL(key.getDefMd5(), root[REQUEST_DEF_MD5].asString().make_string());
    EXPECT_EQUAL(key.getConfigId(), root[REQUEST_CLIENT_CONFIGID].asString().make_string());
    EXPECT_EQUAL(hostName, root[REQUEST_CLIENT_HOSTNAME].asString().make_string());
    EXPECT_EQUAL(currentGeneration, root[REQUEST_CURRENT_GENERATION].asLong());
    EXPECT_EQUAL(wantedGeneration, root[REQUEST_WANTED_GENERATION].asLong());
    EXPECT_EQUAL(md5, root[REQUEST_CONFIG_MD5].asString().make_string());
    EXPECT_EQUAL(timeout, root[REQUEST_TIMEOUT].asLong());
    Trace trace;
    trace.deserialize(root[REQUEST_TRACE]);
    EXPECT_TRUE(trace.shouldTrace(2));
    EXPECT_TRUE(trace.shouldTrace(3));
    EXPECT_FALSE(trace.shouldTrace(4));
    EXPECT_EQUAL(timeout, root[REQUEST_TIMEOUT].asLong());
    ConfigDefinition def;
    def.deserialize(root[REQUEST_DEF_CONTENT]);
    EXPECT_EQUAL(origDef.asString(), def.asString());
    ConfigResponse::UP response(v2req.createResponse(req));
    req->GetReturn()->AddString("foobar");
    EXPECT_TRUE(response->validateResponse());
}

TEST("require that v2 reponse is correctly initialized") {
    ConnectionMock conn;
    Slime slime;
    ConfigKey key = ConfigKey::create<MyConfig>("foobi");
    vespalib::string md5 = "mymd5";
    int64_t generation = 3;
    vespalib::string hostname = "myhhost";
    Trace traceIn(3);
    traceIn.trace(2, "Hei!");
    Cursor & root(slime.setObject());
    root.setLong(RESPONSE_VERSION, 2ul);
    root.setString(RESPONSE_DEF_NAME, Memory(key.getDefName()));
    root.setString(RESPONSE_DEF_NAMESPACE, Memory(key.getDefNamespace()));
    root.setString(RESPONSE_DEF_MD5, Memory(key.getDefMd5()));
    root.setString(RESPONSE_CONFIGID, Memory(key.getConfigId()));
    root.setString(RESPONSE_CLIENT_HOSTNAME, Memory(hostname));
    root.setString(RESPONSE_CONFIG_MD5, Memory(md5));
    root.setLong(RESPONSE_CONFIG_GENERATION, generation);
    traceIn.serialize(root.setObject(RESPONSE_TRACE));
    Cursor & payload(root.setObject(RESPONSE_PAYLOAD));
    payload.setString("myField", "foobiar");
    SimpleBuffer buf;
    JsonFormat::encode(slime, buf, true);
    FRT_RPCRequest * req = conn.allocRPCRequest();
    req->GetReturn()->AddString(buf.get().make_string().c_str());
    FRTConfigResponseV2 response(req);
    ASSERT_TRUE(response.validateResponse());
    response.fill();
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
    EXPECT_EQUAL(md5, state.md5);
    EXPECT_EQUAL(generation, state.generation);
    ConfigValue value(response.getValue());
    MyConfig::UP config(value.newInstance<MyConfig>());
    EXPECT_EQUAL("foobiar", config->myField);
    req->SubRef();
}

TEST("require that v3 request is correctly initialized") {
    ConnectionMock conn;
    ConfigKey key = ConfigKey::create<MyConfig>("foobi");
    vespalib::string md5 = "mymd5";
    int64_t currentGeneration = 3;
    int64_t wantedGeneration = 4;
    vespalib::string hostName = "myhost";
    int64_t timeout = 3000;
    Trace traceIn(3);
    traceIn.trace(2, "Hei");
    FRTConfigRequestV3 v3req(&conn, key, md5, currentGeneration, wantedGeneration, hostName, timeout, traceIn, VespaVersion::fromString("1.2.3"), CompressionType::LZ4);
    ConfigDefinition origDef(MyConfig::CONFIG_DEF_SCHEMA);

    FRT_RPCRequest * req = v3req.getRequest();
    ASSERT_TRUE(req != NULL);
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
    EXPECT_EQUAL(wantedGeneration, root[REQUEST_WANTED_GENERATION].asLong());
    EXPECT_EQUAL(md5, root[REQUEST_CONFIG_MD5].asString().make_string());
    EXPECT_EQUAL(timeout, root[REQUEST_TIMEOUT].asLong());
    EXPECT_EQUAL("LZ4", root[REQUEST_COMPRESSION_TYPE].asString().make_string());
    EXPECT_EQUAL(root[REQUEST_VESPA_VERSION].asString().make_string(), "1.2.3");
    Trace trace;
    trace.deserialize(root[REQUEST_TRACE]);
    EXPECT_TRUE(trace.shouldTrace(2));
    EXPECT_TRUE(trace.shouldTrace(3));
    EXPECT_FALSE(trace.shouldTrace(4));
    EXPECT_EQUAL(timeout, root[REQUEST_TIMEOUT].asLong());
    ConfigDefinition def;
    def.deserialize(root[REQUEST_DEF_CONTENT]);
    EXPECT_EQUAL(origDef.asString(), def.asString());
    ConfigResponse::UP response(v3req.createResponse(req));
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
    vespalib::string md5;
    int64_t generation;
    vespalib::string hostname;
    Trace traceIn;

    V3RequestFixture()
        : conn(),
          slime(),
          root(slime.setObject()),
          req(conn.allocRPCRequest()), 
          key(ConfigKey::create<BarConfig>("foobi")),
          md5("mymd5"),
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
        root.setString(RESPONSE_CONFIG_MD5, Memory(md5));
        root.setLong(RESPONSE_CONFIG_GENERATION, generation);
        traceIn.serialize(root.setObject(RESPONSE_TRACE));
    }

    ~V3RequestFixture() {
        req->SubRef();
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
        EXPECT_EQUAL(md5, state.md5);
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
