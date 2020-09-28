// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/tensor/mixed/packed_labels.h>
#include <vespa/eval/tensor/mixed/packed_mappings.h>
#include <vespa/eval/tensor/mixed/packed_mappings_builder.h>
#include <vespa/eval/tensor/mixed/packed_mixed_tensor.h>
#include <vespa/eval/tensor/mixed/packed_mixed_tensor_builder.h>
#include <vespa/eval/tensor/mixed/packed_mixed_tensor_builder_factory.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <stdlib.h>
#include <assert.h>
#include <set>

using namespace vespalib::eval;
using namespace vespalib::eval::packed_mixed_tensor;

namespace {

uint32_t random_range(uint32_t from, uint32_t to) {
    assert(from + 1 < to);
    int unif = rand() % (to - from);
    return from + unif;
}

const char *mixed_tensor_types[] = {
    "tensor<float>(x{})",
    "tensor<float>(a{},b{},c{},d{},e{},f{})",
    "tensor<float>(x{},y{})",
    "tensor<float>(x{},z[3])",
    "tensor<float>(w[5],x{},y{},z[3])"
};

const char *float_tensor_types[] = {
    "tensor<float>(x{})",
    "tensor<float>(x{},y{})",
    "tensor<float>(x{},z[3])",
    "tensor<float>(w[5],x{},y{},z[3])",
    "tensor<float>(z[2])",
    "tensor<float>()"
};

    vespalib::string label1(""),
                     label2("foo"),
                     label3("bar");
    vespalib::string label4("foobar"),
                     label5("barfoo"),
                     label6("other");
    vespalib::string label7("long text number one"),
                     label8("long text number two"),
                     label9("long text number three");

std::vector<vespalib::stringref>
generate_random_address(uint32_t dims)
{
    std::vector<vespalib::stringref> foo(dims, label1);
    for (auto & ref : foo) {
        size_t pct = random_range(0, 100);
             if (pct <  5) { ref = label1; }
        else if (pct < 30) { ref = label2; }
        else if (pct < 55) { ref = label3; }
        else if (pct < 65) { ref = label4; }
        else if (pct < 75) { ref = label5; }
        else if (pct < 85) { ref = label6; }
        else if (pct < 90) { ref = label7; }
        else if (pct < 95) { ref = label8; }
        else               { ref = label9; }
    }
    return foo;
}

} // namespace <unnamed>

class MappingsBuilderTest : public ::testing::Test {
public:
    std::unique_ptr<PackedMappingsBuilder> builder;
    std::unique_ptr<PackedMappings> built;

    MappingsBuilderTest() = default;

    virtual ~MappingsBuilderTest() = default;

    void build_and_compare() {
        ASSERT_TRUE(builder);
        built = builder->build_mappings();
        ASSERT_TRUE(built);
        EXPECT_EQ(builder->num_mapped_dims(), built->num_mapped_dims());
        EXPECT_EQ(builder->size(), built->size());
        for (size_t idx = 0; idx < built->size(); ++idx) {
            std::vector<vespalib::stringref> got(builder->num_mapped_dims());
            built->fill_address_by_sortid(idx, got);
            printf("Got address:");
            for (auto ref : got) {
                printf(" '%s'", ref.data());
            }
            uint32_t subspace = built->subspace_of_address(got);
            uint32_t original = builder->add_mapping_for(got);
            printf(" -> %u\n", original);
            EXPECT_EQ(subspace, original);
        }
    }
};

TEST_F(MappingsBuilderTest, empty_mapping)
{
    for (uint32_t dims : { 0, 1, 2, 3 }) {
        builder = std::make_unique<PackedMappingsBuilder>(dims);
        build_and_compare();
    }
}

TEST_F(MappingsBuilderTest, just_one)
{
    vespalib::string label("foobar");
    for (uint32_t dims : { 0, 1, 2, 3, 7 }) {
        builder = std::make_unique<PackedMappingsBuilder>(dims);
        std::vector<vespalib::stringref> foo(dims, label);
        uint32_t idx = builder->add_mapping_for(foo);
        EXPECT_EQ(idx, 0);
        build_and_compare();
    }
}

