// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/common/testhelper.h>
#include <vespa/document/base/testdocman.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/slobrok/sbmirror.h>
#include <vespa/storage/storageserver/communicationmanager.h>
#include <vespa/storage/storageserver/message_dispatcher.h>
#include <vespa/storage/storageserver/rpc/caching_rpc_target_resolver.h>
#include <vespa/storage/storageserver/rpc/message_codec_provider.h>
#include <vespa/storage/storageserver/rpc/shared_rpc_resources.h>
#include <vespa/storage/storageserver/rpc/storage_api_rpc_service.h>
#include <vespa/storage/storageserver/rpcrequestwrapper.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/host_name.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <gmock/gmock.h>
#include <condition_variable>
#include <deque>
#include <functional>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>

#include <thread>

using namespace ::testing;
using namespace document::test;
using namespace std::chrono_literals;

namespace storage::rpc {

namespace {

constexpr std::chrono::duration message_timeout = 60s;
constexpr std::chrono::duration slobrok_register_timeout = 60s;

class LockingMockOperationDispatcher : public MessageDispatcher {
    using MessageQueueType = std::deque<std::shared_ptr<api::StorageMessage>>;

    mutable std::mutex              _mutex;
    mutable std::condition_variable _cond;
    MessageQueueType                _enqueued;
public:
    LockingMockOperationDispatcher();
    ~LockingMockOperationDispatcher() override;

    void dispatch_sync(std::shared_ptr<api::StorageMessage> msg) override {
        std::lock_guard lock(_mutex);
        _enqueued.emplace_back(std::move(msg));
        _cond.notify_all();
    }

    void dispatch_async(std::shared_ptr<api::StorageMessage> msg) override {
        std::lock_guard lock(_mutex);
        _enqueued.emplace_back(std::move(msg));
        _cond.notify_all();
    }

    [[nodiscard]] bool empty() const noexcept {
        std::lock_guard lock(_mutex);
        return _enqueued.empty();
    }

    void wait_until_n_messages_received(size_t n) const {
        std::unique_lock lock(_mutex);
        const auto deadline = std::chrono::steady_clock::now() + message_timeout;
        if (!_cond.wait_until(lock, deadline, [this, n]{ return (_enqueued.size() == n); })) {
            throw std::runtime_error("Timed out waiting for message");
        }
    }

    [[nodiscard]] std::shared_ptr<api::StorageMessage> pop_first_message() {
        std::lock_guard lock(_mutex);
        assert(!_enqueued.empty());
        auto msg = std::move(_enqueued.front());
        _enqueued.pop_front();
        return msg;
    }
};

LockingMockOperationDispatcher::LockingMockOperationDispatcher()  = default;
LockingMockOperationDispatcher::~LockingMockOperationDispatcher() = default;

api::StorageMessageAddress make_address(uint16_t node_index, bool is_distributor) {
    static vespalib::string _coolcluster("coolcluster");
    return {&_coolcluster, (is_distributor ? lib::NodeType::DISTRIBUTOR : lib::NodeType::STORAGE), node_index};
}

vespalib::string to_slobrok_id(const api::StorageMessageAddress& address) {
    // TODO factor out slobrok ID generation code to be independent of resolver?
    return CachingRpcTargetResolver::address_to_slobrok_id(address);
}

class RpcNode {
protected:
    vdstestlib::DirConfig                             _config;
    std::shared_ptr<const document::DocumentTypeRepo> _doc_type_repo;
    LockingMockOperationDispatcher                    _messages;
    std::unique_ptr<MessageCodecProvider>             _codec_provider;
    std::unique_ptr<SharedRpcResources>               _shared_rpc_resources;
    api::StorageMessageAddress                        _node_address;
    vespalib::string                                  _slobrok_id;
public:
    RpcNode(uint16_t node_index, bool is_distributor, const mbus::Slobrok& slobrok)
        : _config(getStandardConfig(true)),
          _doc_type_repo(document::TestDocRepo().getTypeRepoSp()),
          _node_address(make_address(node_index, is_distributor)),
          _slobrok_id(to_slobrok_id(_node_address))
    {
        auto& cfg = _config.getConfig("stor-server");
        cfg.set("node_index", std::to_string(node_index));
        cfg.set("is_distributor", is_distributor ? "true" : "false");
        addSlobrokConfig(_config, slobrok);

        _shared_rpc_resources = std::make_unique<SharedRpcResources>(config::ConfigUri(_config.getConfigId()), 0, 1, 1);
        // TODO make codec provider into interface so we can test decode-failures more easily?
        _codec_provider = std::make_unique<MessageCodecProvider>(_doc_type_repo);
    }
    ~RpcNode();

