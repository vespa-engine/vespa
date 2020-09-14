// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocman.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/documentapi/loadtypes/loadtypeset.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/slobrok/sbmirror.h>
#include <vespa/storage/storageserver/rpc/storage_api_rpc_service.h>
#include <vespa/storage/storageserver/rpc/shared_rpc_resources.h>
#include <vespa/storage/storageserver/rpc/message_codec_provider.h>
#include <vespa/storage/storageserver/rpc/caching_rpc_target_resolver.h>
#include <vespa/storage/storageserver/communicationmanager.h>
#include <vespa/storage/storageserver/rpcrequestwrapper.h>
#include <vespa/storage/storageserver/message_dispatcher.h>
#include <vespa/storageapi/message/persistence.h>
#include <tests/common/testhelper.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <condition_variable>
#include <deque>
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
    return {"coolcluster", (is_distributor ? lib::NodeType::DISTRIBUTOR : lib::NodeType::STORAGE), node_index};
}

vespalib::string to_slobrok_id(const api::StorageMessageAddress& address) {
    // TODO factor out slobrok ID generation code to be independent of resolver?
    return CachingRpcTargetResolver::address_to_slobrok_id(address);
}

class StorageApiNode {
    vdstestlib::DirConfig                             _config;
    std::shared_ptr<const document::DocumentTypeRepo> _doc_type_repo;
    std::shared_ptr<const documentapi::LoadTypeSet>   _load_type_set;
    LockingMockOperationDispatcher                    _messages;
    std::unique_ptr<MessageCodecProvider>             _codec_provider;
    std::unique_ptr<SharedRpcResources>               _shared_rpc_resources;
    std::unique_ptr<StorageApiRpcService>             _service;
    api::StorageMessageAddress                        _node_address;
    vespalib::string                                  _slobrok_id;
public:
    StorageApiNode(uint16_t node_index, bool is_distributor, const mbus::Slobrok& slobrok)
        : _config(getStandardConfig(true)),
          _doc_type_repo(document::TestDocRepo().getTypeRepoSp()),
          _load_type_set(std::make_shared<documentapi::LoadTypeSet>()),
          _node_address(make_address(node_index, is_distributor)),
          _slobrok_id(to_slobrok_id(_node_address))
    {
        auto& cfg = _config.getConfig("stor-server");
        cfg.set("node_index", std::to_string(node_index));
        cfg.set("is_distributor", is_distributor ? "true" : "false");
        addSlobrokConfig(_config, slobrok);

        _shared_rpc_resources = std::make_unique<SharedRpcResources>(_config.getConfigId(), 0, 1);
        // TODO make codec provider into interface so we can test decode-failures more easily?
        _codec_provider = std::make_unique<MessageCodecProvider>(_doc_type_repo, _load_type_set);
        _service = std::make_unique<StorageApiRpcService>(_messages, *_shared_rpc_resources, *_codec_provider);

        _shared_rpc_resources->start_server_and_register_slobrok(_slobrok_id);
        // Explicitly wait until we are visible in Slobrok. Just waiting for mirror readiness is not enough.
        wait_until_visible_in_slobrok(_slobrok_id);
    }

    void wait_until_visible_in_slobrok(vespalib::stringref id) {
        while (_shared_rpc_resources->slobrok_mirror().lookup(id).empty()) {
            std::this_thread::sleep_for(10ms); // TODO timeout handling
        }
    }

    const api::StorageMessageAddress& node_address() const noexcept { return _node_address; }

    std::shared_ptr<api::PutCommand> create_dummy_put_command() const {
        auto doc_type = _doc_type_repo->getDocumentType("testdoctype1");
        auto doc = std::make_shared<document::Document>(*doc_type, document::DocumentId("id:foo:testdoctype1::bar"));
        doc->setFieldValue(doc->getField("hstringval"), std::make_unique<document::StringFieldValue>("hello world"));
        return std::make_shared<api::PutCommand>(makeDocumentBucket(document::BucketId(0)), std::move(doc), 100);
    }

    void send_request(std::shared_ptr<api::StorageCommand> req) {
        ASSERT_TRUE(_messages.empty());
        _service->send_rpc_v1_request(std::move(req));
        ASSERT_TRUE(_messages.empty()); // If non-empty, request was bounced (Slobrok lookup failed)
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
};

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
};

StorageApiRpcServiceTest::~StorageApiRpcServiceTest() = default;

TEST_F(StorageApiRpcServiceTest, can_send_and_respond_to_request_end_to_end) {
    auto cmd = _node_0->create_dummy_put_command();
    cmd->setAddress(_node_1->node_address());
    ASSERT_NO_FATAL_FAILURE(_node_0->send_request(cmd));

    auto recv_msg = _node_1->wait_and_receive_single_message();
    auto* put_cmd = dynamic_cast<api::PutCommand*>(recv_msg.get());
    ASSERT_TRUE(put_cmd != nullptr);
    auto reply = std::shared_ptr<api::StorageReply>(put_cmd->makeReply());
    _node_1->send_response(reply);

    auto recv_reply = _node_0->wait_and_receive_single_message();
    auto* put_reply = dynamic_cast<api::PutReply*>(recv_reply.get());
    ASSERT_TRUE(put_reply != nullptr);
}

}
