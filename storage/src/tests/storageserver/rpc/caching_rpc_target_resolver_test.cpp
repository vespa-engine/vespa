// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/slobrok/imirrorapi.h>
#include <vespa/storage/storageserver/rpc/caching_rpc_target_resolver.h>
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/vdslib/state/nodetype.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace storage::rpc;
using slobrok::api::IMirrorAPI;
using storage::api::StorageMessageAddress;
using storage::lib::NodeType;

class MockMirror : public IMirrorAPI {
public:
    using Mappings = std::map<vespalib::string, IMirrorAPI::SpecList>;
    Mappings mappings;
    uint32_t gen;
    MockMirror() : mappings(), gen(1) {}
    SpecList lookup(const std::string& pattern) const override {
        auto itr = mappings.find(pattern);
        if (itr != mappings.end()) {
            return itr->second;
        }
        return {};
    }
    uint32_t updates() const override { return gen; }
    bool ready() const override { return true; }
    void inc_gen() { ++gen; }
};

class MockWrappedFrtTarget : public WrappedFrtTarget {
private:
    bool& _valid;
public:
    MockWrappedFrtTarget(bool& valid) : _valid(valid) {}
    FRT_Target* get() noexcept override { return nullptr; }
    bool is_valid() const noexcept override { return _valid; }
};

class MockTargetFactory : public RpcTargetFactory {
public:
    mutable bool valid_target;

    MockTargetFactory() : valid_target(true) {}
    std::unique_ptr<RpcTarget> make_target(const vespalib::string& connection_spec, uint32_t slobrok_gen) const override {
        return std::make_unique<RpcTarget>(std::make_unique<MockWrappedFrtTarget>(valid_target),
                connection_spec, slobrok_gen);
    }
};

class CachingRpcTargetResolverTest : public ::testing::Test {
public:
    MockMirror mirror;
    MockTargetFactory factory;
    CachingRpcTargetResolver resolver;
    StorageMessageAddress address_0;
    StorageMessageAddress address_1;
    vespalib::string spec_0;
    vespalib::string spec_1;

    CachingRpcTargetResolverTest()
        : mirror(),
          factory(),
          resolver(mirror, factory),
          address_0("my_cluster", NodeType::STORAGE, 5),
          address_1("my_cluster", NodeType::DISTRIBUTOR, 7),
          spec_0("tcp/my:41"),
          spec_1("tcp/my:42")
    {
        add_mapping(address_0, spec_0);
    }
    void add_mapping(const StorageMessageAddress& address, const vespalib::string& connection_spec) {
        mirror.mappings[to_slobrok_id(address)] = {{to_slobrok_id(address), connection_spec}};
    }
    static vespalib::string to_slobrok_id(const storage::api::StorageMessageAddress& address) {
        return CachingRpcTargetResolver::address_to_slobrok_id(address);
    }
};

TEST_F(CachingRpcTargetResolverTest, converts_storage_message_address_to_slobrok_id)
{
    EXPECT_EQ("storage/cluster.my_cluster/storage/5", to_slobrok_id(address_0));
    EXPECT_EQ("storage/cluster.my_cluster/distributor/7", to_slobrok_id(address_1));
}

TEST_F(CachingRpcTargetResolverTest, resolves_rpc_target_and_caches_result)
{
    auto target_a = resolver.resolve_rpc_target(address_0);
    ASSERT_TRUE(target_a);
    EXPECT_EQ(spec_0, target_a->_spec);
    EXPECT_EQ(1, target_a->_slobrok_gen);
    auto target_b = resolver.resolve_rpc_target(address_0);
    ASSERT_TRUE(target_b);
    EXPECT_EQ(target_a.get(), target_b.get());
    EXPECT_EQ(spec_0, target_b->_spec);
    EXPECT_EQ(1, target_b->_slobrok_gen);
}

TEST_F(CachingRpcTargetResolverTest, cached_rpc_target_is_updated_when_slobrok_generation_changes)
{
    auto target_a = resolver.resolve_rpc_target(address_0);
    mirror.inc_gen();
    auto target_b = resolver.resolve_rpc_target(address_0);
    EXPECT_EQ(target_a.get(), target_b.get());
    EXPECT_EQ(2, target_b->_slobrok_gen);
}

TEST_F(CachingRpcTargetResolverTest, new_rpc_target_is_created_if_connection_spec_changes)
{
    auto target_a = resolver.resolve_rpc_target(address_0);
    add_mapping(address_0, spec_1);
    mirror.inc_gen();
    auto target_b = resolver.resolve_rpc_target(address_0);
    EXPECT_NE(target_a.get(), target_b.get());
    EXPECT_EQ(spec_1, target_b->_spec);
    EXPECT_EQ(2, target_b->_slobrok_gen);
}

TEST_F(CachingRpcTargetResolverTest, new_rpc_target_is_created_if_raw_target_is_invalid)
{
    auto target_a = resolver.resolve_rpc_target(address_0);
    factory.valid_target = false;
    auto target_b = resolver.resolve_rpc_target(address_0);
    EXPECT_NE(target_a.get(), target_b.get());
    EXPECT_EQ(spec_0, target_b->_spec);
    EXPECT_EQ(1, target_b->_slobrok_gen);
}

TEST_F(CachingRpcTargetResolverTest, null_rpc_target_is_returned_if_slobrok_id_is_not_found)
{
    auto target = resolver.resolve_rpc_target(address_1);
    EXPECT_FALSE(target);
}