TEST_F(MappingsBuilderTest, some_random)
{
    for (uint32_t dims : { 1, 2, 5 }) {
        builder = std::make_unique<PackedMappingsBuilder>(dims);
        uint32_t cnt = random_range(dims*5, dims*20);
        printf("Generate %u addresses for %u dims\n", cnt, dims);
        for (uint32_t i = 0; i < cnt; ++i) {
            auto foo = generate_random_address(dims);
            uint32_t idx = builder->add_mapping_for(foo);
            EXPECT_LE(idx, i);
        }
        build_and_compare();
    }
}

class MixedBuilderTest : public ::testing::Test {
public:
    std::unique_ptr<PackedMixedTensorBuilder<float>> builder;
    std::unique_ptr<Value> built;

    MixedBuilderTest() = default;

    virtual ~MixedBuilderTest() = default;

    size_t expected_value = 0;

    void build_and_compare(size_t expect_size) {
        built.reset(nullptr);
        EXPECT_FALSE(built);
        ASSERT_TRUE(builder);
        built = builder->build(std::move(builder));
        EXPECT_FALSE(builder);
        ASSERT_TRUE(built);
        EXPECT_EQ(built->index().size(), expect_size);
        auto cells = built->cells().typify<float>();
        for (float f : cells) {
            float expect = ++expected_value;
            EXPECT_EQ(f, expect);
        }
    }
};

TEST_F(MixedBuilderTest, empty_mapping)
{
    for (auto type_spec : mixed_tensor_types) {
        ValueType type = ValueType::from_spec(type_spec);
        size_t dims = type.count_mapped_dimensions();
        size_t dsss = type.dense_subspace_size();
        EXPECT_GT(dims, 0);
        EXPECT_GT(dsss, 0);
        builder = std::make_unique<PackedMixedTensorBuilder<float>>(type, dims, dsss, 3);
        build_and_compare(0);
    }
}

TEST_F(MixedBuilderTest, just_one)
{
    size_t counter = 0;
    for (auto type_spec : float_tensor_types) {
        ValueType type = ValueType::from_spec(type_spec);
        size_t dims = type.count_mapped_dimensions();
        size_t dsss = type.dense_subspace_size();
        EXPECT_GT(dsss, 0);
        builder = std::make_unique<PackedMixedTensorBuilder<float>>(type, dims, dsss, 3);
        auto address = generate_random_address(dims);
        auto ref = builder->add_subspace(address);
        EXPECT_EQ(ref.size(), dsss);
        for (size_t i = 0; i < ref.size(); ++i) {
            ref[i] = ++counter;
        }
        build_and_compare(1);
    }
}

TEST_F(MixedBuilderTest, some_random)
{
    size_t counter = 0;
    for (auto type_spec : mixed_tensor_types) {
        ValueType type = ValueType::from_spec(type_spec);
        uint32_t dims = type.count_mapped_dimensions();
        uint32_t dsss = type.dense_subspace_size();
        EXPECT_GT(dims, 0);
        EXPECT_GT(dsss, 0);
        builder = std::make_unique<PackedMixedTensorBuilder<float>>(type, dims, dsss, 3);

        uint32_t cnt = random_range(dims*5, dims*20);
        printf("MixBuild: generate %u addresses for %u dims\n", cnt, dims);
        std::set<std::vector<vespalib::stringref>> seen;
        for (uint32_t i = 0; i < cnt; ++i) {
            auto address = generate_random_address(dims);
            if (seen.insert(address).second) {
                auto ref = builder->add_subspace(address);
                EXPECT_EQ(ref.size(), dsss);
                for (size_t j = 0; j < ref.size(); ++j) {
                    ref[j] = ++counter;
                }
            }
        }
        printf("MixBuild: generated %zu unique addresses\n", seen.size());
        build_and_compare(seen.size());
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
