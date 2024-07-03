// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "slime_cluster_state_bundle_codec.h"
#include <vespa/config-stor-distribution.h>
#include <vespa/config/common/misc.h>
#include <vespa/config/configgen/configpayload.h>
#include <vespa/config/print/configdatabuffer.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/data/slime/array_traverser.h>
#include <vespa/vespalib/data/slime/object_traverser.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/size_literals.h>

using document::FixedBucketSpaces;
using vespalib::slime::Cursor;
using vespalib::slime::BinaryFormat;
using vespalib::DataBuffer;
using vespalib::ConstBufferRef;
using vespalib::compression::CompressionConfig;
using vespalib::compression::decompress;
using vespalib::compression::compress;
using vespalib::Memory;
using DistributionConfigBuilder = storage::lib::Distribution::DistributionConfigBuilder;
using namespace vespalib::slime;

namespace storage::rpc {

// TODO find a suitable home for this class to avoid dupes with rpcsendv2.cpp
namespace {
class OutputBuf : public vespalib::Output {
public:
    explicit OutputBuf(size_t estimatedSize) : _buf(estimatedSize) { }
    ~OutputBuf() override;
    vespalib::DataBuffer & getBuf() { return _buf; }
private:
    vespalib::WritableMemory reserve(size_t bytes) override {
        _buf.ensureFree(bytes);
        return {_buf.getFree(), _buf.getFreeLen()};
    }
    Output &commit(size_t bytes) override {
        _buf.moveFreeToData(bytes);
        return *this;
    }
    vespalib::DataBuffer _buf;
};

OutputBuf::~OutputBuf() = default;

vespalib::string serialize_state(const lib::ClusterState& state) {
    vespalib::asciistream as;
    state.serialize(as);
    return as.str();
}

const Memory BaselineField("baseline");
const Memory BlockFeedInClusterField("block-feed-in-cluster");
const Memory DeferredActivationField("deferred-activation");
const Memory DescriptionField("description");
const Memory DistributionConfigField("distribution-config");
const Memory FeedBlockField("feed-block");
const Memory SpacesField("spaces");
const Memory StatesField("states");

// Important: these conversion routines are NOT complete and NOT general! They are only to be used
// by code transitively used by unit tests that expect a particular type subset and "shape" of config.

void convert_struct(const Inspector& in, Cursor& out);

struct ConfigArrayConverter : ArrayTraverser {
    Cursor& _out;
    explicit ConfigArrayConverter(Cursor& out) noexcept: _out(out) {}

    void entry([[maybe_unused]] size_t idx, const Inspector& in) override {
        assert(in.type().getId() == OBJECT::ID);
        auto type = in["type"].asString();
        auto& value = in["value"];
        assert(value.valid());
        if (type == "int") {
            _out.addLong(value.asLong());
        } else if (type == "bool") {
            _out.addBool(value.asBool());
        } else if (type == "string") {
            _out.addString(value.asString());
        } else if (type == "double") {
            _out.addDouble(value.asDouble());
        } else if (type == "array") {
            assert(value.type().getId() == ARRAY::ID);
            ConfigArrayConverter arr_conv(_out.addArray());
            value.traverse(arr_conv);
        } else if (type == "struct") {
            convert_struct(value, _out.addObject());
        } else {
            fprintf(stderr, "unknown array entry type '%s'\n", type.make_string().c_str());
            abort();
        }
    }
};

struct ConfigObjectConverter : ObjectTraverser {
    Cursor& _out;
    explicit ConfigObjectConverter(Cursor& out) noexcept: _out(out) {}

