// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/storage/storageserver/fnetlistener.h>
#include <vespa/storage/storageserver/message_enqueuer.h>
#include <vespa/storage/storageserver/rpcrequestwrapper.h>
#include <vespa/storage/storageserver/slime_cluster_state_bundle_codec.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vdstestlib/cppunit/dirconfig.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <tests/common/testhelper.h>
#include <vector>

namespace storage {

using document::FixedBucketSpaces;

class FNetListenerTest : public CppUnit::TestFixture {
public:
    CPPUNIT_TEST_SUITE(FNetListenerTest);
    CPPUNIT_TEST(baseline_set_distribution_states_rpc_enqueues_command_with_state_bundle);
    CPPUNIT_TEST(set_distribution_states_rpc_with_derived_enqueues_command_with_state_bundle);
    CPPUNIT_TEST(compressed_bundle_is_transparently_uncompressed);
    CPPUNIT_TEST(set_distribution_rpc_is_immediately_failed_if_listener_is_closed);
    CPPUNIT_TEST(overly_large_uncompressed_bundle_size_parameter_returns_rpc_error);
    CPPUNIT_TEST(mismatching_uncompressed_bundle_size_parameter_returns_rpc_error);
    CPPUNIT_TEST_SUITE_END();

    void baseline_set_distribution_states_rpc_enqueues_command_with_state_bundle();
    void set_distribution_states_rpc_with_derived_enqueues_command_with_state_bundle();
    void compressed_bundle_is_transparently_uncompressed();
    void set_distribution_rpc_is_immediately_failed_if_listener_is_closed();
    void overly_large_uncompressed_bundle_size_parameter_returns_rpc_error();
    void mismatching_uncompressed_bundle_size_parameter_returns_rpc_error();
};

CPPUNIT_TEST_SUITE_REGISTRATION(FNetListenerTest);

namespace {

struct MockOperationEnqueuer : MessageEnqueuer {
    std::vector<std::shared_ptr<api::StorageMessage>> _enqueued;

    void enqueue(std::shared_ptr<api::StorageMessage> msg) override {
        _enqueued.emplace_back(std::move(msg));
    }
};

struct DummyReturnHandler : FRT_IReturnHandler {
    void HandleReturn() override {}
    FNET_Connection* GetConnection() override { return nullptr; }
};

struct Fixture {
    // TODO factor out Slobrok code to avoid need to set up live ports for unrelated tests
    mbus::Slobrok slobrok;
    vdstestlib::DirConfig config;
    MockOperationEnqueuer enqueuer;
    std::unique_ptr<FNetListener> fnet_listener;
    SlimeClusterStateBundleCodec codec;
    DummyReturnHandler return_handler;
    bool request_is_detached{false};
    FRT_RPCRequest* bound_request{nullptr};

    Fixture() : config(getStandardConfig(true)) {
        config.getConfig("stor-server").set("node_index", "1");
        addSlobrokConfig(config, slobrok);
        fnet_listener = std::make_unique<FNetListener>(enqueuer, config.getConfigId(), 0);
    }

    ~Fixture() {
        // Must destroy any associated message contexts that may have refs to FRT_Request
        // instance _before_ we destroy the request itself.
        enqueuer._enqueued.clear();
        if (bound_request) {
            bound_request->SubRef();
        }
    }

    void bind_request_params(EncodedClusterStateBundle& encoded_bundle, uint32_t uncompressed_length) {
        bound_request = new FRT_RPCRequest(); // Naked new isn't pretty, but FRT_RPCRequest has internal refcounting
        auto* params = bound_request->GetParams();
        params->AddInt8(static_cast<uint8_t>(encoded_bundle._compression_type));
        params->AddInt32(uncompressed_length);
        const auto buf_len = encoded_bundle._buffer->getDataLen();
        params->AddData(encoded_bundle._buffer->stealBuffer(), buf_len);

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
        CPPUNIT_ASSERT(bound_request != nullptr);
        CPPUNIT_ASSERT(request_is_detached);
        CPPUNIT_ASSERT_EQUAL(size_t(1), enqueuer._enqueued.size());
        auto& state_request = dynamic_cast<const api::SetSystemStateCommand&>(*enqueuer._enqueued[0]);
        CPPUNIT_ASSERT_EQUAL(expectedBundle, state_request.getClusterStateBundle());
    }

