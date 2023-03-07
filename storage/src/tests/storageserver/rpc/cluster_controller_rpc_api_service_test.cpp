// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/storage/storageserver/message_dispatcher.h>
#include <vespa/storage/storageserver/rpcrequestwrapper.h>
#include <vespa/storage/storageserver/rpc/cluster_controller_api_rpc_service.h>
#include <vespa/storage/storageserver/rpc/shared_rpc_resources.h>
#include <vespa/storage/storageserver/rpc/slime_cluster_state_bundle_codec.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <tests/common/testhelper.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vector>

namespace storage::rpc {

using document::FixedBucketSpaces;
using namespace ::testing;

struct ClusterControllerApiRpcServiceTest : Test {
};

namespace {

struct MockOperationDispatcher : MessageDispatcher {
    std::vector<std::shared_ptr<api::StorageMessage>> _enqueued;

    void dispatch_sync(std::shared_ptr<api::StorageMessage> msg) override {
        _enqueued.emplace_back(std::move(msg));
    }
    void dispatch_async(std::shared_ptr<api::StorageMessage> msg) override {
        _enqueued.emplace_back(std::move(msg));
    }
};

struct DummyReturnHandler : FRT_IReturnHandler {
    void HandleReturn() override {}
    FNET_Connection* GetConnection() override { return nullptr; }
};

struct FixtureBase {
    mbus::Slobrok slobrok;
    vdstestlib::DirConfig config;
    MockOperationDispatcher dispatcher;
    std::unique_ptr<SharedRpcResources> shared_rpc_resources;
    std::unique_ptr<ClusterControllerApiRpcService> cc_service;
    DummyReturnHandler return_handler;
    bool request_is_detached{false};
    FRT_RPCRequest* bound_request{nullptr};

    FixtureBase()
        : config(getStandardConfig(true))
    {
        config.getConfig("stor-server").set("node_index", "1");
        addSlobrokConfig(config, slobrok);

        shared_rpc_resources = std::make_unique<SharedRpcResources>(config::ConfigUri(config.getConfigId()), 0, 1, 1);
        cc_service = std::make_unique<ClusterControllerApiRpcService>(dispatcher, *shared_rpc_resources);
        shared_rpc_resources->start_server_and_register_slobrok("my_cool_rpc_test");
    }

    virtual ~FixtureBase() {
        // Must destroy any associated message contexts that may have refs to FRT_Request
        // instance _before_ we destroy the request itself.
        dispatcher._enqueued.clear();
        if (bound_request) {
            bound_request->internal_subref();
        }
    }
};

struct SetStateFixture : FixtureBase {
    SlimeClusterStateBundleCodec codec;

    SetStateFixture() : FixtureBase() {}

    void bind_request_params(EncodedClusterStateBundle& encoded_bundle, uint32_t uncompressed_length) {
        bound_request = new FRT_RPCRequest(); // Naked new isn't pretty, but FRT_RPCRequest has internal refcounting
        auto* params = bound_request->GetParams();
        params->AddInt8(static_cast<uint8_t>(encoded_bundle._compression_type));
        params->AddInt32(uncompressed_length);
        params->AddData(std::move(*encoded_bundle._buffer));

        bound_request->SetDetachedPT(&request_is_detached);
        bound_request->SetReturnHandler(&return_handler);
    }

    void create_request(const lib::ClusterStateBundle& bundle) {
        // Only 1 request allowed per fixture due to lifetime handling snags
        assert(bound_request == nullptr);
        auto encoded_bundle = codec.encode(bundle);
        bind_request_params(encoded_bundle, encoded_bundle._uncompressed_length);
    }

    void assert_enqueued_operation_has_bundle(const lib::ClusterStateBundle& expectedBundle) {
        ASSERT_TRUE(bound_request != nullptr);
        ASSERT_TRUE(request_is_detached);
        ASSERT_EQ(1, dispatcher._enqueued.size());
        auto& state_request = dynamic_cast<const api::SetSystemStateCommand&>(*dispatcher._enqueued[0]);
        ASSERT_EQ(expectedBundle, state_request.getClusterStateBundle());
    }

    void assert_request_received_and_propagated(const lib::ClusterStateBundle& bundle) {
        create_request(bundle);
        cc_service->RPC_setDistributionStates(bound_request);
        assert_enqueued_operation_has_bundle(bundle);
    }

    void assert_request_returns_error_response(RPCRequestWrapper::ErrorCode error_code) {
        cc_service->RPC_setDistributionStates(bound_request);
        ASSERT_FALSE(request_is_detached);
        ASSERT_TRUE(bound_request->IsError());
        ASSERT_EQ(static_cast<uint32_t>(error_code), bound_request->GetErrorCode());
    }

    static lib::ClusterStateBundle dummy_baseline_bundle() {
        return lib::ClusterStateBundle(lib::ClusterState("version:123 distributor:3 storage:3"));
    }