    const api::StorageMessageAddress& node_address() const noexcept { return _node_address; }
    const SharedRpcResources& shared_rpc_resources() const noexcept { return *_shared_rpc_resources; }
    SharedRpcResources& shared_rpc_resources() noexcept { return *_shared_rpc_resources; }

    void wait_until_visible_in_slobrok(vespalib::stringref id) {
        const auto deadline = std::chrono::steady_clock::now() + slobrok_register_timeout;
        while (_shared_rpc_resources->slobrok_mirror().lookup(id).empty()) {
            if (std::chrono::steady_clock::now() > deadline) {
                throw std::runtime_error("Timed out waiting for node to be visible in Slobrok");
            }
            std::this_thread::sleep_for(10ms);
        }
    }
};

RpcNode::~RpcNode() = default;

class StorageApiNode : public RpcNode {
    std::unique_ptr<StorageApiRpcService> _service;
public:
    StorageApiNode(uint16_t node_index, bool is_distributor, const mbus::Slobrok& slobrok)
        : RpcNode(node_index, is_distributor, slobrok)
    {
        StorageApiRpcService::Params params;
        _service = std::make_unique<StorageApiRpcService>(_messages, *_shared_rpc_resources, *_codec_provider, params);

        _shared_rpc_resources->start_server_and_register_slobrok(_slobrok_id);
        // Explicitly wait until we are visible in Slobrok. Just waiting for mirror readiness is not enough.
        wait_until_visible_in_slobrok(_slobrok_id);
    }
    ~StorageApiNode();

    std::shared_ptr<api::PutCommand> create_dummy_put_command() const {
        auto doc_type = _doc_type_repo->getDocumentType("testdoctype1");
        auto doc = std::make_shared<document::Document>(*_doc_type_repo, *doc_type, document::DocumentId("id:foo:testdoctype1::bar"));
        doc->setFieldValue(doc->getField("hstringval"), std::make_unique<document::StringFieldValue>("hello world"));
        return std::make_shared<api::PutCommand>(makeDocumentBucket(document::BucketId(0)), std::move(doc), 100);
    }

    void send_request_verify_not_bounced(std::shared_ptr<api::StorageCommand> req) {
        if (!_messages.empty()) {
            throw std::runtime_error("Node had pending messages before send");
        }
        _service->send_rpc_v1_request(std::move(req));
        if (!_messages.empty()) {
            throw std::runtime_error("RPC request was bounced. Most likely due to missing Slobrok mapping");
        }
    }

    void send_request(std::shared_ptr<api::StorageCommand> req) {
        _service->send_rpc_v1_request(std::move(req));
    }

    // TODO move StorageTransportContext away from communicationmanager.h
    // TODO refactor reply handling to avoid duping detail code with CommunicationManager?
    void send_response(const std::shared_ptr<api::StorageReply>& reply) {
        std::unique_ptr<StorageTransportContext> context(dynamic_cast<StorageTransportContext*>(
                reply->getTransportContext().release()));
        assert(context);
        _service->encode_rpc_v1_response(*context->_request->raw_request(), *reply);
        context->_request->returnRequest();
    }

    [[nodiscard]] std::shared_ptr<api::StorageMessage> wait_and_receive_single_message() {
        _messages.wait_until_n_messages_received(1);
        return _messages.pop_first_message();
    }

    void send_raw_request_and_expect_error(StorageApiNode& node,
                                           FRT_RPCRequest* req,
                                           const vespalib::string& expected_msg) {
        auto spec = vespalib::make_string("tcp/localhost:%d", node.shared_rpc_resources().listen_port());
        auto* target = _shared_rpc_resources->supervisor().GetTarget(spec.c_str());
        target->InvokeSync(req, 60.0);
        EXPECT_TRUE(req->IsError());
        EXPECT_EQ(req->GetErrorCode(), FRTE_RPC_METHOD_FAILED);
        EXPECT_EQ(req->GetErrorMessage(), expected_msg);
        target->internal_subref();
        req->internal_subref();
    }
};

StorageApiNode::~StorageApiNode() {
    // Ensure we shut down the underlying RPC threads before destroying
    // the RPC service that may receive callbacks from it.
    _shared_rpc_resources->shutdown();
}

} // anonymous namespace

// TODO consider completely mocking Slobrok to avoid any race conditions during node registration
struct StorageApiRpcServiceTest : Test {
    mbus::Slobrok _slobrok;
    std::unique_ptr<StorageApiNode> _node_0;
    std::unique_ptr<StorageApiNode> _node_1;