    void field(const Memory& name, const Inspector& in) override {
        assert(in.type().getId() == OBJECT::ID);
        auto type = in["type"].asString();
        auto& value = in["value"];
        assert(value.valid());
        if (type == "int") {
            _out.setLong(name, value.asLong());
        } else if (type == "bool") {
            _out.setBool(name, value.asBool());
        } else if (type == "string") {
            _out.setString(name, value.asString());
        } else if (type == "double") {
            _out.setDouble(name, value.asDouble());
        } else if (type == "array") {
            assert(value.type().getId() == ARRAY::ID);
            ConfigArrayConverter arr_conv(_out.setArray(name));
            value.traverse(arr_conv);
        } else if (type == "struct") {
            convert_struct(value, _out.setObject(name));
        } else {
            fprintf(stderr, "unknown struct entry type '%s'\n", type.make_string().c_str());
            abort();
        }
    }
};

void convert_struct(const Inspector& in, Cursor& out) {
    ConfigObjectConverter conv(out);
    in.traverse(conv);
}

void convert_to_config_payload(const Inspector& in, Cursor& out) {
    convert_struct(in["configPayload"], out);
}

} // anon ns

// Only used from unit tests; the cluster controller encodes all bundles
// we decode in practice.
EncodedClusterStateBundle SlimeClusterStateBundleCodec::encode(const lib::ClusterStateBundle& bundle) const {
    vespalib::Slime slime;
    Cursor& root = slime.setObject();
    if (bundle.deferredActivation()) {
        root.setBool(DeferredActivationField, bundle.deferredActivation());
    }
    Cursor& states = root.setObject(StatesField);
    states.setString(BaselineField, serialize_state(*bundle.getBaselineClusterState()));
    Cursor& spaces = states.setObject(SpacesField);
    for (const auto& sp : bundle.getDerivedClusterStates()) {
        spaces.setString(FixedBucketSpaces::to_string(sp.first), serialize_state(*sp.second));
    }
    // We only encode feed block state if the cluster is actually blocked.
    if (bundle.block_feed_in_cluster()) {
        Cursor& feed_block = root.setObject(FeedBlockField);
        feed_block.setBool(BlockFeedInClusterField, true);
        feed_block.setString(DescriptionField, bundle.feed_block()->description());
    }

    if (bundle.has_distribution_config()) {
        Cursor& distr_root = root.setObject(DistributionConfigField);
        ::config::ConfigDataBuffer buf;
        bundle.distribution_config_bundle()->config().serialize(buf);
        // There is no way in C++ to directly serialize to the actual payload format we expect to
        // deserialize, so we have to manually convert the type-annotated config snapshot :I
        convert_to_config_payload(buf.slimeObject().get(), distr_root);
    }

    OutputBuf out_buf(4_Ki);
    BinaryFormat::encode(slime, out_buf);
    ConstBufferRef to_compress(out_buf.getBuf().getData(), out_buf.getBuf().getDataLen());
    auto buf = std::make_unique<DataBuffer>(vespalib::roundUp2inN(out_buf.getBuf().getDataLen()));
    auto actual_type = compress(CompressionConfig::LZ4, to_compress, *buf, false);

    EncodedClusterStateBundle encoded_bundle;
    encoded_bundle._compression_type = actual_type;
    assert(to_compress.size() <= INT32_MAX);
    encoded_bundle._uncompressed_length = to_compress.size();
    encoded_bundle._buffer = std::move(buf);
    return encoded_bundle;
}

namespace {


struct StateInserter : vespalib::slime::ObjectTraverser {
    lib::ClusterStateBundle::BucketSpaceStateMapping& _space_states;

    explicit StateInserter(lib::ClusterStateBundle::BucketSpaceStateMapping& space_states)
        : _space_states(space_states) {}

    void field(const Memory& symbol, const Inspector& inspector) override {
        _space_states.emplace(FixedBucketSpaces::from_string(symbol.make_stringview()),
                              std::make_shared<const lib::ClusterState>(inspector.asString().make_string()));
    }
};

}

std::shared_ptr<const lib::ClusterStateBundle> SlimeClusterStateBundleCodec::decode(
        const EncodedClusterStateBundle& encoded_bundle) const
{
    ConstBufferRef blob(encoded_bundle._buffer->getData(), encoded_bundle._buffer->getDataLen());
    DataBuffer uncompressed;
    decompress(encoded_bundle._compression_type, encoded_bundle._uncompressed_length,
               blob, uncompressed, false);
    if (encoded_bundle._uncompressed_length != uncompressed.getDataLen()) {
        throw std::range_error(vespalib::make_string("ClusterStateBundle indicated uncompressed size (%u) is "
                                                     "not equal to actual uncompressed size (%zu)",
                                                     encoded_bundle._uncompressed_length,
                                                     uncompressed.getDataLen()));
    }

    vespalib::Slime slime;
    BinaryFormat::decode(Memory(uncompressed.getData(), uncompressed.getDataLen()), slime);
    Inspector& root = slime.get();
    Inspector& states = root[StatesField];
    auto baseline = std::make_shared<lib::ClusterState>(states[BaselineField].asString().make_string());

    Inspector& spaces = states[SpacesField];
    lib::ClusterStateBundle::BucketSpaceStateMapping space_states;
    StateInserter inserter(space_states);
    spaces.traverse(inserter);

    const bool deferred_activation = root[DeferredActivationField].asBool(); // Defaults to false if not set.
    std::shared_ptr<const lib::DistributionConfigBundle> distribution_config;
    std::optional<lib::ClusterStateBundle::FeedBlock> feed_block;

    Inspector& fb = root[FeedBlockField];
    if (fb.valid()) {
        feed_block = lib::ClusterStateBundle::FeedBlock(fb[BlockFeedInClusterField].asBool(),
                                                        fb[DescriptionField].asString().make_string());
    }
    Inspector& dc = root[DistributionConfigField];
    if (dc.valid()) {
        auto raw_cfg = std::make_unique<DistributionConfigBuilder>(::config::ConfigPayload(dc));
        distribution_config = lib::DistributionConfigBundle::of(std::move(raw_cfg));
    }
    return std::make_shared<lib::ClusterStateBundle>(std::move(baseline), std::move(space_states), std::move(feed_block),
                                                     std::move(distribution_config), deferred_activation);
}

}