    static lib::ClusterStateBundle dummy_baseline_bundle_with_deferred_activation(bool deferred) {
        return lib::ClusterStateBundle(lib::ClusterState("version:123 distributor:3 storage:3"), {}, deferred);
    }
};

std::shared_ptr<const lib::ClusterState> state_of(vespalib::stringref state) {
    return std::make_shared<const lib::ClusterState>(state);
}

vespalib::string make_compressable_state_string() {
    vespalib::asciistream ss;
    for (int i = 0; i < 99; ++i) {
        ss << " ." << i << ".s:d";
    }
    return vespalib::make_string("version:123 distributor:100%s storage:100%s",
                                 ss.str().data(), ss.str().data());
}

} // anon namespace

TEST_F(ClusterControllerApiRpcServiceTest, baseline_set_distribution_states_rpc_enqueues_command_with_state_bundle) {
    SetStateFixture f;
    auto baseline = f.dummy_baseline_bundle();

    f.assert_request_received_and_propagated(baseline);
}

TEST_F(ClusterControllerApiRpcServiceTest, set_distribution_states_rpc_with_derived_enqueues_command_with_state_bundle) {
    SetStateFixture f;
    lib::ClusterStateBundle spaces_bundle(
            lib::ClusterState("version:123 distributor:3 storage:3"),
            {{FixedBucketSpaces::default_space(), state_of("version:123 distributor:3 storage:3 .0.s:d")},
             {FixedBucketSpaces::global_space(), state_of("version:123 distributor:3 .1.s:d storage:3")}});

    f.assert_request_received_and_propagated(spaces_bundle);
}

TEST_F(ClusterControllerApiRpcServiceTest, set_distribution_states_rpc_with_feed_block_state) {
    SetStateFixture f;
    lib::ClusterStateBundle bundle(
            lib::ClusterState("version:123 distributor:3 storage:3"), {},
            lib::ClusterStateBundle::FeedBlock(true, "full disk"), true);

    f.assert_request_received_and_propagated(bundle);
}

TEST_F(ClusterControllerApiRpcServiceTest, compressed_bundle_is_transparently_uncompressed) {
    SetStateFixture f;
    auto state_str = make_compressable_state_string();
    lib::ClusterStateBundle compressable_bundle{lib::ClusterState(state_str)};

    f.create_request(compressable_bundle);
    // First verify that the bundle is sent in compressed form
    ASSERT_LT(f.bound_request->GetParams()->GetValue(2)._data._len, state_str.size());
    // Ensure we uncompress it to the original form
    f.cc_service->RPC_setDistributionStates(f.bound_request);
    f.assert_enqueued_operation_has_bundle(compressable_bundle);
}

TEST_F(ClusterControllerApiRpcServiceTest, set_distribution_rpc_is_immediately_failed_if_listener_is_closed) {
    SetStateFixture f;
    f.create_request(f.dummy_baseline_bundle());
    f.cc_service->close();
    f.assert_request_returns_error_response(RPCRequestWrapper::ERR_NODE_SHUTTING_DOWN);
}

TEST_F(ClusterControllerApiRpcServiceTest, overly_large_uncompressed_bundle_size_parameter_returns_rpc_error) {
    SetStateFixture f;
    auto encoded_bundle = f.codec.encode(f.dummy_baseline_bundle());
    f.bind_request_params(encoded_bundle, ClusterControllerApiRpcService::StateBundleMaxUncompressedSize + 1);
    f.assert_request_returns_error_response(RPCRequestWrapper::ERR_BAD_REQUEST);
}

TEST_F(ClusterControllerApiRpcServiceTest, mismatching_uncompressed_bundle_size_parameter_returns_rpc_error) {
    SetStateFixture f;
    auto encoded_bundle = f.codec.encode(f.dummy_baseline_bundle());
    f.bind_request_params(encoded_bundle, encoded_bundle._buffer->getDataLen() + 100);
    f.assert_request_returns_error_response(RPCRequestWrapper::ERR_BAD_REQUEST);
}

TEST_F(ClusterControllerApiRpcServiceTest, true_deferred_activation_flag_can_be_roundtrip_encoded) {
    SetStateFixture f;
    f.assert_request_received_and_propagated(f.dummy_baseline_bundle_with_deferred_activation(true));

}

TEST_F(ClusterControllerApiRpcServiceTest, false_deferred_activation_flag_can_be_roundtrip_encoded) {
    SetStateFixture f;
    f.assert_request_received_and_propagated(f.dummy_baseline_bundle_with_deferred_activation(false));
}

struct ActivateStateFixture : FixtureBase {
    ActivateStateFixture() : FixtureBase() {}

    void bind_request_params(uint32_t activate_version) {
        bound_request = new FRT_RPCRequest(); // Naked new isn't pretty, but FRT_RPCRequest has internal refcounting
        auto* params = bound_request->GetParams();
        params->AddInt32(activate_version);

        bound_request->SetDetachedPT(&request_is_detached);
        bound_request->SetReturnHandler(&return_handler);
    }

    void create_request(uint32_t activate_version) {
        // Only 1 request allowed per fixture due to lifetime handling snags
        assert(bound_request == nullptr);
        bind_request_params(activate_version);
    }

    void assert_enqueued_operation_has_activate_version(uint32_t version) {
        ASSERT_TRUE(bound_request != nullptr);
        ASSERT_TRUE(request_is_detached);
        ASSERT_EQ(1, dispatcher._enqueued.size());
        auto& state_request = dynamic_cast<const api::ActivateClusterStateVersionCommand&>(*dispatcher._enqueued[0]);
        ASSERT_EQ(version, state_request.version());
    }

    void assert_request_received_and_propagated(uint32_t activate_version) {
        create_request(activate_version);
        cc_service->RPC_activateClusterStateVersion(bound_request);
        assert_enqueued_operation_has_activate_version(activate_version);
    }
};

TEST_F(ClusterControllerApiRpcServiceTest, activate_cluster_state_version_rpc_enqueues_command_with_version) {
    ActivateStateFixture f;
    f.assert_request_received_and_propagated(1234567);
}

}