    StorageApiRpcServiceTest()
        : _slobrok(),
          _node_0(std::make_unique<StorageApiNode>(1, true, _slobrok)),
          _node_1(std::make_unique<StorageApiNode>(4, false, _slobrok))
    {
        // FIXME ugh, this isn't particularly pretty...
        _node_0->wait_until_visible_in_slobrok(to_slobrok_id(_node_1->node_address()));
        _node_1->wait_until_visible_in_slobrok(to_slobrok_id(_node_0->node_address()));
    }
    ~StorageApiRpcServiceTest() override;

    static api::StorageMessageAddress non_existing_address() {
        return make_address(100, false);
    }

    [[nodiscard]] std::shared_ptr<api::PutCommand> send_and_receive_put_command_at_node_1(
            const std::function<void(api::PutCommand&)>& req_mutator) {
        auto cmd = _node_0->create_dummy_put_command();
        cmd->setAddress(_node_1->node_address());
        req_mutator(*cmd);
        _node_0->send_request_verify_not_bounced(cmd);

        auto recv_msg = _node_1->wait_and_receive_single_message();
        auto recv_as_put = std::dynamic_pointer_cast<api::PutCommand>(recv_msg);
        assert(recv_as_put);
        return recv_as_put;
    }
    [[nodiscard]] std::shared_ptr<api::PutCommand> send_and_receive_put_command_at_node_1() {
        return send_and_receive_put_command_at_node_1([]([[maybe_unused]] auto& cmd) noexcept {});
    }

    [[nodiscard]] std::shared_ptr<api::PutReply> respond_and_receive_put_reply_at_node_0(
            const std::shared_ptr<api::PutCommand>& cmd,
            const std::function<void(api::StorageReply&)>& reply_mutator) {
        auto reply = std::shared_ptr<api::StorageReply>(cmd->makeReply());
        reply_mutator(*reply);
        _node_1->send_response(reply);

        auto recv_reply = _node_0->wait_and_receive_single_message();
        auto recv_as_put_reply = std::dynamic_pointer_cast<api::PutReply>(recv_reply);
        assert(recv_as_put_reply);
        return recv_as_put_reply;
    }

