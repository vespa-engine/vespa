// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/value_type.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attribute_header.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("attribute_header_test");

using namespace search;
using namespace search::attribute;

using HnswIPO = std::optional<HnswIndexParams>;
using vespalib::eval::ValueType;

const Config tensor_cfg(BasicType::TENSOR, CollectionType::SINGLE);
const vespalib::string file_name = "my_file_name";
const ValueType tensor_type = ValueType::from_spec("tensor<float>(x[4])");
constexpr uint32_t num_docs = 23;
constexpr uint64_t unique_value_count = 11;
constexpr uint64_t total_value_count = 13;
constexpr uint64_t create_serial_num = 17;
constexpr uint32_t version = 19;

vespalib::GenericHeader
populate_header(const HnswIPO& hnsw_params)
{
    AttributeHeader header(file_name,
                           tensor_cfg.basicType(),
                           tensor_cfg.collectionType(),
                           tensor_type,
                           false,
                           PersistentPredicateParams(),
                           hnsw_params,
                           num_docs,
                           unique_value_count,
                           total_value_count,
                           create_serial_num,
                           version);

    vespalib::GenericHeader result;
    header.addTags(result);
    return result;
}

void
verify_roundtrip_serialization(const HnswIPO& hnsw_params_in)
{
    auto gen_header = populate_header(hnsw_params_in);
    auto attr_header = AttributeHeader::extractTags(gen_header, file_name);

    EXPECT_EQ(tensor_cfg.basicType(), attr_header.getBasicType());
    EXPECT_EQ(tensor_cfg.collectionType(), attr_header.getCollectionType());
    EXPECT_EQ(tensor_type, attr_header.getTensorType());
    EXPECT_EQ(num_docs, attr_header.getNumDocs());
    EXPECT_EQ(create_serial_num, attr_header.getCreateSerialNum());
    EXPECT_EQ(total_value_count, attr_header.get_total_value_count());
    EXPECT_EQ(unique_value_count, attr_header.get_unique_value_count());
    EXPECT_EQ(version, attr_header.getVersion());
    EXPECT_EQ(false, attr_header.getPredicateParamsSet());
    const auto& hnsw_params_out = attr_header.get_hnsw_index_params();
    EXPECT_EQ(hnsw_params_in.has_value(), hnsw_params_out.has_value());
    if (hnsw_params_in.has_value()) {
        EXPECT_EQ(hnsw_params_in.value(), hnsw_params_out.value());
    }
}

TEST(AttributeHeaderTest, can_be_added_to_and_extracted_from_generic_header)
{
    verify_roundtrip_serialization(HnswIPO({16, 100, DistanceMetric::Euclidean}));
    verify_roundtrip_serialization(HnswIPO({16, 100, DistanceMetric::Angular}));
    verify_roundtrip_serialization(HnswIPO({16, 100, DistanceMetric::GeoDegrees}));
    verify_roundtrip_serialization(HnswIPO({16, 100, DistanceMetric::InnerProduct}));
    verify_roundtrip_serialization(HnswIPO({16, 100, DistanceMetric::PrenormalizedAngular}));
    verify_roundtrip_serialization(HnswIPO({16, 100, DistanceMetric::Hamming}));
    verify_roundtrip_serialization(HnswIPO());
}

GTEST_MAIN_RUN_ALL_TESTS()