    void assert_request_received_and_propagated(const lib::ClusterStateBundle& bundle) {
        create_request(bundle);
        fnet_listener->RPC_setDistributionStates(bound_request);
        assert_enqueued_operation_has_bundle(bundle);
    }

    void assert_request_returns_error_response(RPCRequestWrapper::ErrorCode error_code) {
        fnet_listener->RPC_setDistributionStates(bound_request);
        CPPUNIT_ASSERT(!request_is_detached);
        CPPUNIT_ASSERT(bound_request->IsError());
        CPPUNIT_ASSERT_EQUAL(static_cast<uint32_t>(error_code), bound_request->GetErrorCode());
    }

    lib::ClusterStateBundle dummy_baseline_bundle() const {
        return lib::ClusterStateBundle(lib::ClusterState("version:123 distributor:3 storage:3"));
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
                                 ss.str().c_str(), ss.str().c_str());
}

}

void FNetListenerTest::baseline_set_distribution_states_rpc_enqueues_command_with_state_bundle() {
    Fixture f;
    auto baseline = f.dummy_baseline_bundle();

    f.assert_request_received_and_propagated(baseline);
}

void FNetListenerTest::set_distribution_states_rpc_with_derived_enqueues_command_with_state_bundle() {
    Fixture f;
    lib::ClusterStateBundle spaces_bundle(
            lib::ClusterState("version:123 distributor:3 storage:3"),
            {{FixedBucketSpaces::default_space(), state_of("version:123 distributor:3 storage:3 .0.s:d")},
             {FixedBucketSpaces::global_space(), state_of("version:123 distributor:3 .1.s:d storage:3")}});

    f.assert_request_received_and_propagated(spaces_bundle);
}

void FNetListenerTest::compressed_bundle_is_transparently_uncompressed() {
    Fixture f;
    auto state_str = make_compressable_state_string();
    lib::ClusterStateBundle compressable_bundle{lib::ClusterState(state_str)};

    f.create_request(compressable_bundle);
    // First verify that the bundle is sent in compressed form
    CPPUNIT_ASSERT(f.bound_request->GetParams()->GetValue(2)._data._len < state_str.size());
    // Ensure we uncompress it to the original form
    f.fnet_listener->RPC_setDistributionStates(f.bound_request);
    f.assert_enqueued_operation_has_bundle(compressable_bundle);
}

void FNetListenerTest::set_distribution_rpc_is_immediately_failed_if_listener_is_closed() {
    Fixture f;
    f.create_request(f.dummy_baseline_bundle());
    f.fnet_listener->close();
    f.assert_request_returns_error_response(RPCRequestWrapper::ERR_NODE_SHUTTING_DOWN);
}

void FNetListenerTest::overly_large_uncompressed_bundle_size_parameter_returns_rpc_error() {
    Fixture f;
    auto encoded_bundle = f.codec.encode(f.dummy_baseline_bundle());
    f.bind_request_params(encoded_bundle, FNetListener::StateBundleMaxUncompressedSize + 1);
    f.assert_request_returns_error_response(RPCRequestWrapper::ERR_BAD_REQUEST);
}

void FNetListenerTest::mismatching_uncompressed_bundle_size_parameter_returns_rpc_error() {
    Fixture f;
    auto encoded_bundle = f.codec.encode(f.dummy_baseline_bundle());
    f.bind_request_params(encoded_bundle, encoded_bundle._buffer->getDataLen() + 100);
    f.assert_request_returns_error_response(RPCRequestWrapper::ERR_BAD_REQUEST);
}

}