    [[nodiscard]] std::shared_ptr<api::PutReply> respond_and_receive_put_reply_at_node_0(
            const std::shared_ptr<api::PutCommand>& cmd) {
        return respond_and_receive_put_reply_at_node_0(cmd, []([[maybe_unused]] auto& reply) noexcept {});
    }
};

StorageApiRpcServiceTest::~StorageApiRpcServiceTest() = default;

TEST_F(StorageApiRpcServiceTest, can_send_and_respond_to_request_end_to_end) {
    auto cmd = _node_0->create_dummy_put_command();
    cmd->setAddress(_node_1->node_address());
    _node_0->send_request_verify_not_bounced(cmd);

    auto recv_msg = _node_1->wait_and_receive_single_message();
    auto* put_cmd = dynamic_cast<api::PutCommand*>(recv_msg.get());
    ASSERT_TRUE(put_cmd != nullptr);
    auto reply = std::shared_ptr<api::StorageReply>(put_cmd->makeReply());
    _node_1->send_response(reply);

    auto recv_reply = _node_0->wait_and_receive_single_message();
    auto* put_reply = dynamic_cast<api::PutReply*>(recv_reply.get());
    ASSERT_TRUE(put_reply != nullptr);
}

TEST_F(StorageApiRpcServiceTest, send_to_unknown_address_bounces_with_error_reply) {
    auto cmd = _node_0->create_dummy_put_command();
    cmd->setAddress(non_existing_address());
    cmd->getTrace().setLevel(9);
    _node_0->send_request(cmd);

    auto bounced_msg = _node_0->wait_and_receive_single_message();
    auto* put_reply = dynamic_cast<api::PutReply*>(bounced_msg.get());
    ASSERT_TRUE(put_reply != nullptr);

    auto expected_code = static_cast<api::ReturnCode::Result>(mbus::ErrorCode::NO_ADDRESS_FOR_SERVICE);
    auto expected_msg = vespalib::make_string(
            "The address of service '%s' could not be resolved. It is not currently "
            "registered with the Vespa name server. "
            "The service must be having problems, or the routing configuration is wrong. "
            "Address resolution attempted from host '%s'",
            to_slobrok_id(non_existing_address()).c_str(), vespalib::HostName::get().c_str());

    EXPECT_EQ(put_reply->getResult(), api::ReturnCode(expected_code, expected_msg));
    EXPECT_THAT(put_reply->getTrace().toString(), HasSubstr("The service must be having problems"));
}

TEST_F(StorageApiRpcServiceTest, request_metadata_is_propagated_to_receiver) {
    auto recv_cmd = send_and_receive_put_command_at_node_1([](auto& cmd){
        cmd.getTrace().setLevel(7);
        cmd.setTimeout(1337s);
    });
    EXPECT_EQ(recv_cmd->getTrace().getLevel(), 7);
    EXPECT_EQ(recv_cmd->getTimeout(), 1337s);
}

TEST_F(StorageApiRpcServiceTest, response_trace_is_propagated_to_sender) {
    auto recv_cmd = send_and_receive_put_command_at_node_1([](auto& cmd){
        cmd.getTrace().setLevel(1);
    });
    auto recv_reply = respond_and_receive_put_reply_at_node_0(recv_cmd, [](auto& reply){
        reply.getTrace().trace(1, "Doing cool things", false);
    });
    auto trace_str = recv_reply->getTrace().toString();
    EXPECT_THAT(trace_str, HasSubstr("Doing cool things"));
}

TEST_F(StorageApiRpcServiceTest, response_trace_only_propagated_if_trace_level_set) {
    auto recv_cmd = send_and_receive_put_command_at_node_1();
    auto recv_reply = respond_and_receive_put_reply_at_node_0(recv_cmd, [](auto& reply){
        reply.getTrace().trace(1, "Doing cool things", false);
    });
    auto trace_str = recv_reply->getTrace().toString();
    EXPECT_THAT(trace_str, Not(HasSubstr("Doing cool things")));
}

TEST_F(StorageApiRpcServiceTest, malformed_request_header_returns_rpc_error) {
    auto& supervisor = _node_0->shared_rpc_resources().supervisor();
    auto* req = supervisor.AllocRPCRequest();
    req->SetMethodName(StorageApiRpcService::rpc_v1_method_name());
    auto* params = req->GetParams();
    params->AddInt8(0);  // No compression
    params->AddInt32(24);
    strncpy(params->AddData(24), "some non protobuf stuff", 24);
    params->AddInt8(0);  // Still no compression
    params->AddInt32(0); // Not actually valid, but we'll try to decode the header first.
    params->AddData(0);

    _node_0->send_raw_request_and_expect_error(*_node_1, req, "Unable to decode RPC request header protobuf");
}

TEST_F(StorageApiRpcServiceTest, malformed_request_payload_returns_rpc_error) {
    auto& supervisor = _node_0->shared_rpc_resources().supervisor();
    auto* req = supervisor.AllocRPCRequest();
    req->SetMethodName(StorageApiRpcService::rpc_v1_method_name());
    auto* params = req->GetParams();
    params->AddInt8(0);  // No compression
    params->AddInt32(0);
    params->AddData(0);  // This is a valid empty protobuf header with no fields set
    params->AddInt8(0);  // Even still no compression
    params->AddInt32(0); // This, however, isn't valid, since at least sizeof(uint32_t) must be present
    params->AddData(0);

    _node_0->send_raw_request_and_expect_error(*_node_1, req, "Unable to decode RPC request payload");
}

// TODO also test bad response header/payload

TEST_F(StorageApiRpcServiceTest, trace_events_are_emitted_for_send_and_receive) {
    auto recv_cmd = send_and_receive_put_command_at_node_1([](auto& cmd){
        cmd.getTrace().setLevel(9);
    });
    auto recv_reply = respond_and_receive_put_reply_at_node_0(recv_cmd);
    auto trace_str = recv_reply->getTrace().toString();
    // Ordering of traced events matter, so we use a cheeky regex.
    EXPECT_THAT(trace_str, ContainsRegex("Sending request from.+"
                                         "Request received at.+"
                                         "Sending response from.+"
                                         "Response received at"));
}

}
